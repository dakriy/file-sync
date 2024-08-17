package org.klrf.filesync.domain

interface History {
    fun add(item: Item)
    fun exists(item: Item): Boolean
}

interface InputGateway {
    fun programs(): List<Program>
    fun history(): History
}

fun interface OutputGateway {
    fun save(items: List<OutputItem>)
}

fun interface Source {
    fun listItems(): Sequence<Item>
}

