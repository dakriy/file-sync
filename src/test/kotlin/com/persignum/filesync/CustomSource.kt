package com.persignum.filesync

import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.Source
import com.persignum.filesync.gateways.SourceImplSpec
import com.persignum.filesync.gateways.SourceSpec

data class CustomSource(
    override val name: String,
    private val spec: SourceSpec,
    private val implSpec: SourceImplSpec,
) : Source {
    override fun listItems(): Sequence<Item> {
        return emptySequence()
    }
}
