package org.klrf.filesync.gateways

import com.github.sardine.SardineFactory
import io.ktor.http.*
import java.io.InputStream
import java.time.Instant
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.Source

data class NextCloudSource(
    private val url: String,
    private val path: String,
    private val username: String,
    private val password: String?,
    private val depth: Int,
    private val program: String,
) : Source {
    private inner class NextCloudItem(
        override val name: String,
        override val createdAt: Instant,
        override val program: String,
        val path: String,
    ) : Item {
        override suspend fun data(): InputStream {
            val fileLocation = "$url${path.encodeURLPath()}"
            val sardine = SardineFactory.begin(username, password)
            return sardine.get(fileLocation)
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
                NextCloudItem(item.name, item.modified.toInstant(), program, item.path)
            }
        sardine.shutdown()

        return items.asSequence()
    }
}
