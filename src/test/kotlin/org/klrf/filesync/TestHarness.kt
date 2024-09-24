package org.klrf.filesync

import com.google.common.jimfs.Jimfs
import com.uchuhimo.konf.source.yaml
import io.kotest.matchers.nulls.shouldNotBeNull
import org.intellij.lang.annotations.Language
import org.klrf.filesync.domain.*
import org.klrf.filesync.gateways.*

class TestHarness {
    private var yaml: String = ""
    private val ftpConnectors = mutableListOf<FTPClientStub>()
    private var assertBlock: (List<OutputItem>) -> Unit = { }

    //    private var libreTimeConnector: LibreTimeConnector =
    var fs = Jimfs.newFileSystem()

    fun config(@Language("YAML") yaml: String) {
        this.yaml = yaml
    }

    fun ftpConnector(url: String, vararg items: MemoryItem) {
        val ftpClient = FTPClientStub(FTPConnection(url), *items)

        ftpConnectors.add(ftpClient)
    }

    fun ftpConnector(vararg connectors: FTPClientStub) {
        ftpConnectors.addAll(connectors)
    }
//
//    fun libretimeConnector(connector: LibreTimeConnector) {
//
//    }

    fun assert(block: (List<OutputItem>) -> Unit) {
        assertBlock = block
    }

    private fun findConnector(connection: FTPConnection): FTPConnector =
        ftpConnectors.find { stub -> stub.connection == connection }
            ?: error("An FTP connector was requested that was not defined in the test.")

    fun execute() {
        var outputItems: List<OutputItem>? = null

        val outputFactory = OutputGatewayFactory { spec ->
            val gateway = DefaultOutputGatewayFactory(fs, LibreTimeStub()).build(spec)

            val outputGateway = OutputGateway {
                outputItems = it
                gateway.save(it)
            }

            outputGateway
        }

        val input = ConfigInput(::findConnector, outputFactory) {
            from.yaml.string(yaml)
        }

        FileSync(input).sync()

        try {
            val result = outputItems
            result.shouldNotBeNull()
            assertBlock(result)
        } finally {
            try {
                fs.close()
            } catch (_: UnsupportedOperationException) {
            }
        }
    }
}