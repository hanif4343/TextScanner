package com.hanif.textscanner.data

data class ScanSession(
    val id: Long,
    val title: String,
    val pages: List<ScanPage> = emptyList(),
    val totalExpected: Int = 0,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val folderId: String = "default"
) {
    val completedPages: Int get() = pages.count { it.text.isNotBlank() }

    val fullText: String get() = pages
        .sortedBy { it.pageNumber }
        .filter { it.text.isNotBlank() }
        .joinToString("\n\n") { p ->
            if (pages.size > 1) "=== পৃষ্ঠা ${p.pageNumber} ===\n${p.text}"
            else p.text
        }

    val wordCount: Int get() = fullText.trim()
        .split(Regex("\\s+")).count { it.isNotEmpty() }

    val isComplete: Boolean get() = status == SessionStatus.COMPLETE
    val isPartial: Boolean get() = status == SessionStatus.IN_PROGRESS && pages.isNotEmpty()
}

data class ScanPage(
    val pageNumber: Int,
    val text: String,
    val imageUri: String = "",
    val scannedAt: Long = System.currentTimeMillis(),
    val failed: Boolean = false
)

enum class SessionStatus {
    IN_PROGRESS,
    COMPLETE,
    FAILED
}

data class ScanFolder(
    val id: String,
    val name: String,
    val icon: String = "📁",
    val createdAt: Long = System.currentTimeMillis()
)
