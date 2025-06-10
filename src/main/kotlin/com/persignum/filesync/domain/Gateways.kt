package com.persignum.filesync.domain

interface InputGateway {
    fun programs(): List<Program>
    fun output(): OutputGateway
    val stopOnFailure: Boolean
}

fun interface OutputGateway {
    suspend fun save(items: List<OutputItem>)
}

interface Source {
    val name: String

    fun listItems(): Sequence<Item>
}
