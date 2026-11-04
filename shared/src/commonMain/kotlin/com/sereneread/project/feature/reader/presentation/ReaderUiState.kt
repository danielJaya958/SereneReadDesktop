package com.sereneread.project.feature.reader.presentation

import com.sereneread.project.feature.reader.domain.BookContent
import com.sereneread.project.feature.reader.domain.Bookmark

data class ReaderUiState(
    val title: String = "",
    val content: BookContent? = null,
    val initialProgress: Float = 0f,
    val downloadProgress: Float? = null,
    val error: String? = null,
    val currentPage: Int = 0,
    val bookmarks: List<Bookmark> = emptyList(),
    val bookmarkError: String? = null,
    val bookmarkForm: BookmarkForm? = null,
)

/** [bookmarkId] is set when [page] already has a bookmark, which makes the form an edit. */
data class BookmarkForm(
    val page: Int,
    val note: String = "",
    val bookmarkId: Long? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)
