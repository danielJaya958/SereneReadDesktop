package com.sereneread.project.feature.library.presentation

import com.sereneread.project.feature.library.domain.RemoteBook

data class BookDetailUiState(
    val book: RemoteBook? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val editForm: EditForm? = null,
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val isSavingFavorite: Boolean = false,
    val download: DownloadState = DownloadState.Checking,
)

/** Whether this computer holds the PDF, so the book can be read offline. */
sealed interface DownloadState {
    data object Checking : DownloadState
    data object Missing : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Saved(val path: String) : DownloadState
}

data class EditForm(
    val title: String,
    val author: String,
    val year: String,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSaving && title.isNotBlank() && author.isNotBlank() && year.toIntOrNull() != null
}
