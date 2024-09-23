package org.klrf.filesync.domain

interface History {
    fun add(items: Iterable<Item>)
    fun exists(item: Item): Boolean
}

interface InputGateway {
    fun programs(): List<Program>
    fun history(): History
    fun output(): OutputGateway
}

data class SaveStatus(
    val success: List<OutputItem>,
    val failed: List<Pair<OutputItem, Throwable>>,
)

fun interface OutputGateway {
    fun save(items: List<OutputItem>): SaveStatus
}

fun interface Source {
    fun listItems(): Sequence<Item>
}

