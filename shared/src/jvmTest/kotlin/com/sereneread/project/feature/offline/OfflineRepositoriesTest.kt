package com.sereneread.project.feature.offline

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sereneread.project.core.database.SereneReadDatabase
import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.auth.domain.SignOutUseCase
import com.sereneread.project.feature.library.FakeRemoteBookFiles
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.library.domain.RemoteBookPage
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.library.domain.UploadBook
import com.sereneread.project.feature.offline.data.OfflineRemoteBookRepository
import com.sereneread.project.feature.offline.data.OfflineStatsRepository
import com.sereneread.project.feature.offline.data.OfflineStore
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsRepository
import com.sereneread.project.feature.stats.domain.StatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class OfflineRepositoriesTest {

    private val networkDown = AppResult.Failure(AppError.Network(IOException("down")))
    private val book = RemoteBook(9, "Berpikir", "Kahneman", 2011, 5, 654, false)
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { SereneReadDatabase.Schema.create(it) }
    private val store = OfflineStore(SereneReadDatabase(driver), Dispatchers.Unconfined)
    private val files = FakeRemoteBookFiles()

    private inner class FakeBooks : RemoteBookRepository {
        var failure: AppResult.Failure? = null
        var progressFailure: AppResult.Failure? = null
        var serverBook = book
        val sentPages = mutableListOf<Int>()

        override suspend fun getBooks(page: Int, query: String, sort: BookSort) =
            failure ?: AppResult.Success(RemoteBookPage(listOf(serverBook), page, false))

        override suspend fun getBook(id: Long) = failure ?: AppResult.Success(serverBook)

        override suspend fun updateBook(id: Long, changes: BookChanges) =
            failure ?: AppResult.Success(serverBook.copy(title = changes.title))

        override suspend fun deleteBook(id: Long) = failure ?: AppResult.Success(Unit)

        override suspend fun updateProgress(id: Long, page: Int): AppResult<Unit> {
            sentPages += page
            return progressFailure ?: AppResult.Success(Unit)
        }

        override suspend fun setFavorite(id: Long, isFavorite: Boolean) =
            failure ?: AppResult.Success(serverBook.copy(isFavorite = isFavorite))

        override suspend fun uploadBook(book: UploadBook, onProgress: (Float) -> Unit) = error("not used")
    }

    private inner class FakeStats : StatsRepository {
        var failure: AppResult.Failure? = null
        val sent = mutableListOf<String>()

        override suspend fun saveSession(session: ReadingSession): AppResult<Unit> {
            sent += session.clientSessionId
            return failure ?: AppResult.Success(Unit)
        }

        override suspend fun getSummary(today: LocalDate) =
            AppResult.Success(StatsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

        override suspend fun getDaily(from: LocalDate, to: LocalDate) = error("not used")
        override suspend fun getTopBooks() = error("not used")
    }

    private val remote = FakeBooks()
    private val repository = OfflineRemoteBookRepository(remote, store, files)
    private val remoteStats = FakeStats()
    private val stats = OfflineStatsRepository(remoteStats, store)

    private fun session(id: String) = ReadingSession(
        clientSessionId = id,
        bookId = 9,
        startedAt = Instant.parse("2026-11-02T01:00:00Z"),
        endedAt = Instant.parse("2026-11-02T01:20:00Z"),
        localDate = LocalDate(2026, 11, 2),
        startPage = 5,
        endPage = 12,
        pagesRead = 7,
        durationSeconds = 900,
    )

    private fun page(result: AppResult<RemoteBookPage>) = assertIs<AppResult.Success<RemoteBookPage>>(result).data

    @Test
    fun offlineListShowsDownloadedBooksOnly() = runTest {
        remote.getBooks(1, "")
        repository.getBooks(1, "")
        store.saveBooks(listOf(book.copy(id = 10, title = "Lain")))
        files.downloaded += 9
        remote.failure = networkDown

        val result = page(repository.getBooks(1, ""))

        assertEquals(listOf(book), result.books)
        assertTrue(result.isOffline)
        assertEquals(false, result.hasNext)
    }

    @Test
    fun offlineSearchMatchesTitleOrAuthor() = runTest {
        repository.getBooks(1, "")
        files.downloaded += 9
        remote.failure = networkDown

        assertEquals(1, page(repository.getBooks(1, "kahne")).books.size)
        assertEquals(0, page(repository.getBooks(1, "tidak ada")).books.size)
    }

    @Test
    fun offlineListFollowsChosenSort() = runTest {
        store.saveBooks(listOf(book, book.copy(id = 10, title = "Atomic", author = "Zed", publishedYear = 2018)))
        files.downloaded += setOf(9L, 10L)
        remote.failure = networkDown

        assertEquals(listOf(10L, 9L), page(repository.getBooks(1, "", BookSort.TITLE)).books.map { it.id })
        assertEquals(listOf(9L, 10L), page(repository.getBooks(1, "", BookSort.AUTHOR)).books.map { it.id })
        assertEquals(listOf(10L, 9L), page(repository.getBooks(1, "", BookSort.PUBLISHED_YEAR)).books.map { it.id })
    }

    @Test
    fun serverErrorIsNotTreatedAsOffline() = runTest {
        val failure = AppResult.Failure(AppError.Server(500, "gagal"))
        remote.failure = failure

        assertEquals(failure, repository.getBooks(1, ""))
    }

    @Test
    fun listedBookCanBePeekedWithoutIo() = runTest {
        repository.getBooks(1, "")

        assertEquals(book, repository.peekBook(9))
        assertEquals(null, repository.peekBook(10))
    }

    @Test
    fun offlineDetailUsesLocalCopy() = runTest {
        repository.getBook(9)
        remote.failure = networkDown

        assertEquals(AppResult.Success(book), repository.getBook(9))
    }

    @Test
    fun offlineDetailWithoutCopyKeepsError() = runTest {
        remote.failure = networkDown

        assertEquals(networkDown, repository.getBook(9))
    }

    @Test
    fun failedProgressStaysQueuedAndMovesLocalCopy() = runTest {
        repository.getBook(9)
        remote.progressFailure = networkDown

        repository.updateProgress(9, 40)

        assertEquals(mapOf(9L to 40), store.pendingProgress())
        assertEquals(40, store.book(9)?.lastReadPage)
    }

    @Test
    fun confirmedProgressLeavesQueue() = runTest {
        repository.updateProgress(9, 40)

        assertTrue(store.pendingProgress().isEmpty())
    }

    @Test
    fun rejectedProgressLeavesQueue() = runTest {
        remote.progressFailure = AppResult.Failure(AppError.Validation("Nomor halaman tidak valid"))

        repository.updateProgress(9, 999)

        assertTrue(store.pendingProgress().isEmpty())
    }

    @Test
    fun listSendsQueuedProgressFirst() = runTest {
        remote.progressFailure = networkDown
        repository.updateProgress(9, 40)
        remote.progressFailure = null

        val books = page(repository.getBooks(1, "")).books

        assertEquals(listOf(40, 40), remote.sentPages)
        assertTrue(store.pendingProgress().isEmpty())
        assertEquals(5, books.single().lastReadPage)
    }

    @Test
    fun queuedPageWinsOverOlderServerPage() = runTest {
        remote.progressFailure = networkDown
        repository.updateProgress(9, 40)

        val result = assertIs<AppResult.Success<RemoteBook>>(repository.getBook(9))

        assertEquals(40, result.data.lastReadPage)
    }

    @Test
    fun confirmingOldPageKeepsNewerQueuedPage() = runTest {
        store.queueProgress(9, 50)

        store.removeProgress(9, 40)

        assertEquals(mapOf(9L to 50), store.pendingProgress())
    }

    @Test
    fun deletedBookLeavesLocalCopyAndQueue() = runTest {
        repository.getBook(9)
        store.queueProgress(9, 40)

        repository.deleteBook(9)

        assertEquals(null, store.book(9))
        assertTrue(store.pendingProgress().isEmpty())
    }

    @Test
    fun failedSessionIsResentBeforeSummary() = runTest {
        remoteStats.failure = networkDown
        stats.saveSession(session("a"))
        assertEquals(listOf(session("a")), store.pendingSessions())
        remoteStats.failure = null

        stats.getSummary(LocalDate(2026, 11, 2))

        assertEquals(listOf("a", "a"), remoteStats.sent)
        assertTrue(store.pendingSessions().isEmpty())
    }

    @Test
    fun summaryStopsSendingWhenNetworkIsDown() = runTest {
        remoteStats.failure = networkDown
        stats.saveSession(session("a"))
        stats.saveSession(session("b"))
        remoteStats.sent.clear()

        stats.getSummary(LocalDate(2026, 11, 2))

        assertEquals(1, remoteStats.sent.size)
        assertEquals(2, store.pendingSessions().size)
    }

    @Test
    fun signOutRemovesEverythingLocal() = runTest {
        val session = SessionManager(FakeTokenStore())
        session.signIn("token")
        store.saveBooks(listOf(book))
        store.queueProgress(9, 40)
        store.queueSession(session("a"))
        files.downloaded += 9

        SignOutUseCase(session, store, files)()

        assertEquals(null, session.currentToken())
        assertTrue(store.books().isEmpty())
        assertTrue(store.pendingProgress().isEmpty())
        assertTrue(store.pendingSessions().isEmpty())
        assertEquals(1, files.deleteAllCalls)
    }
}
