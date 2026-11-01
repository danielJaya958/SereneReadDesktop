package com.sereneread.project.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.core.platform.FilePicker
import com.sereneread.project.feature.library.domain.BookFormat
import com.sereneread.project.feature.library.domain.BookRepository
import com.sereneread.project.feature.library.domain.ImportBookUseCase
import com.sereneread.project.feature.library.domain.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: BookRepository,
    private val importBook: ImportBookUseCase,
    private val filePicker: FilePicker,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeBooks().collect { books ->
                _state.update { it.copy(books = books, isLoading = false) }
            }
        }
    }

    fun onImportClick() {
        viewModelScope.launch {
            val path = filePicker.pickFile(BookFormat.extensions) ?: return@launch
            val message = when (importBook(path)) {
                is ImportResult.Imported -> null
                is ImportResult.AlreadyExists -> "Buku sudah ada di library."
                ImportResult.UnsupportedFormat -> "Format tidak didukung. Pilih file EPUB, PDF, atau TXT."
            }
            _state.update { it.copy(message = message) }
        }
    }

    fun onDeleteBook(id: Long) {
        viewModelScope.launch { repository.deleteBook(id) }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }
}
