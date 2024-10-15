package com.persignum.filesync

import java.nio.file.Path
import com.persignum.filesync.gateways.OutputConnector

class OutputStub : OutputConnector {
    var existingFiles = mutableListOf<String>()
    var uploads = mutableListOf<Path>()
    override suspend fun exists(filename: String): Boolean = filename in existingFiles

    override suspend fun upload(file: Path) {
        uploads.add(file)
    }
}
