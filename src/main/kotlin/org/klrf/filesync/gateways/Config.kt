package org.klrf.filesync.gateways

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.Feature
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import java.nio.file.FileSystem
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import org.klrf.filesync.domain.*

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
    val `class`: String? = null,
//    val rateLimit: Int = 10,
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

    val stopOnFailure by optional<Boolean?>(null)
}

data class OutputSpec(
    val dir: String = "output",
    val ffmpegOptions: String? = null,
    val enabled: Boolean = true,
    val id3Version: String? = null,
    val dryRun: Boolean = false,
    val libreTime: LibreTimeSpec? = null,
)

data class LibreTimeSpec(
    val url: String,
    val apiKey: String,
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
            SourceType.Empty -> EmptySource
            SourceType.FTP -> {
                FTPSource(
                    program, FTPConnection(
                        spec.url ?: missingFieldError("url", SourceType.FTP, spec.name),
                        spec.username,
                        spec.password,
                        impl?.path,
                        spec.port ?: 21,
                    )
                )
            }

            SourceType.NextCloud -> NextCloudSource(
                spec.url ?: missingFieldError("url", SourceType.NextCloud, spec.name),
                impl?.path ?: error("The 'path' field is required for the ${SourceType.NextCloud.name} source on program '$program'."),
                spec.username ?: missingFieldError("username", SourceType.NextCloud, spec.name),
                spec.password,
                impl.depth,
                program,
            )

            SourceType.Custom -> Class.forName(
                spec.`class` ?: missingFieldError("class", SourceType.Custom, spec.name),
            ).getConstructor(String::class.java, SourceSpec::class.java, SourceImplSpec::class.java)
                .newInstance(program, spec, impl) as Source
        }
    }
}

class DefaultOutputFactory(
    private val fileSystem: FileSystem,
    private val libreTimeConnector: LibreTimeConnector? = null
) : OutputFactory {
    override fun build(config: OutputSpec, limits: Map<String, Int>): OutputGateway {
        return if (config.enabled) {
            val libreTimeConnector = libreTimeConnector
                ?: buildLibreTimeConnector(config.libreTime)

            FileOutput(
                fileSystem.getPath(config.dir),
                libreTimeConnector,
                config.ffmpegOptions,
                config.dryRun,
                limits,
                config.id3Version,
            )
        } else EmptyOutputGateway
    }

    private fun buildLibreTimeConnector(spec: LibreTimeSpec?) =
        if (spec != null) {
            LibreTimeApi(spec.url, spec.apiKey, HttpClient(Java) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                    })
                }
            })
        } else NullLibreTimeConnector
}

object EmptyOutputGateway : OutputGateway {
    override suspend fun save(items: List<OutputItem>) {}
}

class ConfigInput(
    private val sourceFactory: SourceFactory,
    private val outputFactory: OutputFactory,
    sourceConfig: Config.() -> Config,
) : InputGateway {
    private val logger = KotlinLogging.logger {}
    private val config = Config {
        addSpec(FileSyncSpec)
        enable(Feature.OPTIONAL_SOURCE_BY_DEFAULT)
    }
        .run(sourceConfig)
        .from.env()
        .from.systemProperties()

    override val stopOnFailure: Boolean = config[FileSyncSpec.stopOnFailure] ?: false

    override fun programs(): List<Program> {
        val sources = config[FileSyncSpec.sources].associateBy { it.name }
        return config[FileSyncSpec.programs].map { program ->
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
        return outputFactory.build(config[FileSyncSpec.output], emptyMap())
    }
}
