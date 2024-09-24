package org.klrf.filesync.gateways

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.Feature
import java.nio.file.FileSystem
import java.time.format.DateTimeFormatter
import org.klrf.filesync.domain.*

enum class SourceType {
    Empty,
    FTP,
//    NextCloud,
//    Custom,
}

data class SourceSpec(
    val type: SourceType,

    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null,
//    val customSource: String? = null,
) {
    fun toFTPConnection() = FTPConnection(
        url ?: error("url is required for ftp source"),
        username,
        password,
        path,
    )
}

data class ParseSpec(
    val regex: String,
    val dates: Map<String, String> = emptyMap(),
    val strict: Boolean = false,
    val entireMatch: Boolean = false,
) {
    fun toParse() = Parse(
        regex.toRegex(),
        dates.mapValues { (_, v) -> DateTimeFormatter.ofPattern(v) },
        strict,
        entireMatch,
    )
}

data class ProgramSpec(
    val source: SourceSpec,
    val parse: ParseSpec? = null,
    val output: Output? = null,
)

object FileSyncSpec : ConfigSpec() {
    val programs by optional<Map<String, ProgramSpec>>(emptyMap())

    val output by optional<OutputSpec>(OutputSpec())

    val stopOnFailure by optional<Boolean?>(null)
}

data class OutputSpec(
    val dir: String = "output",
    val ffmpegOptions: String? = null,
    val enabled: Boolean = true,
    val id3Version: String? = null,
)

fun interface OutputGatewayFactory {
    fun build(config: OutputSpec): OutputGateway
}

fun interface FTPConnectorFactory {
    fun build(connection: FTPConnection): FTPConnector
}

class DefaultOutputGatewayFactory(
    private val fileSystem: FileSystem,
    private val libreTimeConnector: LibreTimeConnector,
) : OutputGatewayFactory {
    override fun build(config: OutputSpec): OutputGateway {
        return if (config.enabled) {
            FileOutput(
                fileSystem.getPath(config.dir),
                libreTimeConnector,
                config.ffmpegOptions,
                config.id3Version,
            )
        } else OutputGateway { }
    }
}

class ConfigInput(
    private val ftpConnectorFactory: FTPConnectorFactory,
    private val outputGatewayFactory: OutputGatewayFactory,
    sourceConfig: Config.() -> Config,
) : InputGateway {
    private val config = Config {
        addSpec(FileSyncSpec)
        enable(Feature.OPTIONAL_SOURCE_BY_DEFAULT)
    }
        .run(sourceConfig)
        .from.env()
        .from.systemProperties()

    override val stopOnFailure: Boolean = config[FileSyncSpec.stopOnFailure] ?: false

    private fun buildSource(name: String, sourceConfig: SourceSpec): Source {
        val type = sourceConfig.type

        return when (type) {
            SourceType.Empty -> EmptySource
            SourceType.FTP -> {
                val ftpConnection = sourceConfig.toFTPConnection()
                FTPSource(name, ftpConnectorFactory.build(ftpConnection))
            }
        }
    }

    override fun programs(): List<Program> {
        return config[FileSyncSpec.programs].map { (name, programSpec) ->
            val source = buildSource(name, programSpec.source)

            val parse = programSpec.parse?.toParse()

            Program(name, source, parse, programSpec.output)
        }
    }

    override fun output(): OutputGateway {
        return outputGatewayFactory.build(config[FileSyncSpec.output])
    }
}
