package com.persignum.filesync.domain

import java.io.OutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

interface Item {
    val name: String

    val createdAt: Instant

    suspend fun data(stream: OutputStream)

    fun computeFormatFromName(): String = if ('.' in name) {
        name.substringAfterLast('.')
    } else "mp3"

    fun nameAndExtension(): Pair<String, String> {
        val name = name.substringBeforeLast('.')
        val extension = computeFormatFromName()
        return name to extension
    }
}

class ParsedItem(
    val item: Item,
    captureGroups: Map<String, String> = emptyMap(),
    parsedDates: Map<String, LocalDateTime> = emptyMap(),
) : Item by item {
    private val nameAndExtension = nameAndExtension()

    private val createdAtDate = LocalDateTime.ofInstant(item.createdAt, ZoneId.systemDefault())
    private val dates = mapOf("created_at" to createdAtDate) + parsedDates
    private val replacements = mapOf(
        "old_filename" to nameAndExtension.first,
        "old_extension" to nameAndExtension.second,
        "raw_filename" to item.name,
    ) + captureGroups + dates.mapValues { it.value.toString() }

    private val dateFormatSignatures = dates.keys.map { "{$it" }.sortedByDescending { it.length }

    private fun String.replaceDate(found: Pair<Int, String>): String {
        // Interested in 2 things
        // math after date name (if any)
        // format
        val (index, foundStr) = found
        val dateName = foundStr.drop(1)

        val full = substring(index, indexOf('}', index) + 1)
        val innerFull = full.drop(1).dropLast(1)

        // Drop the curly braces
        val name = innerFull.substringBefore(":")
        val format = innerFull.substringAfter(":")

        val duration = name.substringAfter(dateName)

        val date = manipulateDate(dates[dateName]!!, duration)
        val pattern = DateTimeFormatter.ofPattern(format)

        return replace(full, date.format(pattern))
    }

    private fun manipulateDate(date: LocalDateTime, durationStr: String): LocalDateTime {
        if (durationStr.isBlank()) return date

        val operator = durationStr.first()
        val durationInput = durationStr.drop(1)
        val inputToIso8601 = "P" + durationInput
            .uppercase()
            .replace(" ", "")
            .replace("D", "DT")
            .removeSuffix("T")

        val duration = try {
            Duration.parse(inputToIso8601)
        } catch (e: DateTimeParseException) {
            error("Duration format '$durationInput' should be in a format like '1y 2m 3d 4h 5m 6s'. Can include/exclude any unit.")
        }

        return when (operator) {
            '+' -> date.plus(duration)
            '-' -> date.minus(duration)
            else -> error("Operator '$operator' must be '+' or '-'.")
        }
    }

    fun interpolate(str: String): String {
        var dated = replacements.entries.fold(str) { acc, (key, value) ->
            acc.replace("{$key}", value)
        }

        var found: Pair<Int, String>?
        while (true) {
            found = dated.findAnyOf(dateFormatSignatures)
            if (found == null) break
            dated = dated.replaceDate(found)
        }

        return dated
    }
}

data class OutputItem(
    val item: ParsedItem,
    val program: String,
    val source: String,
    val fileName: String,
    val format: String = "mp3",
    val tags: Map<String, String> = emptyMap(),
) : Item by item {
    val file = "$fileName.$format"

    override fun toString(): String = "$program/$fileName"
}
