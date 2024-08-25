package org.klrf.filesync.gateways

import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import org.klrf.filesync.domain.OutputGateway
import org.klrf.filesync.domain.OutputItem
import kotlin.io.path.div
import kotlin.io.path.setAttribute
import kotlin.io.path.writeBytes

class FileOutput(
    private val directory: Path,
) : OutputGateway {
    override fun save(items: List<OutputItem>) {
        val programs = items.map { it.program }.distinct()
        programs.forEach { program ->
            (directory / program).createDirectories()
        }

        items.forEach { item ->
            val file = directory / item.program / item.file
            file.writeBytes(
                item.data(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            file.setAttribute("creationTime", FileTime.from(item.createdAt))
        }
    }
}

//class LibreTimeOutput(
//    private val url: String,
//    private val apiKey: String,
//    private val workDir: File,
//    private val httpClient: HttpClient,
//) : OutputGateway {
//    private val logger = KotlinLogging.logger { }
//
//    private fun saveFile(item: OutputItem): File {
//        val f = File(workDir, item.file)
//        f.writeBytes(item.data())
//        Files.setAttribute(f.toPath(), "creationTime", FileTime.from(item.createdAt))
//        return f
//    }
//
//    private fun tagFile(item: OutputItem, file: File) {
//        val f = AudioFileIO.read(file)
//        val tag = f.tagAndConvertOrCreateDefault
//        item.tags.forEach { (key, value) ->
//            tag.setField(FieldKey.valueOf(key.uppercase()), value)
//        }
//        f.commit()
//    }
//
//    override fun save(items: List<OutputItem>) {
//        val errors = mutableListOf<Throwable>()
//
//        items.forEach { item ->
//            try {
//                val f = saveFile(item)
//
//                FFmpegBuilder().apply {
//                    setInput(f.path)
//                    overrideOutputFiles(true)
//                    addOutput("${f.path}.tmp").apply {
//                        setFormat(item.format)
//                    }
//                }
//
//                tagFile(item, f)
//
//
//                runBlocking {
//                    val response: HttpResponse = httpClient.submitFormWithBinaryData(
//                        url = "$url/rest/media",
//                        formData = formData {
//                            append("file", item.data(), Headers.build {
//                                append(HttpHeaders.ContentType, ContentType.Audio.MPEG)
//                                append(
//                                    HttpHeaders.ContentDisposition,
//                                    "filename=\"${item.file}\""
//                                )
//                            })
//                        }
//                    ) {
//                        val key = Base64.getEncoder().encodeToString(("$apiKey:").toByteArray())
//                        header("Authorization", "Bearer $key")
//                    }
//
//                    val body = response.bodyAsText()
//                    logger.debug { "Response for file $item: $body" }
//
//                    if (response.status.isSuccess()) {
//                        logger.info { "Successfully uploaded $item " }
//                    } else {
//                        error("Failed uploading $item to LibreTime: $body")
//                    }
//                }
//            } catch (e: Exception) {
//                logger.error(e) { "Failed when processing $item." }
//                errors.add(e)
//            }
//        }
//
//
//        if (errors.isNotEmpty()) {
//            logger.error { "Errors occurred when processing some files. Check the logs." }
//            throw errors.first()
//        }
//    }
//}
