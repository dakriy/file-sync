package com.persignum.filesync

import java.nio.file.FileSystems
import com.persignum.filesync.domain.FileSync
import com.persignum.filesync.gateways.ConfigInput
import com.persignum.filesync.gateways.DefaultOutputFactory
import com.persignum.filesync.gateways.DefaultSourceFactory

fun main(args: Array<String>) {
    val input = ConfigInput(DefaultSourceFactory, DefaultOutputFactory(FileSystems.getDefault())) {
        from.file("config.yaml")
    }
    val fileSync = FileSync(input)
    fileSync.sync()
}
