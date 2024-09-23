package org.klrf.filesync.gateways

import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import kotlin.io.path.*
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.OutputGateway
import org.klrf.filesync.domain.OutputItem

class FileOutput(
    private val directory: Path,
    private val ffmpegOptions: String?,
) : OutputGateway {
    private fun convert(input: Path, output: Path) {
        val ffmpegPath = System.getenv("FFMPEG") ?: "ffmpeg"

        val options = ffmpegOptions?.split(" ")?.toTypedArray() ?: emptyArray()

        val command = arrayOf(ffmpegPath, "-y", "-i", input.absolutePathString(), *options, output.absolutePathString())
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { reader ->
            reader.lines().toList()
        }

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("FFMPEG failed. Output: ${output.joinToString("\n")}")
        }
    }

    private fun setCreationTime(path: Path, item: Item) {
        val fileTime = FileTime.from(item.createdAt)
        path.setAttribute("creationTime", fileTime)
        path.setLastModifiedTime(fileTime)
    }

    fun download(item: Item): Path {
        val file = directory / item.program / item.name
        file.writeBytes(
            item.data(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        setCreationTime(file, item)

        return file
    }

    override fun save(items: List<OutputItem>) {
        val programs = items.map { it.program }.distinct()
        val transformDir = directory / "transform"
        programs.forEach { program ->
            (directory / program).createDirectories()
            (transformDir / program).createDirectories()
        }

        items.map { item ->
            val ex = try {
                val file = download(item)

                val outFile = transformDir / item.program / item.file
                if (item.format != item.computeFormatFromName()) {
                    convert(file, outFile)
                } else file.copyTo(outFile)
                setCreationTime(outFile, item)

                null
            } catch (ex: Throwable) {
                ex
            }
            if (ex != null) throw ex
            item to ex
        }

        // Download -- DONE
        // FFMPEG to convert
        // FFMPEG audio normalization
        // ffmpeg -y -i "$file" -q:a 1 -filter:a loudnorm=I=-23.0:offset=0.0:print_format=summary:linear=false:dual_mono=true "Processing$filename.mp3"
        // tag audio
        // LibreTime upload
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