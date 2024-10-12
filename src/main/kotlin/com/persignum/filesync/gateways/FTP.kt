package com.persignum.filesync.gateways

import java.io.IOException
import java.io.PrintWriter
import java.time.Instant
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
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
) : Source {
    inner class FTPItem(
        override val name: String,
        override val createdAt: Instant,
    ) : Item {
        override suspend fun data() = ftpAction { ftp ->
            val path = connection.path ?: ""
            ftp.retrieveFileStream("$path/$name")
                ?: throw IOException("File not found: $path/$name")
        }
    }

    override fun listItems(): Sequence<Item> = ftpAction { ftp ->
        ftp.cwd(connection.path)
        val files = ftp.listFiles()
        files
            .filter(FTPFile::isFile)
            .map { FTPItem(it.name, it.timestampInstant) }
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
