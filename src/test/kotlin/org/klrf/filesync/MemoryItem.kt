package org.klrf.filesync

import java.time.Instant
import org.klrf.filesync.domain.Item

data class MemoryItem(
    override val name: String,
    override val createdAt: Instant = defaultTime,
    val data: ByteArray = ByteArray(0),
    private val dataHook: suspend () -> Unit = {},
) : Item {
    override suspend fun data() = data.also { dataHook() }.inputStream()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryItem

        if (name != other.name) return false
        if (createdAt != other.createdAt) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        private val defaultTime = Instant.now()
    }
}