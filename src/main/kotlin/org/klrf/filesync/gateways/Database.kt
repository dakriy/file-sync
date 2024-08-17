package org.klrf.filesync.gateways

import org.jetbrains.exposed.sql.Table

object FileSyncTable : Table("filesync") {
    val program = varchar("program", 255)
    val name = varchar("name", 260)
    val hash = varchar("hash", 128)

    init {
        index(true, hash)
    }

    override val primaryKey: PrimaryKey = PrimaryKey(program, name)
}
