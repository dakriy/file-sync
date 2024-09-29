package org.klrf.filesync

import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source
import org.klrf.filesync.gateways.SourceImplSpec
import org.klrf.filesync.gateways.SourceSpec

data class CustomSource(
    private val programName: String,
    private val spec: SourceSpec,
    private val implSpec: SourceImplSpec,
) : Source {
    override fun listItems(): Sequence<Item> {
        return emptySequence()
    }
}
