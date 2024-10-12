package com.persignum.filesync

import java.nio.file.Path
import com.persignum.filesync.gateways.LibreTimeConnector

class LibreTimeStub : LibreTimeConnector {
    var existingFiles = mutableListOf<String>()
    var uploads = mutableListOf<Path>()
    override suspend fun exists(filename: String): Boolean = filename in existingFiles

    override suspend fun upload(file: Path) {
        uploads.add(file)
    }
}
