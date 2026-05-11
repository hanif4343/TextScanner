package com.hanif.textscanner.data

data class ScanHistory(
    val id: Long,
    val text: String,
    val sourceUri: String,
    val timestamp: Long,
    val wordCount: Int
)
