package com.persignum.filesync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class ParseMatchMode {
    Strict,
    Warn,
    Lax,
}

data class Parse(
    val regex: Regex,
    val dateWithParseFormat: Map<String, DateTimeFormatter> = emptyMap(),
    val matchMode: ParseMatchMode,
    val entireMatch: Boolean,
) {
    private val logger = KotlinLogging.logger { }

    private val captureGroups by lazy {
        val captureGroupRegex = """\(\?<(\w+)>""".toRegex()
        captureGroupRegex.findAll(regex.toString()).map { it.groupValues[1] }.toList()
    }

    fun parse(program: String, item: Item): ParsedItem? {
        val result = if (entireMatch) regex.matchEntire(item.name) else regex.find(item.name)

        if (result == null) {
            val message = "Item in $program did not match '${item.name}'"

            when (matchMode) {
                ParseMatchMode.Strict -> error(message)
                ParseMatchMode.Warn -> logger.warn { message }
                ParseMatchMode.Lax -> {}
            }

            return null
        }

        val captureGroups = captureGroups.associateWith { captureGroupName ->
            result.groups[captureGroupName]?.value
                ?: error("Capture group '$captureGroupName' did not exist. Regex: '$regex' INPUT: ${item.name}.")
        }

        val dateGroups = dateWithParseFormat.mapValues { (name, format) ->
            val dateString = captureGroups[name]
                ?: error("Capture group '$name' does not exist in '$regex' for program '$program'")

            try {
                val date = LocalDateTime.parse(dateString, format)
                return@mapValues date
            } catch (_: DateTimeParseException) {
            }

            try {
                val time = LocalTime.parse(dateString, format)
                return@mapValues LocalDate.now().atTime(time)
            } catch (_: DateTimeParseException) {
            }

            try {
                val date = LocalDate.parse(dateString, format)
                return@mapValues date.atStartOfDay()
            } catch (_: DateTimeParseException) {
            }

            throw IllegalArgumentException("Unable to parse date '$name' with value '$dateString' for $program/${item.name}.")
        }

        return ParsedItem(item, captureGroups, dateGroups)
    }
}
