package org.klrf.filesync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Parse(
    val regex: Regex,
    val dateWithParseFormat: Map<String, DateTimeFormatter> = emptyMap(),
    val strict: Boolean,
) {
    private val logger = KotlinLogging.logger { }

    private val captureGroups by lazy {
        val captureGroupRegex = """\(\?<(\w+)>""".toRegex()
        captureGroupRegex.findAll(regex.toString()).map { it.groupValues[1] }.toList()
    }

    fun parse(item: Item): ParsedItem? {
        val result = regex.find(item.name)

        if (result == null) {
            val message = "Item in ${item.program} did not match '${item.name}'"
            if (strict) error(message)
            else logger.warn { message }

            return null
        }

        val captureGroups = captureGroups.associateWith { captureGroupName ->
            result.groups[captureGroupName]?.value
                ?: error("Capture group '$captureGroupName' did not exist. Regex: '$regex' INPUT: ${item.name}.")
        }

        val dateGroups = dateWithParseFormat.mapValues { (name, format) ->
            val dateString = captureGroups[name]
                ?: error("Capture group '$name' does not exist in '$regex' for program '${item.program}'")

            LocalDate.parse(dateString, format)
        }

        return ParsedItem(item, captureGroups, dateGroups)
    }
}
