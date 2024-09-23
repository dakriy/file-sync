package org.klrf.filesync

import java.time.Instant
import org.klrf.filesync.domain.Item
import org.klrf.filesync.gateways.FTPConnection
import org.klrf.filesync.gateways.FTPConnector

class FTPClientStub(
    override val connection: FTPConnection,
    private val items: List<Item>,
) : FTPConnector {
    constructor(connection: FTPConnection, vararg items: Item) : this(connection, items.toList())

    override fun listFiles(): Sequence<Pair<String, Instant>> {
        return items.map { it.name to it.createdAt }.asSequence()
    }

    override fun downloadFile(file: String) = items.first { it.name == file }.data()
}
