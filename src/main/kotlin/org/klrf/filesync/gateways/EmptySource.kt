package org.klrf.filesync.gateways

import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

object EmptySource : Source {
    override fun listItems(): Sequence<Item> = emptySequence()
}
