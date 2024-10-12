package com.persignum.filesync

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.persignum.filesync.domain.FileSync
import com.persignum.filesync.gateways.*
import java.io.File
import java.nio.file.FileSystems

class CLI : CliktCommand() {
    private val file by option("--file", "-f").help("Path to config file.").file().default(File("config.yaml"))
    private val dryRun by option("--dry-run", "-d").help("Disables all downloading/uploading.").flag()
    private val stopOnFail by option("--stop-on-fail", "-f").help("Stops processing on first error.").flag()
    private val outputDir by option("--output-dir", "-o").help("Output directory.").path()
    private val logLevel by option("--log-level", "-l").help("Sets the log level.")

    override fun run() {
        logLevel?.let { logLevel ->
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.lowercase())
        }

        val input = ConfigInput(DefaultSourceFactory, DefaultOutputFactory(FileSystems.getDefault())) {
            from.file(file)
        }

        if (outputDir != null) {
            input.config[FileSyncSpec.output] = input.config[FileSyncSpec.output].copy(dir = outputDir.toString())
        }

        if (stopOnFail) {
            input.config[FileSyncSpec.stopOnFailure] = true
        }

        if (dryRun) {
            input.config[FileSyncSpec.output] = input.config[FileSyncSpec.output].copy(dryRun = true)
        }

        val fileSync = FileSync(input)

        fileSync.sync()
    }
}
