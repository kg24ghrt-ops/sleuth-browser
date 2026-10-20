package com.sleuth.browser.data

data class CaseFile(
    val id: Long = 0L,
    val code: String = "",
    val title: String = "",
    val notes: String = "",
    val createdAt: Long = 0L,
    val stakeout: Boolean = false
)

data class Evidence(
    val id: Long = 0L,
    val caseId: Long = 0L,
    val type: String = "",
    val title: String = "",
    val url: String = "",
    val note: String = "",
    val path: String = "",
    val createdAt: Long = 0L,
    val meta: String = ""
)

data class HistoryEntry(
    val id: Long = 0L,
    val url: String = "",
    val title: String = "",
    val visitedAt: Long = 0L,
    val visits: Int = 1
)

data class BookmarkEntry(
    val id: Long = 0L,
    val url: String = "",
    val title: String = "",
    val createdAt: Long = 0L
)

data class CaseEvent(
    val id: Long = 0L,
    val caseId: Long = 0L,
    val kind: String = "",
    val detail: String = "",
    val url: String = "",
    val createdAt: Long = 0L
)

object EvidenceType {
    const val SCREENSHOT = "SCREENSHOT"
    const val PAGE = "PAGE"
    const val NOTE = "NOTE"
    const val EXCERPT = "EXCERPT"
    const val PROTECTION = "PROTECTION"
}
