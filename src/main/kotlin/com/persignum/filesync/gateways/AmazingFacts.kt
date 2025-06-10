package com.persignum.filesync.gateways

import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.Source
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.OutputStream
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

    inner class AmazingFactsItem(
        override val name: String,
        override val createdAt: Instant,
        private val downloadUrl: String,
    ) : Item {
        override suspend fun data(stream: OutputStream) {
            withContext(Dispatchers.IO) {
                URL(downloadUrl).openStream().copyTo(stream)
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
            .mapNotNull {
                val relativeUrl = it.attr("href") ?: return@mapNotNull null

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
                    .lastOrNull { it.tagName() == "a" } ?: error("No download url for $title at $detailsUrl")

                val downloadUrl = downloadTag.attr("href") ?: error("Missing href for download url for $title at $detailsUrl")

                val fileName = downloadUrl.substringAfterLast("/")

                AmazingFactsItem(
                    "$title --- $fileName",
                    createdAt,
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