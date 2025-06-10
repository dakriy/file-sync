package com.persignum.filesync.gateways

import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.Source
import it.sauronsoftware.ftp4j.FTPClient
import it.sauronsoftware.ftp4j.FTPFile
import java.io.OutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


data class FTPConnection(
    val url: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null,
    val security: Security = Security.None,
    val tlsResumption: Boolean = true,
    val ignoreCertificate: Boolean = false,
) {
    enum class Security {
        None,
        FTPS,
        FTPES,
    }
}

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
        override suspend fun data(stream: OutputStream) = ftpAction { ftp ->
            val file = if (path != null) {
                "$path/$name"
            } else name
            ftp.download(file, stream, 0, null)
        }
    }

    private fun FTPClient.list(dir: String?, maxDepth: Int, current: Int = 1): List<FTPItem> {
        if (dir != null) {
            changeDirectory(dir)
        }
        val all = list()
        val files = all
            .filter { it.type == FTPFile.TYPE_FILE }
            .map { FTPItem(it.name, it.modifiedDate.toInstant(), dir) }
        if (current >= maxDepth) return files

        val dirs = all.filter { it.type == FTPFile.TYPE_DIRECTORY }
        return files + dirs.flatMap {
            val path = if (dir != null) {
                "$dir/${it.name}"
            } else it.name

            list(path, maxDepth, current + 1)
        }
    }

    override fun listItems(): Sequence<Item> = ftpAction { ftp ->
        ftp.list(connection.path, depth)
    }.asSequence()

    private fun <T> ftpAction(action: (FTPClient) -> T): T {
        val ftp = FTPClient().apply {
            security = when (connection.security) {
                FTPConnection.Security.None -> FTPClient.SECURITY_FTP
                FTPConnection.Security.FTPS -> FTPClient.SECURITY_FTPS
                FTPConnection.Security.FTPES -> FTPClient.SECURITY_FTPES
            }
            reuseSession = connection.tlsResumption

            if (security != FTPClient.SECURITY_FTP && connection.ignoreCertificate) {
                val trustManager = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate>? {
                        return null
                    }
                    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {
                    }
                    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {
                    }
                })
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustManager, SecureRandom())
                val sslSocketFactory = sslContext.socketFactory
                setSSLSocketFactory(sslSocketFactory)
            }
        }

        ftp.connect(connection.url, connection.port)

        ftp.login(
            connection.username ?: "anonymous",
            connection.password ?: "anonymous@domain.com",
        )

        return AutoCloseable { ftp.disconnect(true) }.use {
            action(ftp)
        }
    }
}
