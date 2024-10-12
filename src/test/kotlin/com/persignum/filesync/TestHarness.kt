package com.persignum.filesync

import com.google.common.jimfs.Jimfs
import com.persignum.filesync.domain.*
import com.persignum.filesync.gateways.*
import com.persignum.filesync.gateways.OutputFactory
import com.uchuhimo.konf.source.yaml
import io.kotest.matchers.nulls.shouldNotBeNull
import java.nio.file.FileSystem
import org.intellij.lang.annotations.Language

class TestHarness {
    private var yaml: String = ""
    private val sources = mutableMapOf<String, Source>()
    private var assertBlock: (List<OutputItem>) -> Unit = { }

    val libreTimeConnector = LibreTimeStub()
    var fs: FileSystem = Jimfs.newFileSystem()
    var useEmptyOutput = false

    private val sourceFactory = SourceFactory { program, spec, _ ->
        sources[spec.name] ?: sources[program] ?: EmptySource(spec.name)
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

    fun addSource(name: String, vararg items: MemoryItem) {
        sources[name] = SourceStub(name, items.toList())
    }

    fun execute() {
        var outputItems: List<OutputItem>? = null

        val outputFactory = OutputFactory { spec, limits ->
            val gateway = if(useEmptyOutput) EmptyOutputGateway else {
                DefaultOutputFactory(fs, libreTimeConnector)
                    .build(spec, limits)
            }

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