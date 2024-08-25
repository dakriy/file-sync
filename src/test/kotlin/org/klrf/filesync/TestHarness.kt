package org.klrf.filesync

import com.google.common.jimfs.Jimfs
import com.uchuhimo.konf.source.yaml
import io.kotest.matchers.nulls.shouldNotBeNull
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.klrf.filesync.domain.FileSync
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.OutputGateway
import org.klrf.filesync.domain.OutputItem
import org.klrf.filesync.gateways.ConfigInput
import org.klrf.filesync.gateways.DefaultOutputGatewayFactory
import org.klrf.filesync.gateways.FTPConnection
import org.klrf.filesync.gateways.FTPConnector
import org.klrf.filesync.gateways.FileSyncTable
import org.klrf.filesync.gateways.OutputGatewayFactory

class TestHarness {
    private var yaml: String = ""
    private val ftpConnectors = mutableListOf<FTPClientStub>()
    private var assertBlock: (List<OutputItem>) -> Unit = { }
    private var history = mutableListOf<Item>()
    val fs = Jimfs.newFileSystem()

    fun config(@Language("YAML") yaml: String) {
        this.yaml = yaml
    }

    fun history(vararg items: Item) {
        history.addAll(items)
    }

    fun ftpConnector(url: String, vararg items: Item) {
        val ftpClient = FTPClientStub(FTPConnection(url), *items)

        ftpConnectors.add(ftpClient)
    }

    fun ftpConnector(vararg connectors: FTPClientStub) {
        ftpConnectors.addAll(connectors)
    }

    fun assert(block: (List<OutputItem>) -> Unit) {
        assertBlock = block
    }

    private fun findConnector(connection: FTPConnection): FTPConnector =
        ftpConnectors.find { stub -> stub.connection == connection }
            ?: error("An FTP connector was requested that was not defined in the test.")

    fun execute() {
        var outputItems: List<OutputItem>? = null

        val outputFactory = OutputGatewayFactory { spec ->
            val gateway = DefaultOutputGatewayFactory(
                fs,
            ).build(spec)

            val outputGateway = OutputGateway {
                outputItems = it
                gateway.save(it)
            }

            outputGateway
        }

        val input = ConfigInput(::findConnector, outputFactory) {
            from.yaml.string(yaml)
        }

        if (input.db != null && history.isNotEmpty()) {
            transaction(input.db) {
                FileSyncTable.batchInsert(history) { item ->
                    this[FileSyncTable.program] = item.program
                    this[FileSyncTable.hash] = item.hash()
                    this[FileSyncTable.name] = item.name
                }
            }
        }

        FileSync(input).sync()

        if (input.db != null && history.isNotEmpty()) {
            transaction(input.db) {
                FileSyncTable.deleteAll()
            }
        }

        val result = outputItems
        result.shouldNotBeNull()
        assertBlock(result)
        fs.close()
    }
}