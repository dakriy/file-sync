package org.klrf.filesync.domain

interface InputGateway {
    fun programs(): List<Program>
    fun output(): OutputGateway
    val stopOnFailure: Boolean
}

fun interface OutputGateway {
    suspend fun save(items: List<OutputItem>)
}

fun interface Source {
    fun listItems(): Sequence<Item>
}

