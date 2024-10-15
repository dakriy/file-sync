package com.persignum.filesync.gateways

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.cio.*
import java.nio.file.Path
import java.util.*
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface OutputConnector {
    suspend fun exists(filename: String): Boolean
    suspend fun upload(file: Path)
}

@Serializable
private data class LibreTimeFile(
    val filepath: String?,
)

class LibreTimeApi(
    private val libreTimeUrl: String,
    private val apiKey: String,
    private val httpClient: HttpClient,
) : OutputConnector {
    constructor(map: Map<String, String>) : this(
        map["url"]!!,
        map["apiKey"]!!,
        HttpClient(Java) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
    )

    private val logger = KotlinLogging.logger {}
    private val fileNames by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runBlocking {
            val response: HttpResponse = httpClient.get("$libreTimeUrl/api/v2/files?format=json") {
                headers {
                    append("Authorization", "Api-Key $apiKey")
                }
            }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                logger.error { "Could not get existing files from LibreTime. Status code was ${response.status.value}: $body" }
                error("Could not get existing files from LibreTime.")
            }

            val body = response.body<List<LibreTimeFile>>()

            body.mapNotNull { it.filepath?.substringAfterLast("/") }.toSet()
        }
    }

    override suspend fun exists(filename: String) = filename in fileNames

    override suspend fun upload(file: Path) {
        val response: HttpResponse = httpClient.submitFormWithBinaryData(
            url = "$libreTimeUrl/rest/media",
            formData = formData {
                append("file", ChannelProvider { file.readChannel() }, Headers.build {
                    append(HttpHeaders.ContentType, ContentType.defaultForFile(file))
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
            }
        ) {
            val key = Base64.getEncoder().encodeToString("$apiKey:".toByteArray())
            header(HttpHeaders.Authorization, "Basic $key")
        }

        val body = response.bodyAsText()
        logger.debug { "Response for uploading ${file.pathString}: $body" }

        if (response.status.isSuccess()) {
            logger.info { "Successfully uploaded ${file.pathString} to LibreTime." }
        } else {
            error("Could not upload ${file.pathString} to LibreTime. Status code was ${response.status.value}: $body")
        }
    }
}

object NullOutputConnector : OutputConnector {
    override suspend fun exists(filename: String): Boolean {
        return false
    }

    override suspend fun upload(file: Path) {
    }
}
