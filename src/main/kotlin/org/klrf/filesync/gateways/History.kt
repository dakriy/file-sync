package org.klrf.filesync.gateways

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.klrf.filesync.domain.History
import org.klrf.filesync.domain.Item

object EmptyHistory : History {
    override fun add(items: Iterable<Item>) {}

    override fun exists(item: Item) = false
}

class DatabaseHistory(private val db: Database) : History {
    override fun add(items: Iterable<Item>) {
        transaction(db) {
            FileSyncTable.batchInsert(items) {
                this[FileSyncTable.program] = it.program
                this[FileSyncTable.name] = it.name
            }
        }
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
