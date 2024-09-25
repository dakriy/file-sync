package org.klrf.filesync.gateways

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level.DEBUG
import java.io.IOException
import java.io.PrintWriter
import java.time.Instant
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

data class FTPConnection(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null,
    val port: Int,
)

data class FTPSource(
    private val program: String,
    private val connection: FTPConnection,
) : Source {
    private val logger = KotlinLogging.logger {}

    inner class FTPItem(
        override val name: String,
        override val createdAt: Instant,
    ) : Item {
        override val program: String = this@FTPSource.program
        override suspend fun data() = ftpAction { ftp ->
            val path = connection.path ?: ""
            ftp.retrieveFileStream("$path/$name")
                ?: throw IOException("File not found: $path/$name")
        }
    }

    override fun listItems(): Sequence<Item> = ftpAction { ftp ->
        val files = ftp.listFiles(connection.path)
        files
            .filter(FTPFile::isFile)
            .map { FTPItem(it.name, it.timestampInstant) }
            .sortedByDescending { it.createdAt }
    }.asSequence()

    private fun <T> ftpAction(action: (FTPClient) -> T): T {
        val ftp = FTPClient()

        return AutoCloseable(ftp::disconnect).use {
            if (logger.isLoggingEnabledFor(DEBUG)) {
                ftp.addProtocolCommandListener(PrintCommandListener(PrintWriter(System.out)))
            }

            ftp.connect(connection.url, connection.port)
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
