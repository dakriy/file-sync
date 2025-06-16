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

    val forceSortMode: SortMode?
        get() = null

    fun listItems(): Sequence<Item>
}
