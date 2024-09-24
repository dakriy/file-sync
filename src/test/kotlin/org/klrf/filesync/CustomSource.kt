package org.klrf.filesync

import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

class CustomSource : Source {
    override fun listItems(): Sequence<Item> {
        return emptySequence()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
