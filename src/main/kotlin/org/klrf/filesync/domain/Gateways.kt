package org.klrf.filesync.domain

fun interface InputGateway {
    fun programs(): List<Program>
}

fun interface OutputGateway {
    fun save(items: List<OutputItem>)
}

fun interface Source {
    fun listItems(): Sequence<Item>
}

