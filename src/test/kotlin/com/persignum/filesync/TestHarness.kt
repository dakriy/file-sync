package com.persignum.filesync

import com.google.common.jimfs.Jimfs
import com.persignum.filesync.domain.FileSync
import com.persignum.filesync.domain.OutputGateway
import com.persignum.filesync.domain.OutputItem
import com.persignum.filesync.domain.Source
import com.persignum.filesync.gateways.*
import com.uchuhimo.konf.source.yaml
import io.kotest.matchers.nulls.shouldNotBeNull
import org.intellij.lang.annotations.Language
import java.nio.file.FileSystem

class TestHarness {
    private var yaml: String = ""
    private val sources = mutableMapOf<String, Source>()
    private var assertBlock: (List<OutputItem>) -> Unit = { }

    val libreTimeConnector = OutputStub()
    var fs: FileSystem = Jimfs.newFileSystem()
    var useEmptyOutput = false
    var programs: MutableList<String> = mutableListOf()
    var sourcesFilter: MutableList<String> = mutableListOf()

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

    fun programsFilter(vararg program: String) {
        programs.addAll(program)
    }
    fun sourcesFilter(vararg source: String) {
        sourcesFilter.addAll(source)
    }

    fun addSource(name: String, vararg items: MemoryItem) {
        sources[name] = SourceStub(name, items.toList())
    }

    fun execute() {
        var outputItems: List<OutputItem>? = null

        val outputFactory = OutputFactory { spec, limits, globalLimit ->
            val gateway = if(useEmptyOutput) EmptyOutputGateway else {
                DefaultOutputFactory(fs, libreTimeConnector)
                    .build(spec, limits, globalLimit)
            }

            val outputGateway = OutputGateway {
                outputItems = it
                gateway.save(it)
            }

            outputGateway
        }

        val input = ConfigInput(sourceFactory, outputFactory, programs, sourcesFilter) {
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