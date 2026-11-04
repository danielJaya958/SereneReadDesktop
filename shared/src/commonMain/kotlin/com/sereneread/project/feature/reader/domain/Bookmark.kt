package com.sereneread.project.feature.reader.domain

import com.sereneread.project.core.network.AppResult

/** [page] counts from 1. A book has at most one bookmark per page. */
data class Bookmark(
    val id: Long,
    val page: Int,
    val note: String?,
)

interface BookmarkRepository {
    suspend fun getBookmarks(bookId: Long): AppResult<List<Bookmark>>

    /** Replaces the note when [page] already has a bookmark, and a null [note] removes it. */
    suspend fun saveBookmark(bookId: Long, page: Int, note: String?): AppResult<Bookmark>

    suspend fun deleteBookmark(bookId: Long, bookmarkId: Long): AppResult<Unit>
}
