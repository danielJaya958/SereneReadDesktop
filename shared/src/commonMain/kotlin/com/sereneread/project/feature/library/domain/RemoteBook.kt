package com.sereneread.project.feature.library.domain

import com.sereneread.project.core.network.AppResult

/** A book stored on the server. [totalPages] is 0 until the server has processed the PDF. */
data class RemoteBook(
    val id: Long,
    val title: String,
    val author: String,
    val publishedYear: Int,
    val lastReadPage: Int,
    val totalPages: Int,
    val isFavorite: Boolean,
    /** A path on the server host. Null until the server has made the cover. */
    val thumbnailUrl: String? = null,
)

enum class BookSort {
    NEWEST,
    TITLE,
    AUTHOR,
    PUBLISHED_YEAR,
}

data class RemoteBookPage(
    val books: List<RemoteBook>,
    val page: Int,
    val hasNext: Boolean,
    val isOffline: Boolean = false,
)

data class UploadBook(
    val filePath: String,
    val title: String,
    val author: String,
    val publishedYear: Int,
)

data class BookChanges(
    val title: String,
    val author: String,
    val publishedYear: Int,
)

interface RemoteBookRepository {
    suspend fun getBooks(page: Int, query: String, sort: BookSort = BookSort.NEWEST): AppResult<RemoteBookPage>
    suspend fun getBook(id: Long): AppResult<RemoteBook>
    suspend fun updateBook(id: Long, changes: BookChanges): AppResult<RemoteBook>
    suspend fun deleteBook(id: Long): AppResult<Unit>

    /** [page] counts from 1. Reaching the last page marks the book as finished on the server. */
    suspend fun updateProgress(id: Long, page: Int): AppResult<Unit>

    suspend fun setFavorite(id: Long, isFavorite: Boolean): AppResult<RemoteBook>

    /** [onProgress] receives the sent fraction, from 0 to 1. */
    suspend fun uploadBook(book: UploadBook, onProgress: (Float) -> Unit): AppResult<RemoteBook>
}
