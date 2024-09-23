package org.klrf.filesync.gateways

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.Feature
import java.nio.file.FileSystem
import java.time.format.DateTimeFormatter
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
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

data class DatabaseSpec(
    val url: String,
    val user: String = "",
    val password: String = "",
)

object FileSyncSpec : ConfigSpec() {
    val programs by optional<Map<String, ProgramSpec>>(emptyMap())

    object HistorySpec : ConfigSpec() {
        val db by optional<DatabaseSpec?>(null)
    }

    val output by optional<OutputSpec>(OutputSpec())
}

data class OutputSpec(
    val dir: String = "",
    val enabled: Boolean = true,
)

fun interface OutputGatewayFactory {
    fun build(config: OutputSpec): OutputGateway
}

fun interface FTPConnectorFactory {
    fun build(connection: FTPConnection): FTPConnector
}

class DefaultOutputGatewayFactory(
    private val fileSystem: FileSystem,
) : OutputGatewayFactory {
    override fun build(config: OutputSpec): OutputGateway {
        return if (config.enabled) {
            FileOutput(fileSystem.getPath(config.dir))
        } else OutputGateway { SaveStatus(it, emptyList()) }
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

    val db by lazy {
        config[FileSyncSpec.HistorySpec.db]?.let {
            Database.connect(it.url, user = it.user, password = it.password)
        }
    }

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

    override fun history() =
        db?.let { db ->
            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(FileSyncTable)
            }
            DatabaseHistory(db)
        } ?: EmptyHistory

    override fun output(): OutputGateway {
        return outputGatewayFactory.build(config[FileSyncSpec.output])
    }
}
