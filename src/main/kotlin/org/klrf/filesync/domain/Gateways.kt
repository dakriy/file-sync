package org.klrf.filesync.domain

interface InputGateway {
    fun programs(): List<Program>
    fun output(): OutputGateway
    val stopOnFailure: Boolean
}

fun interface OutputGateway {
    suspend fun save(items: List<OutputItem>)
}

interface Source {
    /**
     * It is up to the implementation to order tis by date DESCENDING
     */
    fun listItems(): Sequence<Item>
}
