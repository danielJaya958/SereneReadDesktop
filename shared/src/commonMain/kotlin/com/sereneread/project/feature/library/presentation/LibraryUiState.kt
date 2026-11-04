package com.sereneread.project.feature.library.presentation

import com.sereneread.project.feature.library.domain.Book
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val query: String = "",
    val sort: BookSort = BookSort.NEWEST,
    val remoteBooks: List<RemoteBook> = emptyList(),
    val remotePage: Int = 0,
    val hasMoreRemote: Boolean = false,
    val isLoadingRemote: Boolean = true,
    val remoteError: String? = null,
    val isOffline: Boolean = false,
    val uploadForm: UploadForm? = null,
)

data class UploadForm(
    val filePath: String,
    val title: String,
    val author: String,
    val year: String = "",
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !isUploading && title.isNotBlank() && author.isNotBlank() && year.toIntOrNull() != null
}
