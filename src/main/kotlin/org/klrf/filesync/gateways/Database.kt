package org.klrf.filesync.gateways

import org.jetbrains.exposed.sql.Table

object FileSyncTable : Table("filesync") {
    val program = varchar("program", 255)
    val name = varchar("name", 260)

    override val primaryKey: PrimaryKey = PrimaryKey(program, name)
}
