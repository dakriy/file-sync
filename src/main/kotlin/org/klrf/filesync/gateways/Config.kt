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
    NextCloud,
    Custom,
}

data class SourceSpec(
    val type: SourceType,

    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null,
    val port: Int? = null,
    val depth: Int = 1,
    val `class`: String? = null,
)

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
    val name: String,
    val source: SourceSpec = SourceSpec(SourceType.Empty),
    val parse: ParseSpec? = null,
    val output: Output? = null,
)

object FileSyncSpec : ConfigSpec() {
    val programs by optional<List<ProgramSpec>>(emptyList())

    val output by optional<OutputSpec>(OutputSpec())

    val stopOnFailure by optional<Boolean?>(null)
}

data class OutputSpec(
    val dir: String = "output",
    val ffmpegOptions: String? = null,
    val enabled: Boolean = true,
    val id3Version: String? = null,
    val dryRun: Boolean = false,
)

fun interface OutputFactory {
    fun build(config: OutputSpec): OutputGateway
}

fun interface SourceFactory {
    fun build(program: String, spec: SourceSpec): Source
}

object DefaultSourceFactory : SourceFactory {
    override fun build(program: String, spec: SourceSpec): Source {
        val type = spec.type

        return when (type) {
            SourceType.Empty -> EmptySource
            SourceType.FTP -> {
                FTPSource(program, FTPConnection(
                    spec.url ?: error("The 'url' field is required for a FTP source."),
                    spec.username,
                    spec.password,
                    spec.path,
                    spec.port ?: 21,
                ))
            }

            SourceType.NextCloud -> NextCloudSource(
                spec.url ?: error("The 'url' field is required for a NextCloud source."),
                spec.path ?: error("The 'path' field is required for a NextCloud source."),
                spec.username ?: error("The 'username' field is required for a NextCloud source."),
                spec.password,
                spec.depth,
                program,
            )

            SourceType.Custom -> Class.forName(
                spec.`class` ?: error("The 'class' field is required for a Custom source.")
            ).getConstructor().newInstance() as Source
        }
    }
}

class DefaultOutputFactory(
    private val fileSystem: FileSystem,
    private val libreTimeConnector: LibreTimeConnector,
) : OutputFactory {
    override fun build(config: OutputSpec): OutputGateway {
        return if (config.enabled) {
            FileOutput(
                fileSystem.getPath(config.dir),
                libreTimeConnector,
                config.ffmpegOptions,
                config.dryRun,
                config.id3Version,
            )
        } else EmptyOutputGateway
    }
}

object EmptyOutputGateway : OutputGateway {
    override suspend fun save(items: List<OutputItem>) {}
}

class ConfigInput(
    private val sourceFactory: SourceFactory,
    private val outputFactory: OutputFactory,
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

    override fun programs(): List<Program> {
        return config[FileSyncSpec.programs].map { program ->
            val source = sourceFactory.build(program.name, program.source)

            val parse = program.parse?.toParse()

            Program(program.name, source, parse, program.output)
        }
    }

    override fun output(): OutputGateway {
        return outputFactory.build(config[FileSyncSpec.output])
    }
}
