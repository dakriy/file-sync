package com.persignum.filesync

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.persignum.filesync.domain.FileSync
import com.persignum.filesync.gateways.ConfigInput
import com.persignum.filesync.gateways.DefaultOutputFactory
import com.persignum.filesync.gateways.DefaultSourceFactory
import com.persignum.filesync.gateways.FileSyncSpec
import java.io.File
import java.nio.file.FileSystems

class CLI : CliktCommand(name = "file-sync") {
    private val file by option("--file", "-f")
        .help("Path to config file.")
        .file()
        .default(File("config.yaml"))
    private val dryRun by option("--dry-run", "-d")
        .help("Disables all downloading/uploading.")
        .flag()
    private val stopOnFail by option("--stop-on-fail", "-x")
        .help("Stops processing on first error.").flag()
    private val outputDir by option("--output-dir", "-o")
        .help("Output directory.").path()
    private val logLevel by option("--log-level", "-l")
        .help("Sets the log level.")
    private val programs by option("--program", "-p")
        .help("Process single program").multiple()
    private val sources by option("--source", "-s")
        .help("Process all programs from a source.")
        .multiple()
    private val cpus by option("--cpus", "-c")
        .help("Maximum number of CPU's to use for file processing.")
        .int()

    override fun run() {
        logLevel?.let { logLevel ->
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.lowercase())
        }

        if (!file.exists()) {
            echo("Config file not found.")
            throw ProgramResult(1)
        }

        val input = ConfigInput(
            DefaultSourceFactory,
            DefaultOutputFactory(FileSystems.getDefault()),
            programs,
            sources,
        ) {
            from.file(file)
        }

        if (outputDir != null) {
            input.config[FileSyncSpec.output] =
                input.config[FileSyncSpec.output].copy(dir = outputDir.toString())
        }

        cpus?.let { input.config[FileSyncSpec.maxConcurrentDownloads] = it }

        if (stopOnFail) {
            input.config[FileSyncSpec.stopOnFailure] = true
        }

        if (dryRun) {
            input.config[FileSyncSpec.output] =
                input.config[FileSyncSpec.output].copy(dryRun = true)
        }

        val fileSync = FileSync(input)

        fileSync.sync()
    }
}
