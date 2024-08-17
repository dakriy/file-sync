package org.klrf.filesync.gateways

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.transaction
import org.klrf.filesync.domain.History
import org.klrf.filesync.domain.Item

object EmptyHistory : History {
    override fun add(item: Item) {}

    override fun exists(item: Item) = false
}

class DatabaseHistory(private val db: Database) : History {
    override fun add(item: Item) {
        TODO("Not yet implemented")
    }

    override fun exists(item: Item) = transaction(db) {
        FileSyncTable
            .select(stringLiteral("X"))
            .where {
                (FileSyncTable.program eq item.program)
                    .and(FileSyncTable.name eq item.name)
            }
            .count()
    } > 0
}
