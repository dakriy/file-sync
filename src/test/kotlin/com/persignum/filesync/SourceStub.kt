package com.persignum.filesync

import com.persignum.filesync.domain.Source

class SourceStub(
    override val name: String,
    private val items: List<MemoryItem>,
) : Source {
    override fun listItems() = items
        .asSequence()
}
