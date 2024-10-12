package com.persignum.filesync.domain

data class Program(
    val name: String,
    val source: Source,
    val parse: Parse?,
    val output: Output?,
    val extensions: Set<String>?,
)
