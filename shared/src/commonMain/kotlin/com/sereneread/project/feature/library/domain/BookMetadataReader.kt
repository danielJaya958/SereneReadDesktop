package com.sereneread.project.feature.library.domain

data class BookMetadata(
    val title: String?,
    val author: String?,
)

/** Reads embedded metadata from a book file. Returns null when the format has none or the file is unreadable. */
fun interface BookMetadataReader {
    suspend fun read(filePath: String, format: BookFormat): BookMetadata?
}
