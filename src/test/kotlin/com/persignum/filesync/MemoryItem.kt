package com.persignum.filesync

import com.persignum.filesync.domain.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant

data class MemoryItem(
    override val name: String,
    override val createdAt: Instant = defaultTime,
    val data: ByteArray = ByteArray(0),
    private val dataHook: suspend () -> Unit = {},
) : Item {
    override suspend fun data(stream: OutputStream) =
        withContext(Dispatchers.IO) {
            dataHook()
            stream.write(data)
        }

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