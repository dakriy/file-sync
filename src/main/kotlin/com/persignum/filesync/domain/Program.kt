package com.persignum.filesync.domain


enum class SortMode(val comparator: Comparator<Item>) {
    DateAsc(compareBy(Item::createdAt)),
    DateDesc(compareByDescending(Item::createdAt)),
    NameAsc(compareBy(Item::name)),
    NameDesc(compareByDescending(Item::name)),
}

data class Program(
    val name: String,
    val source: Source,
    val parse: Parse?,
    val output: Output?,
    val extensions: Set<String>?,
    val sortMode: SortMode,
)
