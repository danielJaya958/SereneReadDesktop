package com.sereneread.project.feature.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.feature.library.domain.BookRepository
import com.sereneread.project.feature.reader.domain.BookContentReader
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(FlowPreview::class, ExperimentalTime::class)
class ReaderViewModel(
    private val bookId: Long,
    private val repository: BookRepository,
    private val contentReader: BookContentReader,
    private val pageRenderer: PageRenderer,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val progressUpdates = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    private var filePath: String? = null

    init {
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                _state.update { it.copy(error = "Buku tidak ditemukan.") }
                return@launch
            }
            filePath = book.filePath
            repository.markOpened(bookId, Clock.System.now().toEpochMilliseconds())
            _state.update { it.copy(title = book.title, initialProgress = book.progress) }

            val content = runCatching { contentReader.read(book) }
            _state.update {
                content.fold(
                    onSuccess = { c -> it.copy(content = c) },
                    onFailure = { e -> it.copy(error = "Gagal membuka file: ${e.message}") },
                )
            }
        }
        viewModelScope.launch {
            progressUpdates.debounce(500).collect { repository.updateProgress(bookId, it) }
        }
    }

    fun onProgressChanged(progress: Float) {
        progressUpdates.tryEmit(progress.coerceIn(0f, 1f))
    }

    suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap =
        pageRenderer.render(checkNotNull(filePath) { "Book not loaded" }, pageIndex, widthPx)

    override fun onCleared() {
        filePath?.let(pageRenderer::release)
    }
}
