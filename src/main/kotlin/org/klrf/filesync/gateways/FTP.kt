package org.klrf.filesync.gateways

import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.time.Instant
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

interface FTPConnector {
    val connection: FTPConnection

    fun listFiles(): Sequence<Pair<String, Instant>>
    fun downloadFile(file: String): InputStream
}

data class FTPConnection(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null,
    val port: Int = 21,
)

class FTPSource(
    private val program: String,
    private val connector: FTPConnector,
) : Source {
    inner class FTPItem(
        override val name: String,
        override val createdAt: Instant,
    ) : Item {
        constructor(p: Pair<String, Instant>) : this(p.first, p.second)
        override val program: String = this@FTPSource.program
        override suspend fun data() = connector.downloadFile(name)
    }

    override fun listItems(): Sequence<Item> {
        return connector.listFiles().map(::FTPItem)
    }
}

class RealFTPConnector(
    override val connection: FTPConnection,
    private val debug: Boolean = false,
) : FTPConnector {
    private fun <T> ftpAction(action: (FTPClient) -> T): T {
        val ftp = FTPClient()

        return AutoCloseable(ftp::disconnect).use {
            if (debug) {
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

    override fun listFiles() = ftpAction { ftp ->
        val files = ftp.listFiles(connection.path)
        files
            .filter(FTPFile::isFile)
            .map { it.name to it.timestampInstant }
    }.asSequence()

    override fun downloadFile(file: String) = ftpAction { ftp ->
        val path = connection.path ?: ""
        ftp.retrieveFileStream("$path/$file")
            ?: throw IOException("File not found: $path/$file")
    }
}
