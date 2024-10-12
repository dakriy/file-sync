package com.persignum.filesync.gateways

import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.Source

data class EmptySource(
    override val name: String = "",
) : Source {
    override fun listItems(): Sequence<Item> = emptySequence()
}
