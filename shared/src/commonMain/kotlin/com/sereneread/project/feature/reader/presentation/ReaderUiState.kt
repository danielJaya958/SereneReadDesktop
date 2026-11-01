package com.sereneread.project.feature.reader.presentation

import com.sereneread.project.feature.reader.domain.BookContent

data class ReaderUiState(
    val title: String = "",
    val content: BookContent? = null,
    val initialProgress: Float = 0f,
    val error: String? = null,
)
