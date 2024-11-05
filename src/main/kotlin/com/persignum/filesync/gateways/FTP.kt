package com.persignum.filesync.gateways

import java.io.IOException
import java.io.PrintWriter
import java.time.Instant
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.Source

data class FTPConnection(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null,
    val port: Int,
)

data class FTPSource(
    override val name: String,
    private val connection: FTPConnection,
    private val depth: Int,
) : Source {
    inner class FTPItem(
        override val name: String,
        override val createdAt: Instant,
        val path: String?,
    ) : Item {
        override suspend fun data() = ftpAction { ftp ->
            val file = if (path != null) {
                "$path/$name"
            } else name
            ftp.retrieveFileStream(file)
                ?: throw IOException("File not found: $file")
        }
    }

    private fun FTPClient.list(dir: String?, maxDepth: Int, current: Int = 0): List<FTPItem> {
        if (dir != null) {
            cwd(dir)
        }
        val all = listFiles()
        val files = all
            .filter { it.isFile }
            .map { FTPItem(it.name, it.timestampInstant, dir) }
        if (current >= maxDepth) return files

        val dirs = all.filter { it.isDirectory }
        return files + dirs.flatMap {
            val path = if (dir != null) {
                "$dir/${it.name}"
            } else it.name

            list(path, maxDepth, current + 1)
        }
    }

    override fun listItems(): Sequence<Item> = ftpAction { ftp ->
        ftp.list(connection.path, depth)
            .sortedByDescending { it.createdAt }
    }.asSequence()

    private fun <T> ftpAction(action: (FTPClient) -> T): T {
        val ftp = FTPClient()

        return AutoCloseable(ftp::disconnect).use {
            ftp.addProtocolCommandListener(PrintCommandListener(PrintWriter(System.out)))

            ftp.connect(connection.url, connection.port)
            ftp.enterLocalPassiveMode()
            val reply = ftp.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect()
                throw IOException("Unable to connect to FTP server")
            }

            val success = ftp.login(
                connection.username ?: "anonymous",
                connection.password ?: "anonymous@domain.com",
            )
            if (!success) throw IOException("Unable to login to FTP server")

            action(ftp)
        }
    }
}
