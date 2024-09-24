package org.klrf.filesync

import com.google.common.jimfs.Jimfs
import com.uchuhimo.konf.source.yaml
import io.kotest.matchers.nulls.shouldNotBeNull
import java.nio.file.FileSystem
import org.intellij.lang.annotations.Language
import org.klrf.filesync.domain.*
import org.klrf.filesync.gateways.*

class TestHarness {
    private var yaml: String = ""
    private val sources = mutableMapOf<String, Source>()
    private var assertBlock: (List<OutputItem>) -> Unit = { }

    val libreTimeConnector = LibreTimeStub()
    var fs: FileSystem = Jimfs.newFileSystem()

    private val sourceFactory = SourceFactory { program, _ ->
        sources[program] ?: EmptySource
    }

    fun config(@Language("YAML") yaml: String) {
        this.yaml = yaml
    }

    fun addLibreTimeHistory(vararg filename: String) {
        libreTimeConnector.existingFiles.addAll(filename)
    }

    fun assert(block: (List<OutputItem>) -> Unit) {
        assertBlock = block
    }

    fun addSource(program: String, vararg items: MemoryItem) {
        sources[program] = SourceStub(items.toList())
    }

    fun execute() {
        var outputItems: List<OutputItem>? = null

        val outputFactory = OutputFactory { spec ->
            val gateway = DefaultOutputFactory(fs, libreTimeConnector).build(spec)

            val outputGateway = OutputGateway {
                outputItems = it
                gateway.save(it)
            }

            outputGateway
        }

        val input = ConfigInput(sourceFactory, outputFactory) {
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