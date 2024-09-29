package org.klrf.filesync.gateways

import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

data class EmptySource(
    override val name: String = "",
) : Source {
    override fun listItems(): Sequence<Item> = emptySequence()
}
