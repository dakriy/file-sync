package org.klrf.filesync.gateways

import org.jetbrains.exposed.sql.Table

object FileSyncTable : Table("filesync") {
    val program = varchar("program", 255)
    val name = varchar("name", 260)
    val sha256 = varchar("sha256", 64)

    init {
        index(true, sha256)
    }

    override val primaryKey: PrimaryKey = PrimaryKey(program, name)
}
