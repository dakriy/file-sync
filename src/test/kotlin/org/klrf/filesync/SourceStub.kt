package org.klrf.filesync

import org.klrf.filesync.domain.Source

class SourceStub(
    private val items: List<MemoryItem>,
) : Source {
    override fun listItems() = items.asSequence()
}
