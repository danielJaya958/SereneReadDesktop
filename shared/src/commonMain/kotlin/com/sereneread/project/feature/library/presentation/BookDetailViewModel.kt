package com.sereneread.project.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.platform.FileLocations
import com.sereneread.project.core.ui.toUserMessage
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.RemoteBookFiles
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val bookId: Long,
    private val repository: RemoteBookRepository,
    private val files: RemoteBookFiles,
    private val locations: FileLocations,
) : ViewModel() {

    // Starts from the copy the shelf already showed, so the cover is there for the opening transition.
    private val _state = MutableStateFlow(
        repository.peekBook(bookId).let { BookDetailUiState(book = it, isLoading = it == null) },
    )
    val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /** Runs on first display and on return from the reader, where the page may have changed. */
    fun load() {
        _state.update { it.copy(isLoading = it.book == null, error = null) }
        refreshDownload()
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

    fun onDownload() {
        if (_state.value.download !is DownloadState.Missing) return
        _state.update { it.copy(download = DownloadState.Downloading(0f), error = null) }
        downloadJob = viewModelScope.launch {
            val result = files.getOrDownload(bookId) { sent ->
                _state.update { it.copy(download = DownloadState.Downloading(sent)) }
            }
            _state.update {
                when (result) {
                    is AppResult.Success -> it.copy(download = DownloadState.Saved(result.data))
                    is AppResult.Failure -> it.copy(download = DownloadState.Missing, error = result.error.toUserMessage())
                }
            }
        }
    }

    fun onRemoveDownload() {
        downloadJob?.cancel()
        viewModelScope.launch {
            files.delete(bookId)
            _state.update { it.copy(download = DownloadState.Missing) }
        }
    }

    fun onOpenFolder() {
        val saved = _state.value.download as? DownloadState.Saved ?: return
        if (!locations.reveal(saved.path)) {
            _state.update { it.copy(error = "Folder unduhan tidak bisa dibuka.") }
        }
    }

    /** A download in progress keeps its own state; otherwise the disk is the source of truth. */
    private fun refreshDownload() {
        if (_state.value.download is DownloadState.Downloading) return
        viewModelScope.launch {
            val path = files.downloadedPath(bookId)
            _state.update { it.copy(download = if (path != null) DownloadState.Saved(path) else DownloadState.Missing) }
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
