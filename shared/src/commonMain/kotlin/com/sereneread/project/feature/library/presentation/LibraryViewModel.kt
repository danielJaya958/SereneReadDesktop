package com.sereneread.project.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.platform.FilePicker
import com.sereneread.project.core.ui.toUserMessage
import com.sereneread.project.feature.library.domain.BookFormat
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.BookMetadataReader
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.library.domain.UploadBook
import com.sereneread.project.feature.offline.data.OfflineSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MILLIS = 300L

class LibraryViewModel(
    private val remoteRepository: RemoteBookRepository,
    private val metadataReader: BookMetadataReader,
    private val filePicker: FilePicker,
    private val sync: OfflineSync,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var remoteJob: Job? = null
    private var uploadJob: Job? = null

    init {
        viewModelScope.launch {
            sync.pendingCount.collect { count -> _state.update { it.copy(pendingSync = count) } }
        }
    }

    /** Runs on first display and on return from detail, where a book may have changed or been downloaded. */
    fun onScreenShown() {
        loadRemotePage(1)
        loadDownloaded()
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        loadRemotePage(1, debounce = true)
    }

    fun onFilterChange(filter: ShelfFilter) {
        _state.update { it.copy(filter = filter) }
        if (filter == ShelfFilter.DOWNLOADED) loadDownloaded()
    }

    fun onActiveChange(key: String) {
        _state.update { it.copy(activeKey = key) }
    }

    fun onSortChange(sort: BookSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        loadRemotePage(1)
    }

    fun onLoadMore() = loadRemotePage(_state.value.remotePage + 1)

    fun onRetryRemote() = loadRemotePage(1)

    fun onUploadClick() {
        viewModelScope.launch {
            val path = filePicker.pickFile(setOf(BookFormat.PDF.extension)) ?: return@launch
            val metadata = metadataReader.read(path, BookFormat.PDF)
            _state.update {
                it.copy(uploadForm = UploadForm(path, metadata?.title.orEmpty(), metadata?.author.orEmpty()))
            }
        }
    }

    fun onUploadFormChange(form: UploadForm) {
        _state.update { it.copy(uploadForm = form.copy(error = null)) }
    }

    fun onUploadDismiss() {
        uploadJob?.cancel()
        _state.update { it.copy(uploadForm = null) }
    }

    fun onUploadSubmit() {
        val form = _state.value.uploadForm?.takeIf { it.canSubmit } ?: return
        val year = form.year.toIntOrNull() ?: return
        updateForm { it.copy(isUploading = true, progress = 0f, error = null) }

        uploadJob = viewModelScope.launch {
            val book = UploadBook(form.filePath, form.title.trim(), form.author.trim(), year)
            val result = remoteRepository.uploadBook(book) { sent -> updateForm { it.copy(progress = sent) } }
            when (result) {
                is AppResult.Failure -> updateForm {
                    it.copy(isUploading = false, error = result.error.toUserMessage())
                }
                is AppResult.Success -> {
                    _state.update { it.copy(uploadForm = null, message = "Buku berhasil diupload.") }
                    loadRemotePage(1)
                }
            }
        }
    }

    private fun loadDownloaded() {
        viewModelScope.launch {
            val books = remoteRepository.downloadedBooks()
            _state.update { it.copy(downloaded = books) }
        }
    }

    private fun updateForm(transform: (UploadForm) -> UploadForm) {
        _state.update { state -> state.copy(uploadForm = state.uploadForm?.let(transform)) }
    }

    /** Cancels the previous load, so a slow old search cannot overwrite a newer result. */
    private fun loadRemotePage(page: Int, debounce: Boolean = false) {
        remoteJob?.cancel()
        _state.update { it.copy(isLoadingRemote = true, remoteError = null) }
        remoteJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            val current = _state.value
            when (val result = remoteRepository.getBooks(page, current.query, current.sort)) {
                is AppResult.Failure -> _state.update {
                    it.copy(isLoadingRemote = false, remoteError = result.error.toUserMessage())
                }
                is AppResult.Success -> _state.update {
                    val previous = if (page == 1) emptyList() else it.remoteBooks
                    it.copy(
                        remoteBooks = previous + result.data.books,
                        remotePage = result.data.page,
                        hasMoreRemote = result.data.hasNext,
                        isOffline = result.data.isOffline,
                        isLoadingRemote = false,
                    )
                }
            }
        }
    }
}
