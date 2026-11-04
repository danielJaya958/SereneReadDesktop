package com.sereneread.project.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.ui.toUserMessage
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.RemoteBookFiles
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val bookId: Long,
    private val repository: RemoteBookRepository,
    private val files: RemoteBookFiles,
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailUiState())
    val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

    /** Runs on first display and on return from the reader, where the page may have changed. */
    fun load() {
        _state.update { it.copy(isLoading = it.book == null, error = null) }
        viewModelScope.launch {
            when (val result = repository.getBook(bookId)) {
                is AppResult.Failure -> _state.update {
                    it.copy(isLoading = false, error = result.error.toUserMessage())
                }
                is AppResult.Success -> _state.update { it.copy(isLoading = false, book = result.data) }
            }
        }
    }

    fun onEditClick() {
        val book = _state.value.book ?: return
        _state.update { it.copy(editForm = EditForm(book.title, book.author, book.publishedYear.toString())) }
    }

    fun onEditChange(form: EditForm) {
        _state.update { it.copy(editForm = form.copy(error = null)) }
    }

    fun onEditDismiss() {
        _state.update { it.copy(editForm = null) }
    }

    fun onEditSubmit() {
        val form = _state.value.editForm?.takeIf { it.canSubmit } ?: return
        val year = form.year.toIntOrNull() ?: return
        _state.update { it.copy(editForm = form.copy(isSaving = true, error = null)) }

        viewModelScope.launch {
            val changes = BookChanges(form.title.trim(), form.author.trim(), year)
            when (val result = repository.updateBook(bookId, changes)) {
                is AppResult.Failure -> _state.update {
                    it.copy(editForm = form.copy(isSaving = false, error = result.error.toUserMessage()))
                }
                is AppResult.Success -> _state.update { it.copy(book = result.data, editForm = null) }
            }
        }
    }

    fun onFavoriteToggle() {
        val current = _state.value
        val book = current.book?.takeUnless { current.isSavingFavorite } ?: return
        _state.update { it.copy(isSavingFavorite = true, error = null) }

        viewModelScope.launch {
            when (val result = repository.setFavorite(bookId, !book.isFavorite)) {
                is AppResult.Failure -> _state.update {
                    it.copy(isSavingFavorite = false, error = result.error.toUserMessage())
                }
                is AppResult.Success -> _state.update { it.copy(isSavingFavorite = false, book = result.data) }
            }
        }
    }

    fun onDeleteClick() {
        _state.update { it.copy(isConfirmingDelete = true) }
    }

    fun onDeleteDismiss() {
        _state.update { it.copy(isConfirmingDelete = false) }
    }

    fun onDeleteConfirm() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isConfirmingDelete = false, isDeleting = true, error = null) }

        viewModelScope.launch {
            when (val result = repository.deleteBook(bookId)) {
                is AppResult.Failure -> _state.update {
                    it.copy(isDeleting = false, error = result.error.toUserMessage())
                }
                is AppResult.Success -> {
                    files.delete(bookId)
                    _state.update { it.copy(isDeleting = false, isDeleted = true) }
                }
            }
        }
    }
}
