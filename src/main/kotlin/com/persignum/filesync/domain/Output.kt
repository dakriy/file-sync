package com.persignum.filesync.domain

data class Output(
    val format: String? = null,
    val filename: String? = null,
    val tags: Map<String, String> = emptyMap(),
    val limit: Int? = null,
)
