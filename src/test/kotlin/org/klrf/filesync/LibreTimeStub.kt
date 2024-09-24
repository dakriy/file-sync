package org.klrf.filesync

import java.nio.file.Path
import org.klrf.filesync.gateways.LibreTimeConnector

class LibreTimeStub(
    private val existingFiles: List<String> = emptyList(),
) : LibreTimeConnector{
    private var uploads = mutableListOf<Path>()
    override suspend fun exists(filename: String): Boolean = filename in existingFiles

    override suspend fun upload(file: Path) {
        uploads.add(file)
    }
}
