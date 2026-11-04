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
)

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
