package com.persignum.filesync.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

class FileSync(
    private val input: InputGateway,
) {
    private val logger = KotlinLogging.logger { }

    fun sync() {
        val output = input.output()

        var error: Throwable? = null

        val items = input.programs().flatMap { program ->
            try {
                val extensionWhitelist = program.extensions
                val items = program.source.listItems()
                    .filter {
                        if (extensionWhitelist != null) {
                            it.computeFormatFromName() in extensionWhitelist
                        } else true
                    }
                    .mapNotNull { item ->
                        if (program.parse == null) ParsedItem(item)
                        else program.parse.parse(program.name, item)
                    }
                    .take(program.output?.limit ?: Int.MAX_VALUE)
                    .map { item ->
                        val (name, extension) = item.nameAndExtension()
                        val format = program.output?.format ?: extension
                        val filename = program.output?.filename ?: name

                        val tags = program.output?.tags ?: emptyMap()

                        val replacedTags = tags.mapValues { (_, value) -> item.interpolate(value) }
                        val replacedFileName = item.interpolate(filename)

                        OutputItem(
                            item,
                            program.name,
                            program.source.name,
                            replacedFileName,
                            format,
                            replacedTags,
                        )
                    }
                    .toList()

                if (items.isEmpty()) {
                    logger.warn { "No items to download for $program." }
                }

                items
            } catch (e: Throwable) {
                error = e
                if (input.stopOnFailure) throw e

                logger.error(e) { "Failed sourcing from $program." }
                emptyList()
            }
        }

        runBlocking {
            output.save(items)
        }

        if (error != null) {
            throw error!!
        }
    }
}
