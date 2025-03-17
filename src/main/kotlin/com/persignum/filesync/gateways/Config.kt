package com.persignum.filesync.gateways

import com.persignum.filesync.domain.*
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.Feature
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.FileSystem
import java.time.format.DateTimeFormatter

enum class SourceType {
    Empty,
    FTP,
    NextCloud,
    Custom,
}

data class SourceSpec(
    val name: String,
    val type: SourceType,
    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val port: Int? = null,
    val ignoreCertificate: Boolean = false,
    val `class`: String? = null,
    val maxConcurrentDownloads: Int = 1,
)

data class SourceImplSpec(
    val name: String,
    val depth: Int = 1,
    val path: String? = null,
    val extensions: List<String>? = null,
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
    val parse: ParseSpec? = null,
    val output: Output? = null,
    val source: SourceImplSpec? = null,
)

object FileSyncSpec : ConfigSpec() {
    val programs by optional<List<ProgramSpec>>(emptyList())

    val output by optional<OutputSpec>(OutputSpec())

    val sources by optional<List<SourceSpec>>(emptyList())

    val stopOnFailure by optional<Boolean>(false)
}

data class OutputSpec(
    val dir: String = "output",
    val ffmpegOptions: String? = null,
    val id3Version: String? = null,
    val dryRun: Boolean = false,
    val connector: OutputConnectorSpec? = null,
)

data class OutputConnectorSpec(
    val `class`: String,
    val properties: Map<String, String> = emptyMap(),
)

fun interface OutputFactory {
    fun build(config: OutputSpec, limits: Map<String, Int>): OutputGateway
}

fun interface SourceFactory {
    fun build(program: String, spec: SourceSpec, impl: SourceImplSpec?): Source
}

object DefaultSourceFactory : SourceFactory {
    private fun missingFieldError(fieldName: String, type: SourceType, name: String): Nothing =
        error("The '$fieldName' field is required for the ${type.name} source '$name'.")

    override fun build(program: String, spec: SourceSpec, impl: SourceImplSpec?): Source {
        val type = spec.type

        return when (type) {
            SourceType.Empty -> EmptySource(spec.name)
            SourceType.FTP -> {
                val fullUrl = spec.url ?: missingFieldError("url", SourceType.FTP, spec.name)
                val protocol = fullUrl.substringBefore("://", "").lowercase()
                val url = fullUrl.substringAfter("://")
                val security = when (protocol) {
                    "ftp", "" -> FTPConnection.Security.None
                    "ftps" -> FTPConnection.Security.FTPS
                    "ftpes" -> FTPConnection.Security.FTPES
                    else -> error("Unknown protocol type '$protocol' for source '${spec.name}'.")
                }

                FTPSource(
                    spec.name, FTPConnection(
                        url,
                        spec.port ?: 21,
                        spec.username,
                        spec.password,
                        impl?.path,
                        security,
                        spec.ignoreCertificate,
                    ),
                    depth = impl?.depth ?: 1
                )
            }

            SourceType.NextCloud -> NextCloudSource(
                spec.name,
                spec.url ?: missingFieldError("url", SourceType.NextCloud, spec.name),
                impl?.path
                    ?: error("The 'path' field is required for the ${SourceType.NextCloud.name} source on program '$program'."),
                spec.username ?: missingFieldError("username", SourceType.NextCloud, spec.name),
                spec.password,
                impl.depth,
            )

            SourceType.Custom -> Class.forName(
                spec.`class` ?: missingFieldError("class", SourceType.Custom, spec.name),
            ).getConstructor(String::class.java, SourceSpec::class.java, SourceImplSpec::class.java)
                .newInstance(spec.name, spec, impl) as Source
        }
    }
}

class DefaultOutputFactory(
    private val fileSystem: FileSystem,
    private val outputConnector: OutputConnector? = null
) : OutputFactory {
    override fun build(
        config: OutputSpec,
        limits: Map<String, Int>,
    ): OutputGateway {
        val connector = outputConnector
            ?: buildConnectorWithReflection(config.connector)

        return FileOutput(
            fileSystem.getPath(config.dir),
            connector,
            config.ffmpegOptions,
            config.dryRun,
            limits,
            config.id3Version,
        )
    }

    private fun buildConnectorWithReflection(spec: OutputConnectorSpec?) =
        if (spec != null) {
            val clazz = this::class.java.classLoader.loadClass(spec.`class`)

            clazz.getConstructor(Map::class.java).newInstance(spec.properties) as OutputConnector
        } else NullOutputConnector
}

object EmptyOutputGateway : OutputGateway {
    override suspend fun save(items: List<OutputItem>) {}
}

class ConfigInput(
    private val sourceFactory: SourceFactory,
    private val outputFactory: OutputFactory,
    private val programsFilter: List<String> = emptyList(),
    private val sourcesFilter: List<String> = emptyList(),
    sourceConfig: Config.() -> Config,
) : InputGateway {
    private val logger = KotlinLogging.logger {}
    val config = Config {
        addSpec(FileSyncSpec)
        enable(Feature.OPTIONAL_SOURCE_BY_DEFAULT)
    }
        .run(sourceConfig)
        .from.env()
        .from.systemProperties()

    override val stopOnFailure: Boolean
        get() = config[FileSyncSpec.stopOnFailure]

    override fun programs(): List<Program> {
        val sources = config[FileSyncSpec.sources].associateBy { it.name }
        return config[FileSyncSpec.programs].mapNotNull { program ->
            if (programsFilter.isNotEmpty() && program.name !in programsFilter) {
                logger.debug { "Skipping ${program.name} as only $programsFilter were requested." }
                return@mapNotNull null
            }
            if (sourcesFilter.isNotEmpty() && program.source?.name !in sourcesFilter) {
                logger.debug { "Skipping ${program.name} as only programs in $sources were requested." }
                return@mapNotNull null
            }

            val sourceSpec = sources[program.source?.name] ?: SourceSpec("", SourceType.Empty)
                .also { logger.warn { "Unable to find source ${program.name}." } }

            val source = sourceFactory.build(program.name, sourceSpec, program.source)

            val parse = program.parse?.toParse()

            Program(
                program.name,
                source,
                parse,
                program.output,
                program.source?.extensions?.toSet()
            )
        }
    }

    override fun output(): OutputGateway {
        val limits = config[FileSyncSpec.sources].associateBy(
            SourceSpec::name,
            SourceSpec::maxConcurrentDownloads,
        )
        return outputFactory.build(config[FileSyncSpec.output], limits)
    }
}
