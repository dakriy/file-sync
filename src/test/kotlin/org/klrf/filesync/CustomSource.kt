package org.klrf.filesync

import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source
import org.klrf.filesync.gateways.SourceSpec

data class CustomSource(private val spec: SourceSpec) : Source {
    override fun listItems(): Sequence<Item> {
        return emptySequence()
    }
}
