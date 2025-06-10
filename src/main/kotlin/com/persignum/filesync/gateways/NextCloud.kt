package com.persignum.filesync.gateways

import com.github.sardine.SardineFactory
import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.Source
import io.ktor.http.*
import java.io.OutputStream
import java.time.Instant

data class NextCloudSource(
    override val name: String,
    private val url: String,
    private val path: String,
    private val username: String,
    private val password: String?,
    private val depth: Int,
) : Source {
    private inner class NextCloudItem(
        override val name: String,
        override val createdAt: Instant,
        val path: String,
    ) : Item {
        override suspend fun data(stream: OutputStream) {
            val fileLocation = "$url${path.encodeURLPath()}"
            val sardine = SardineFactory.begin(username, password)
            sardine.get(fileLocation).copyTo(stream)
        }
    }

    override fun listItems(): Sequence<Item> {
        val encodedPath = path.dropWhile { it == '/' }
            .encodeURLPath()

        val filesUrl = "$url/remote.php/dav/files/$username/$encodedPath"
        val sardine = SardineFactory.begin(username, password)

        val items = sardine.list(filesUrl, depth)
            .filter { !it.isDirectory }
            .map { item ->
                NextCloudItem(item.name, item.modified.toInstant(), item.path)
            }
        sardine.shutdown()

        return items.asSequence()
    }
}
