package org.klrf.filesync.domain

class FileSync(
    private val input: InputGateway,
    private val output: OutputGateway,
) {
    fun sync() {
        val history = input.history()

        val items = input.programs().flatMap { program ->
            program.source.listItems()
                .sortedByDescending { it.createdAt }
                .mapNotNull { item ->
                    if (program.parse == null) ParsedItem(item)
                    else program.parse.parse(item)
                }
                .take(program.output?.limit ?: Int.MAX_VALUE)
                .filterNot(history::exists)
                .map { item ->
                    val (name, extension) = item.nameAndExtension()
                    val format = program.output?.format ?: extension
                    val filename = program.output?.filename ?: name

                    val tags = program.output?.tags ?: emptyMap()

                    val replacedTags = tags.mapValues { (_, value) -> item.interpolate(value) }
                    val replacedFileName = item.interpolate(filename)

                    OutputItem(item, replacedFileName, format, replacedTags)
                }
                .toList()
        }

        output.save(items)
    }
}
