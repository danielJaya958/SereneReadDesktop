package com.sereneread.project.feature.reader

import androidx.compose.ui.graphics.ImageBitmap
import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.feature.library.FakeRemoteBookFiles
import com.sereneread.project.feature.library.domain.Book
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.library.domain.RemoteBookPage
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.library.domain.UploadBook
import com.sereneread.project.feature.reader.domain.BookContent
import com.sereneread.project.feature.reader.domain.BookContentReader
import com.sereneread.project.feature.reader.domain.Bookmark
import com.sereneread.project.feature.reader.domain.BookmarkRepository
import com.sereneread.project.feature.reader.domain.PageSize
import com.sereneread.project.feature.reader.presentation.BookmarkForm
import com.sereneread.project.feature.reader.presentation.PageRenderer
import com.sereneread.project.feature.reader.presentation.RemoteReaderViewModel
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteReaderViewModelTest {

    private class FakeRepository(var result: AppResult<RemoteBook>) : RemoteBookRepository {
        val savedPages = mutableListOf<Int>()
        var saveResult: AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun getBooks(page: Int, query: String, sort: BookSort) =
            AppResult.Success(RemoteBookPage(emptyList(), page, false))

        override suspend fun getBook(id: Long) = result
        override suspend fun updateBook(id: Long, changes: BookChanges) = error("not used")
        override suspend fun deleteBook(id: Long) = error("not used")
        override suspend fun setFavorite(id: Long, isFavorite: Boolean) = error("not used")

        override suspend fun updateProgress(id: Long, page: Int): AppResult<Unit> {
            savedPages += page
            return saveResult
        }

        override suspend fun uploadBook(book: UploadBook, onProgress: (Float) -> Unit) = error("not used")
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val content = BookContent.Pages(List(101) { PageSize(100f, 150f) }, emptyList())
    private var openedBook: Book? = null
    private val contentReader = BookContentReader { book ->
        openedBook = book
        content
    }
    private val pageRenderer = object : PageRenderer {
        override suspend fun render(filePath: String, pageIndex: Int, widthPx: Int): ImageBitmap = error("not used")
        override fun release(filePath: String) = Unit
    }
    private val files = FakeRemoteBookFiles()
    private val serverBook = RemoteBook(9, "T", "A", 2024, 51, 101, false)
    private val repository = FakeRepository(AppResult.Success(serverBook))
    private val bookmarks = FakeBookmarks()
    private val stats = FakeStats()
    private val clock = FakeClock()

    private fun viewModel(book: AppResult<RemoteBook> = AppResult.Success(serverBook)): RemoteReaderViewModel {
        repository.result = book
        return RemoteReaderViewModel(9, repository, bookmarks, files, contentReader, pageRenderer, stats, clock, TimeZone.UTC)
    }

    private class FakeClock : Clock {
        var current = Instant.parse("2026-11-02T10:00:00Z")
        override fun now() = current
    }

    private class FakeStats : StatsRepository {
        val sessions = mutableListOf<ReadingSession>()

        override suspend fun saveSession(session: ReadingSession): AppResult<Unit> {
            sessions += session
            return AppResult.Success(Unit)
        }

        override suspend fun getSummary(today: LocalDate) = error("not used")
        override suspend fun getDaily(from: LocalDate, to: LocalDate) = error("not used")
        override suspend fun getTopBooks() = error("not used")
    }

    private class FakeBookmarks : BookmarkRepository {
        var listResult: AppResult<List<Bookmark>> = AppResult.Success(emptyList())
        var saveFailure: AppResult.Failure? = null
        var deleteFailure: AppResult.Failure? = null
        val savedNotes = mutableListOf<String?>()
        val deletedIds = mutableListOf<Long>()

        override suspend fun getBookmarks(bookId: Long) = listResult

        override suspend fun saveBookmark(bookId: Long, page: Int, note: String?): AppResult<Bookmark> {
            savedNotes += note
            return saveFailure ?: AppResult.Success(Bookmark(page + 1000L, page, note))
        }

        override suspend fun deleteBookmark(bookId: Long, bookmarkId: Long): AppResult<Unit> {
            deletedIds += bookmarkId
            return deleteFailure ?: AppResult.Success(Unit)
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun opensDownloadedFileAtServerPosition() {
        val state = viewModel().state.value

        assertEquals("T", state.title)
        assertEquals(content, state.content)
        assertEquals(0.5f, state.initialProgress)
        assertNull(state.downloadProgress)
        assertEquals("/cache/9.pdf", openedBook?.filePath)
    }

    @Test
    fun firstPageStartsAtTop() {
        val state = viewModel(AppResult.Success(serverBook.copy(lastReadPage = 1))).state.value

        assertEquals(0f, state.initialProgress)
    }

    @Test
    fun restoresPositionBeforeServerKnowsPageCount() {
        val state = viewModel(AppResult.Success(serverBook.copy(totalPages = 0))).state.value

        assertEquals(0.5f, state.initialProgress)
    }

    @Test
    fun unknownBookShowsServerMessage() {
        val state = viewModel(AppResult.Failure(AppError.NotFound("Buku tidak ditemukan"))).state.value

        assertEquals("Buku tidak ditemukan", state.error)
    }

    @Test
    fun failedDownloadShowsMessageAndHidesProgress() {
        files.result = AppResult.Failure(AppError.Server(500, "gagal"))

        val state = viewModel().state.value

        assertEquals("gagal", state.error)
        assertNull(state.downloadProgress)
    }

    @Test
    fun restoredPositionIsNotSentBack() {
        val viewModel = viewModel()

        viewModel.onProgressChanged(0.5f)
        dispatcher.scheduler.advanceTimeBy(2_000)

        assertTrue(repository.savedPages.isEmpty())
    }

    @Test
    fun sendsOnlyLastPageAfterScrollingStops() {
        val viewModel = viewModel()

        viewModel.onProgressChanged(0.6f)
        dispatcher.scheduler.advanceTimeBy(500)
        viewModel.onProgressChanged(0.7f)
        dispatcher.scheduler.advanceTimeBy(1_001)

        assertEquals(listOf(71), repository.savedPages)
    }

    @Test
    fun endOfBookSendsLastPage() {
        val viewModel = viewModel()

        viewModel.onProgressChanged(1f)
        dispatcher.scheduler.advanceTimeBy(1_001)

        assertEquals(listOf(101), repository.savedPages)
    }

    @Test
    fun exitSendsPendingPageOnce() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onProgressChanged(0.6f)
        viewModel.saveOnExit()
        dispatcher.scheduler.advanceTimeBy(2_000)

        assertEquals(listOf(61), repository.savedPages)
    }

    @Test
    fun exitSendsReadingSessionOnce() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onProgressChanged(0.5f)
        clock.current += 30.seconds
        viewModel.onProgressChanged(0.51f)
        clock.current += 30.seconds
        viewModel.saveOnExit()
        viewModel.saveOnExit()

        val session = stats.sessions.single()
        assertEquals(51, session.startPage)
        assertEquals(52, session.endPage)
        assertEquals(1, session.pagesRead)
        assertEquals(60, session.durationSeconds)
        assertEquals(9, session.bookId)
    }

    @Test
    fun loadsBookmarksAfterOpening() {
        val saved = listOf(Bookmark(1, 3, "catatan"))
        bookmarks.listResult = AppResult.Success(saved)

        assertEquals(saved, viewModel().state.value.bookmarks)
    }

    @Test
    fun failedBookmarkListKeepsBookReadable() {
        bookmarks.listResult = AppResult.Failure(AppError.Server(500, "gagal"))

        val state = viewModel().state.value

        assertEquals(content, state.content)
        assertNull(state.error)
        assertEquals("gagal", state.bookmarkError)
    }

    @Test
    fun formStartsFromCurrentPage() {
        val viewModel = viewModel()
        viewModel.onProgressChanged(0.1f)

        viewModel.onBookmarkFormOpen()

        assertEquals(BookmarkForm(11), viewModel.state.value.bookmarkForm)
    }

    @Test
    fun formEditsExistingBookmarkOfCurrentPage() {
        bookmarks.listResult = AppResult.Success(listOf(Bookmark(7, 51, "lama")))
        val viewModel = viewModel()

        viewModel.onBookmarkFormOpen()

        assertEquals(BookmarkForm(51, "lama", 7), viewModel.state.value.bookmarkForm)
    }

    @Test
    fun savedBookmarkJoinsListInPageOrder() {
        bookmarks.listResult = AppResult.Success(listOf(Bookmark(7, 90, null)))
        val viewModel = viewModel()
        viewModel.onBookmarkFormOpen()
        viewModel.onBookmarkNoteChange("  ingat  ")

        viewModel.onBookmarkSave()

        assertEquals(listOf(51, 90), viewModel.state.value.bookmarks.map { it.page })
        assertEquals(listOf<String?>("ingat"), bookmarks.savedNotes)
        assertNull(viewModel.state.value.bookmarkForm)
    }

    @Test
    fun blankNoteIsSavedAsNoNote() {
        bookmarks.listResult = AppResult.Success(listOf(Bookmark(7, 51, "lama")))
        val viewModel = viewModel()
        viewModel.onBookmarkFormOpen()
        viewModel.onBookmarkNoteChange("   ")

        viewModel.onBookmarkSave()

        assertEquals(listOf<String?>(null), bookmarks.savedNotes)
        assertEquals(listOf(Bookmark(1051, 51, null)), viewModel.state.value.bookmarks)
    }

    @Test
    fun failedSaveKeepsFormWithMessage() {
        bookmarks.saveFailure = AppResult.Failure(AppError.Validation("Catatan maksimal 500 karakter"))
        val viewModel = viewModel()
        viewModel.onBookmarkFormOpen()
        viewModel.onBookmarkNoteChange("panjang")

        viewModel.onBookmarkSave()

        assertEquals(
            BookmarkForm(51, "panjang", error = "Catatan maksimal 500 karakter"),
            viewModel.state.value.bookmarkForm,
        )
    }

    @Test
    fun deleteRemovesBookmark() {
        bookmarks.listResult = AppResult.Success(listOf(Bookmark(7, 51, null), Bookmark(8, 90, null)))
        val viewModel = viewModel()
        viewModel.onBookmarkFormOpen()

        viewModel.onBookmarkDelete()

        assertEquals(listOf(7L), bookmarks.deletedIds)
        assertEquals(listOf(90), viewModel.state.value.bookmarks.map { it.page })
    }

    @Test
    fun failedDeleteKeepsBookmarkAndShowsMessage() {
        val saved = listOf(Bookmark(7, 51, null))
        bookmarks.listResult = AppResult.Success(saved)
        bookmarks.deleteFailure = AppResult.Failure(AppError.Server(500, "gagal"))
        val viewModel = viewModel()
        viewModel.onBookmarkFormOpen()

        viewModel.onBookmarkDelete()

        assertEquals(saved, viewModel.state.value.bookmarks)
        assertEquals("gagal", viewModel.state.value.bookmarkForm?.error)
    }

    @Test
    fun failedSaveIsRetriedOnExit() = runTest(dispatcher) {
        val viewModel = viewModel()
        repository.saveResult = AppResult.Failure(AppError.Server(500, "gagal"))

        viewModel.onProgressChanged(0.6f)
        dispatcher.scheduler.advanceTimeBy(1_001)
        repository.saveResult = AppResult.Success(Unit)
        viewModel.saveOnExit()

        assertEquals(listOf(61, 61), repository.savedPages)
    }
}
