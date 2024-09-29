package org.klrf.filesync

import org.klrf.filesync.domain.Source

class SourceStub(
    override val name: String,
    private val items: List<MemoryItem>,
) : Source {
    override fun listItems() = items
        .sortedByDescending { it.createdAt }
        .asSequence()
}
