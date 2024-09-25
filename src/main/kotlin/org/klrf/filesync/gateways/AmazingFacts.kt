package org.klrf.filesync.gateways

import io.ktor.http.*
import java.io.InputStream
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

@Suppress("unused")
class AmazingFactsSource(
    val programName: String,
    val spec: SourceSpec,
) : Source {
    init {
        requireNotNull(spec.url) { "The 'url' field is required for an AmazingFacts source." }
    }

    inner class AmazingFactsItem(
        override val program: String,
        override val name: String,
        override val createdAt: Instant,
        private val downloadUrl: String,
    ) : Item {
        override suspend fun data(): InputStream {
            return withContext(Dispatchers.IO) {
                URL(downloadUrl).openStream()
            }
        }
    }

    private val datePattern = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    override fun listItems(): Sequence<Item> {
        val initialUrl = spec.url!!
        val doc = Jsoup.connect(initialUrl).get()
        val elements = doc.select("table.table tr td a[href]")

        // order is automatically in date order descending
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
                    programName,
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