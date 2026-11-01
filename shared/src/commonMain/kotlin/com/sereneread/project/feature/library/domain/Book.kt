package com.sereneread.project.feature.library.domain

data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val filePath: String,
    val format: BookFormat,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progress: Float,
)

enum class BookFormat(val extension: String) {
    EPUB("epub"),
    PDF("pdf"),
    TXT("txt");

    companion object {
        val extensions: Set<String> = entries.map { it.extension }.toSet()

        fun fromPath(path: String): BookFormat? {
            val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { it.extension == ext }
        }
    }
}
