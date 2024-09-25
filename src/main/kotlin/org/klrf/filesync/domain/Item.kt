package org.klrf.filesync.domain

import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface Item {
    val program: String

    val name: String

    val createdAt: Instant

    suspend fun data(): InputStream

    fun computeFormatFromName(): String = if ('.' in name) {
        name.substringAfterLast('.')
    } else "mp3"

    fun nameAndExtension(): Pair<String, String> {
        val name = name.substringBeforeLast('.')
        val extension = computeFormatFromName()
        return name to extension
    }
}

data class ParsedItem(
    val item: Item,
    val captureGroups: Map<String, String> = emptyMap(),
    val dates: Map<String, LocalDate> = emptyMap(),
) : Item by item {
    private val nameAndExtension = nameAndExtension()

    private val replacements = mapOf(
        "old_filename" to nameAndExtension.first,
        "old_extension" to nameAndExtension.second,
        "raw_filename" to item.name,
        "created_at" to LocalDate.ofInstant(item.createdAt, ZoneId.systemDefault()).toString(),
    ) + captureGroups

    private val dateFormatSignatures = dates.keys.map { "{$it:" }

    private fun String.replaceDate(found: Pair<Int, String>): String {
        val (index, foundStr) = found
        val foundName = foundStr.drop(1).dropLast(1)
        val full = substring(index).substringBefore('}')
        val format = full.substringAfter(':')
        val date = dates[foundName]!!
        val pattern = DateTimeFormatter.ofPattern(format)

        return replace("{$foundName:$format}", date.format(pattern))
    }

    fun interpolate(str: String): String {
        var dated = str
        var found: Pair<Int, String>?
        while (true) {
            found = dated.findAnyOf(dateFormatSignatures)
            if (found == null) break
            dated = dated.replaceDate(found)
        }

        return replacements.entries.fold(dated) { acc, (key, value) ->
            acc.replace("{$key}", value)
        }
    }
}

data class OutputItem(
    val item: ParsedItem,
    val fileName: String,
    val format: String = "mp3",
    val tags: Map<String, String> = emptyMap(),
) : Item by item {
    val file = "$fileName.$format"

    override fun toString(): String = "$program/$fileName"
}
