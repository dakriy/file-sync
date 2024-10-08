package org.klrf.filesync.gateways

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import kotlin.io.path.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.reference.ID3V2Version
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.OutputGateway
import org.klrf.filesync.domain.OutputItem

class FileOutput(
    private val directory: Path,
    private val libreTimeConnector: LibreTimeConnector,
    private val ffmpegOptions: String?,
    private val dryRun: Boolean,
    downloadLimits: Map<String, Int>,
    id3Version: String?,
) : OutputGateway {
    private val logger = KotlinLogging.logger {}
    private val programSemaphores = downloadLimits.mapValues { Semaphore(it.value) }

    init {
        try {
            if (id3Version != null) {
                TagOptionSingleton.getInstance().iD3V2Version = ID3V2Version.valueOf(id3Version)
            }
        } catch (_: IllegalArgumentException) {
            error("Unknown id3Version '$id3Version'. Valid values are ${ID3V2Version.entries.map { it.name }}.")
        }
    }

    override suspend fun save(items: List<OutputItem>) {
        val transformDir = directory / "transform"
        createDirectories(items, transformDir)

        supervisorScope {
            items.forEach { item ->
                launch(Dispatchers.IO) {
                    try {
                        pipeline(item, transformDir)
                    } catch (ex: Throwable) {
                        logger.error(ex) { "Error when processing $item" }
                    }
                }
            }
        }
    }

    private suspend fun pipeline(item: OutputItem, transformDir: Path) {
        if (libreTimeConnector.exists(item.file)) {
            logger.info { "Skipping $item as it exists in LibreTime." }
            return
        }

        val file = download(item)

        val outFile = transformDir / item.program / item.file

        if (item.format != item.computeFormatFromName() || ffmpegOptions != null) {
            convert(file, outFile)
        } else file.copyTo(outFile, overwrite = true)

        addAudioTags(item, outFile)

        setCreationTime(outFile, item)

        if (!dryRun) {
            logger.info { "Uploading $item" }
            libreTimeConnector.upload(outFile)
        } else {
            logger.info { "Not uploading $item because dry run is set." }
        }
    }

    private fun createDirectories(items: List<OutputItem>, transformDir: Path) {
        val programs = items.map { it.program }.distinct()
        programs.forEach { program ->
            (directory / program).createDirectories()
            (transformDir / program).createDirectories()
        }
    }

    suspend fun download(item: OutputItem): Path {
        val file = directory / item.program / item.name
        if (file.exists()) return file
        withContext(Dispatchers.IO) {
            val semaphore = programSemaphores[item.source]
            semaphore?.acquire()
            logger.info { "Downloading $file" }
            try {
                item.data().use { inputStream ->
                    Files.newOutputStream(
                        file,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    ).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } finally {
                semaphore?.release()
            }
        }
        setCreationTime(file, item)

        return file
    }

    private fun convert(input: Path, output: Path) {
        val ffmpegPath = System.getenv("FFMPEG") ?: "ffmpeg"

        val options = ffmpegOptions?.split(" ")?.toTypedArray() ?: emptyArray()

        val command = arrayOf(
            ffmpegPath,
            "-y",
            "-i",
            input.absolutePathString(),
            *options,
            output.absolutePathString()
        )
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        val ffmpegOutput = process.inputStream.bufferedReader().use { reader ->
            reader.lines().toList()
        }

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("FFMPEG failed. Output: ${ffmpegOutput.joinToString("\n")}")
        }
    }

    private fun setCreationTime(path: Path, item: Item) {
        val fileTime = FileTime.from(item.createdAt)
        path.setAttribute("creationTime", fileTime)
        path.setLastModifiedTime(fileTime)
    }

    private fun addAudioTags(item: OutputItem, path: Path) {
        if (item.tags.isEmpty()) return

        val file = try {
            path.toFile()
        } catch (e: UnsupportedOperationException) {
            return
        }

        val f = AudioFileIO.read(file)
        val tag = f.tagAndConvertOrCreateAndSetDefault
        item.tags.forEach { (key, value) ->
            val fieldKey = try {
                FieldKey.valueOf(key.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn { "Tag $key not valid tag. Valid tags are ${FieldKey.entries.map { it.name.lowercase() }}." }
                return@forEach
            }
            tag.setField(fieldKey, value)
        }
        f.commit()
    }
}
