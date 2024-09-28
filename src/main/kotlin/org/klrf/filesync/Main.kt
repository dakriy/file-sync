package org.klrf.filesync

import java.nio.file.FileSystems
import org.klrf.filesync.domain.FileSync
import org.klrf.filesync.gateways.ConfigInput
import org.klrf.filesync.gateways.DefaultOutputFactory
import org.klrf.filesync.gateways.DefaultSourceFactory

fun main(args: Array<String>) {
    val input = ConfigInput(DefaultSourceFactory, DefaultOutputFactory(FileSystems.getDefault())) {
        from.file("config.yaml")
    }
    val fileSync = FileSync(input)
    fileSync.sync()
}
