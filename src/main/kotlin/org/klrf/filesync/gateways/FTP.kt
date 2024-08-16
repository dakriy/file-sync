package org.klrf.filesync.gateways

import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

interface FTPConnector {
    val connection: FTPConnection

    fun listFiles(): Sequence<String>
//    fun downloadFile(file: String): ByteArray
}

data class FTPConnection(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null
)

class FTPSource(
    private val program: String,
    private val connector: FTPConnector,
) : Source {
    inner class FTPItem(
        override val name: String,
    ) : Item {
        override val program: String = this@FTPSource.program
        override fun data(): ByteArray = ByteArray(0)
    }

    override fun listItems(): Sequence<Item> {
        return connector.listFiles().map(::FTPItem)
    }
}
