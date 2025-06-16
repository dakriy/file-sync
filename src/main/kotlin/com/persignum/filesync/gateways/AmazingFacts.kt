package com.persignum.filesync.gateways

import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.SortMode
import com.persignum.filesync.domain.Source
import io.ktor.http.*
import org.jsoup.Jsoup
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("unused")
class AmazingFactsSource(
    override val name: String,
    val spec: SourceSpec,
    val implSpec: SourceImplSpec,
) : Source {
    init {
        requireNotNull(spec.url) { "The 'url' field is required for an AmazingFacts source." }
    }

    override val forceSortMode: SortMode = SortMode.DateDesc

    inner class AmazingFactsItem(
        override val name: String,
        override val createdAt: Instant,
        private val detailsUrl: String,
        private val downloadUrl: String?,
    ) : Item {
        override suspend fun data(stream: OutputStream) {
            val downloadUrl = downloadUrl ?: error("No download url for $name at $detailsUrl")
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        stream.use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    error("Failed to download $name at $downloadUrl. Details $detailsUrl HTTP error code: $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private val datePattern = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    override fun listItems(): Sequence<Item> {
        val initialUrl = spec.url!!
        val doc = Jsoup.connect(initialUrl).get()
        val elements = doc.select("table.table tr td a[href]")

        return elements
            .asSequence()
            .mapNotNull { element ->
                val relativeUrl = element.attr("href") ?: return@mapNotNull null

                val detailsUrl = constructAbsoluteUrl(initialUrl, relativeUrl)

                val detailsDoc = Jsoup.connect(detailsUrl).get()
                val title =
                    detailsDoc.select("#ProgramTitle").text() ?: error("Program title not found on $detailsUrl")
                val dateStr = detailsDoc
                    .getElementsContainingText("Date: ")
                    .lastOrNull()
                    ?.text()
                    ?.substringAfter("Date: ") ?: error("Date not found on $detailsUrl")

                val createdAt = LocalDate.parse(dateStr, datePattern)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()

                val downloadTag = detailsDoc.getElementsContainingText("Audio Download")
                    .lastOrNull { it.tagName() == "a" }

                val downloadUrl = downloadTag?.attr("href")

                val fileName = downloadUrl?.substringAfterLast("/")

                AmazingFactsItem(
                    if (fileName != null) "$title --- $fileName" else title,
                    createdAt,
                    detailsUrl,
                    downloadUrl,
                )
            }
    }

    fun constructAbsoluteUrl(base: String, relative: String): String {
        val baseUrl = Url(base)
        return URLBuilder().takeFrom(relative).apply {
            protocol = baseUrl.protocol
            host = baseUrl.host
            port = baseUrl.port
        }.toString()
    }
}