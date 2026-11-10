package com.sereneread.project.feature.offline.data

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.library.domain.RemoteBookFiles
import com.sereneread.project.feature.library.domain.RemoteBookPage
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Answers from the local copy when the network is down, and queues progress until the server confirms it. */
class OfflineRemoteBookRepository(
    private val remote: RemoteBookRepository,
    private val store: OfflineStore,
    private val files: RemoteBookFiles,
    private val sync: OfflineSync,
) : RemoteBookRepository by remote {

    private val seen = MutableStateFlow<Map<Long, RemoteBook>>(emptyMap())

    override fun peekBook(id: Long): RemoteBook? = seen.value[id]

    override suspend fun getBooks(page: Int, query: String, sort: BookSort): AppResult<RemoteBookPage> {
        if (page == 1) sync.flush()
        return when (val result = remote.getBooks(page, query, sort)) {
            is AppResult.Success -> {
                val pending = store.pendingProgress()
                val books = result.data.books.map { it.withPending(pending) }
                store.saveBooks(books)
                seen.update { it + books.associateBy(RemoteBook::id) }
                AppResult.Success(result.data.copy(books = books))
            }
            is AppResult.Failure -> if (result.error is AppError.Network) offlinePage(page, query, sort) else result
        }
    }

    override suspend fun getBook(id: Long): AppResult<RemoteBook> =
        when (val result = remote.getBook(id)) {
            is AppResult.Success -> remember(result.data.withPending(store.pendingProgress()))
            is AppResult.Failure -> {
                val cached = if (result.error is AppError.Network) store.book(id) else null
                cached?.let { AppResult.Success(it) } ?: result
            }
        }

    override suspend fun updateBook(id: Long, changes: BookChanges): AppResult<RemoteBook> =
        remote.updateBook(id, changes).remembered()

    override suspend fun setFavorite(id: Long, isFavorite: Boolean): AppResult<RemoteBook> =
        remote.setFavorite(id, isFavorite).remembered()

    override suspend fun deleteBook(id: Long): AppResult<Unit> {
        val result = remote.deleteBook(id)
        if (result is AppResult.Success) {
            store.deleteBook(id)
            seen.update { it - id }
        }
        return result
    }

    /** Queues first, so a timeout or crash during the request cannot lose the page. */
    override suspend fun updateProgress(id: Long, page: Int): AppResult<Unit> {
        store.queueProgress(id, page)
        return sync.sendProgress(id, page)
    }

    /** Every downloaded book, not only the loaded pages, with any page still waiting to be sent. */
    override suspend fun downloadedBooks(): List<RemoteBook> {
        val pending = store.pendingProgress()
        return store.books().filter { files.isDownloaded(it.id) }.map { it.withPending(pending) }
    }

    /**
     * Lists downloaded books only, because other books cannot be opened without a network.
     * The local copy has no upload date, so [BookSort.NEWEST] falls back to title order.
     */
    private suspend fun offlinePage(page: Int, query: String, sort: BookSort): AppResult<RemoteBookPage> {
        val needle = query.trim()
        val books = if (page == 1) {
            store.books().filter {
                files.isDownloaded(it.id) &&
                    (it.title.contains(needle, ignoreCase = true) || it.author.contains(needle, ignoreCase = true))
            }.sortedWith(sort.localOrder)
        } else {
            emptyList()
        }
        return AppResult.Success(RemoteBookPage(books, page, hasNext = false, isOffline = true))
    }

    private suspend fun remember(book: RemoteBook): AppResult<RemoteBook> {
        store.saveBooks(listOf(book))
        seen.update { it + (book.id to book) }
        return AppResult.Success(book)
    }

    private suspend fun AppResult<RemoteBook>.remembered(): AppResult<RemoteBook> = when (this) {
        is AppResult.Success -> remember(data.withPending(store.pendingProgress()))
        is AppResult.Failure -> this
    }

    /** A queued page is newer than what the server reports. */
    private fun RemoteBook.withPending(pending: Map<Long, Int>) = copy(lastReadPage = pending[id] ?: lastReadPage)

    private val BookSort.localOrder: Comparator<RemoteBook>
        get() = when (this) {
            BookSort.NEWEST, BookSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            BookSort.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.author }
            BookSort.PUBLISHED_YEAR -> compareByDescending { it.publishedYear }
        }
}
