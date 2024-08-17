package org.klrf.filesync.gateways

import java.time.Instant
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

interface FTPConnector {
    val connection: FTPConnection

    fun listFiles(): Sequence<Pair<String, Instant>>
//    fun downloadFile(file: String): ByteArray
}

data class FTPConnection(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null
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
        override fun data(): ByteArray = ByteArray(0)
    }

    override fun listItems(): Sequence<Item> {
        return connector.listFiles().map(::FTPItem)
    }
}
//
//class RealFTPConnector(
//    override val connection: FTPConnection,
//    private val debug: Boolean = false,
//) : FTPConnector {
//    override fun listFiles(): List<String> {
//        val ftp = FTPClient()
//
//        if (debug) {
//            ftp.addProtocolCommandListener(PrintCommandListener(PrintWriter(System.out)))
//        }
//
//        ftp.connect(connection.url)
//        val reply = ftp.replyCode
//        if (FTPReply.isPositiveCompletion(reply)) {
//            ftp.disconnect()
//            throw IOException("Unable to connect to FTP server")
//        }
//
//        val success = ftp.login(
//            connection.username ?: "anonymous",
//            connection.password ?: "anonymous@domain.com",
//        )
//        if (!success) {
//            throw IOException("Unable to login to FTP server")
//        }
//
//        val files = ftp.listFiles(connection.path)
//
//        TODO()
//    }
//}
