package org.klrf.filesync

import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

class NextCloudStub(
    private val items: List<Item>,
) : Source {
    override fun listItems() = items.asSequence()
}
