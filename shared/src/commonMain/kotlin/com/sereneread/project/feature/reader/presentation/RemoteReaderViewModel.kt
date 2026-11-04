package com.sereneread.project.feature.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.ui.toUserMessage
import com.sereneread.project.feature.library.domain.Book
import com.sereneread.project.feature.library.domain.BookFormat
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.library.domain.RemoteBookFiles
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.reader.domain.BookContent
import com.sereneread.project.feature.reader.domain.BookContentReader
import com.sereneread.project.feature.reader.domain.BookmarkRepository
import com.sereneread.project.feature.stats.domain.ReadingSessionTracker
import com.sereneread.project.feature.stats.domain.StatsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.math.roundToInt
import kotlin.time.Clock

/** Opens a server book and keeps the server position up to date while it is read. */
@OptIn(FlowPreview::class)
class RemoteReaderViewModel(
    private val bookId: Long,
    private val repository: RemoteBookRepository,
    private val bookmarks: BookmarkRepository,
    private val files: RemoteBookFiles,
    private val contentReader: BookContentReader,
    private val pageRenderer: PageRenderer,
    private val stats: StatsRepository,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val pageChanges = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val session = ReadingSessionTracker(bookId, clock, timeZone)
    private var filePath: String? = null
    private var pageCount = 0
    private var savedPage = 0

    init {
        viewModelScope.launch {
            val book = when (val result = repository.getBook(bookId)) {
                is AppResult.Failure -> return@launch showError(result.error.toUserMessage())
                is AppResult.Success -> result.data
            }
            savedPage = book.lastReadPage
            _state.update {
                it.copy(title = book.title, currentPage = book.lastReadPage, downloadProgress = 0f)
            }

            val download = files.getOrDownload(bookId) { sent -> _state.update { it.copy(downloadProgress = sent) } }
            _state.update { it.copy(downloadProgress = null) }
            val path = when (download) {
                is AppResult.Failure -> return@launch showError(download.error.toUserMessage())
                is AppResult.Success -> download.data
            }

            filePath = path
            val content = runCatching { contentReader.read(book.toLocalBook(path)) }
            _state.update {
                content.fold(
                    onSuccess = { c ->
                        pageCount = (c as? BookContent.Pages)?.pages?.size ?: 0
                        it.copy(content = c, initialProgress = book.readFraction())
                    },
                    onFailure = { e -> it.copy(error = "Gagal membuka file: ${e.message}") },
                )
            }
            if (content.isSuccess) loadBookmarks()
        }
        viewModelScope.launch {
            pageChanges.debounce(SAVE_DELAY_MS).collect { save(it) }
        }
    }

    /** The reader reports a fraction of the last page index, while the server counts pages from 1. */
    fun onProgressChanged(progress: Float) {
        if (pageCount == 0) return
        val page = (progress.coerceIn(0f, 1f) * (pageCount - 1)).roundToInt() + 1
        session.onPage(page)
        if (page == _state.value.currentPage) return
        _state.update { it.copy(currentPage = page) }
        pageChanges.tryEmit(page)
    }

    fun onBookmarkFormOpen() {
        val current = _state.value
        val page = current.currentPage.coerceAtLeast(1)
        val existing = current.bookmarks.find { it.page == page }
        _state.update {
            it.copy(bookmarkForm = BookmarkForm(page, existing?.note.orEmpty(), existing?.id))
        }
    }

    fun onBookmarkNoteChange(note: String) {
        _state.update { it.copy(bookmarkForm = it.bookmarkForm?.copy(note = note, error = null)) }
    }

    fun onBookmarkFormDismiss() {
        _state.update { it.copy(bookmarkForm = null) }
    }

    fun onBookmarkSave() {
        val form = startBookmarkRequest() ?: return
        viewModelScope.launch {
            val note = form.note.trim().ifEmpty { null }
            when (val result = bookmarks.saveBookmark(bookId, form.page, note)) {
                is AppResult.Failure -> showBookmarkFormError(form, result)
                is AppResult.Success -> _state.update { current ->
                    val others = current.bookmarks.filterNot { it.page == result.data.page }
                    current.copy(bookmarks = (others + result.data).sortedBy { it.page }, bookmarkForm = null)
                }
            }
        }
    }

    fun onBookmarkDelete() {
        val form = startBookmarkRequest() ?: return
        val bookmarkId = form.bookmarkId ?: return
        viewModelScope.launch {
            when (val result = bookmarks.deleteBookmark(bookId, bookmarkId)) {
                is AppResult.Failure -> showBookmarkFormError(form, result)
                is AppResult.Success -> _state.update { current ->
                    current.copy(bookmarks = current.bookmarks.filterNot { it.id == bookmarkId }, bookmarkForm = null)
                }
            }
        }
    }

    /** Sends what is still unsaved before leaving, so the previous screen reloads the new page. */
    suspend fun saveOnExit() {
        withTimeoutOrNull(EXIT_SAVE_TIMEOUT_MS) {
            save(_state.value.currentPage)
            session.finish()?.let { stats.saveSession(it) }
        }
    }

    suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap =
        pageRenderer.render(checkNotNull(filePath) { "Book not loaded" }, pageIndex, widthPx)

    override fun onCleared() {
        filePath?.let(pageRenderer::release)
    }

    /** A failed save stays unsaved, so the next page change or exit sends the position again. */
    private suspend fun save(page: Int) {
        if (page == savedPage) return
        if (repository.updateProgress(bookId, page) is AppResult.Success) savedPage = page
    }

    /** A failed list leaves the book readable, so the message stays inside the bookmark menu. */
    private suspend fun loadBookmarks() {
        when (val result = bookmarks.getBookmarks(bookId)) {
            is AppResult.Failure -> _state.update { it.copy(bookmarkError = result.error.toUserMessage()) }
            is AppResult.Success -> _state.update { it.copy(bookmarks = result.data, bookmarkError = null) }
        }
    }

    /** Returns null while another request runs, so a double click sends one request. */
    private fun startBookmarkRequest(): BookmarkForm? {
        val form = _state.value.bookmarkForm?.takeUnless { it.isSaving } ?: return null
        _state.update { it.copy(bookmarkForm = form.copy(isSaving = true, error = null)) }
        return form
    }

    private fun showBookmarkFormError(form: BookmarkForm, failure: AppResult.Failure) {
        _state.update {
            it.copy(bookmarkForm = form.copy(isSaving = false, error = failure.error.toUserMessage()))
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    /** Uses the file's page count, because [RemoteBook.totalPages] is 0 until the server processes the PDF. */
    private fun RemoteBook.readFraction(): Float =
        if (pageCount > 1 && lastReadPage > 1) (lastReadPage - 1).toFloat() / (pageCount - 1) else 0f

    private fun RemoteBook.toLocalBook(path: String) = Book(
        id = id,
        title = title,
        author = author,
        filePath = path,
        format = BookFormat.PDF,
        addedAt = 0,
        lastOpenedAt = null,
        progress = 0f,
    )

    private companion object {
        const val SAVE_DELAY_MS = 1_000L
        const val EXIT_SAVE_TIMEOUT_MS = 1_500L
    }
}
