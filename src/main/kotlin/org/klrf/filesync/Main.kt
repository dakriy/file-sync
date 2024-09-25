package org.klrf.filesync

import org.klrf.filesync.domain.FileSync
import org.klrf.filesync.gateways.ConfigInput
import org.klrf.filesync.gateways.DefaultOutputFactory
import org.klrf.filesync.gateways.DefaultSourceFactory
import org.klrf.filesync.gateways.LibreTimeConnector
import java.nio.file.FileSystems
import java.nio.file.Path

object NullLibreTimeConnector : LibreTimeConnector {
    override suspend fun exists(filename: String): Boolean {
        return false
    }

    override suspend fun upload(file: Path) {
    }
}

fun main(args: Array<String>) {
    val input = ConfigInput(DefaultSourceFactory, DefaultOutputFactory(FileSystems.getDefault(), NullLibreTimeConnector)) {
        from.file("config.yaml")
    }
    val fileSync = FileSync(input)
    fileSync.sync()
}
