package com.sereneread.project.feature.library.presentation

import com.sereneread.project.feature.library.domain.Book

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
)
