package com.sereneread.project.feature.library

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.library.domain.RemoteBookPage
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.library.domain.UploadBook
import com.sereneread.project.feature.library.presentation.BookDetailViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {

    private val book = RemoteBook(9, "T", "A", 2024, 5, 94, false)

    private inner class FakeRepository : RemoteBookRepository {
        var getResult: AppResult<RemoteBook> = AppResult.Success(book)
        var updateResult: AppResult<RemoteBook>? = null
        var deleteResult: AppResult<Unit> = AppResult.Success(Unit)
        var deleteCalls = 0
        var lastChanges: BookChanges? = null
        var favoriteFailure: AppResult.Failure? = null
        val favoriteCalls = mutableListOf<Boolean>()

        override suspend fun getBooks(page: Int, query: String, sort: BookSort) =
            AppResult.Success(RemoteBookPage(emptyList(), page, false))

        override suspend fun getBook(id: Long) = getResult

        override suspend fun updateBook(id: Long, changes: BookChanges): AppResult<RemoteBook> {
            lastChanges = changes
            return updateResult ?: AppResult.Success(
                book.copy(title = changes.title, author = changes.author, publishedYear = changes.publishedYear),
            )
        }

        override suspend fun deleteBook(id: Long): AppResult<Unit> {
            deleteCalls++
            return deleteResult
        }

        override suspend fun updateProgress(id: Long, page: Int) = error("not used")

        override suspend fun setFavorite(id: Long, isFavorite: Boolean): AppResult<RemoteBook> {
            favoriteCalls += isFavorite
            return favoriteFailure ?: AppResult.Success(book.copy(isFavorite = isFavorite))
        }

        override suspend fun uploadBook(book: UploadBook, onProgress: (Float) -> Unit) = error("not used")
    }

    private val repository = FakeRepository()
    private val files = FakeRemoteBookFiles()

    private fun viewModel() = BookDetailViewModel(9, repository, files).apply { load() }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsBook() {
        val viewModel = viewModel()

        assertEquals(book, viewModel.state.value.book)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun reloadUpdatesPageWithoutHidingBook() {
        val viewModel = viewModel()
        val moved = book.copy(lastReadPage = 40)
        repository.getResult = AppResult.Success(moved)
        val loadingStates = mutableListOf<Boolean>()
        val observer = CoroutineScope(Dispatchers.Main).launch {
            viewModel.state.collect { loadingStates += it.isLoading }
        }

        viewModel.load()
        observer.cancel()

        assertEquals(moved, viewModel.state.value.book)
        assertFalse(true in loadingStates)
    }

    @Test
    fun loadFailureShowsMessage() {
        repository.getResult = AppResult.Failure(AppError.NotFound("Buku tidak ditemukan"))

        val viewModel = viewModel()

        assertEquals("Buku tidak ditemukan", viewModel.state.value.error)
    }

    @Test
    fun editStartsFromCurrentBookAndUpdatesIt() {
        val viewModel = viewModel()
        viewModel.onEditClick()
        val form = viewModel.state.value.editForm!!
        assertEquals("2024", form.year)

        viewModel.onEditChange(form.copy(title = " New "))
        viewModel.onEditSubmit()

        assertEquals(BookChanges("New", "A", 2024), repository.lastChanges)
        assertEquals("New", viewModel.state.value.book?.title)
        assertNull(viewModel.state.value.editForm)
    }

    @Test
    fun failedEditKeepsFormWithMessage() {
        repository.updateResult = AppResult.Failure(AppError.Validation("Tahun terbit salah"))
        val viewModel = viewModel()
        viewModel.onEditClick()

        viewModel.onEditSubmit()

        assertEquals("Tahun terbit salah", viewModel.state.value.editForm?.error)
        assertEquals(book, viewModel.state.value.book)
    }

    @Test
    fun favoriteToggleSendsOppositeValue() {
        val viewModel = viewModel()

        viewModel.onFavoriteToggle()
        viewModel.onFavoriteToggle()

        assertEquals(listOf(true, false), repository.favoriteCalls)
        assertFalse(viewModel.state.value.book!!.isFavorite)
        assertFalse(viewModel.state.value.isSavingFavorite)
    }

    @Test
    fun failedFavoriteKeepsBookWithMessage() {
        repository.favoriteFailure = AppResult.Failure(AppError.Server(500, "gagal"))
        val viewModel = viewModel()

        viewModel.onFavoriteToggle()

        assertEquals(book, viewModel.state.value.book)
        assertEquals("gagal", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSavingFavorite)
    }

    @Test
    fun deleteRunsOnlyAfterConfirmation() {
        val viewModel = viewModel()

        viewModel.onDeleteClick()
        assertEquals(0, repository.deleteCalls)

        viewModel.onDeleteConfirm()
        assertEquals(1, repository.deleteCalls)
        assertTrue(viewModel.state.value.isDeleted)
        assertEquals(listOf(9L), files.deleted)
    }

    @Test
    fun failedDeleteStaysOnScreen() {
        repository.deleteResult = AppResult.Failure(AppError.Forbidden("Bukan pemilik buku"))
        val viewModel = viewModel()

        viewModel.onDeleteClick()
        viewModel.onDeleteConfirm()

        assertFalse(viewModel.state.value.isDeleted)
        assertEquals("Bukan pemilik buku", viewModel.state.value.error)
    }
}
