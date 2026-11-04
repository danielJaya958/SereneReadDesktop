package com.sereneread.project.feature.offline.data

import com.sereneread.project.core.database.PendingProgress
import com.sereneread.project.core.database.PendingSession
import com.sereneread.project.core.database.RemoteBookEntity
import com.sereneread.project.core.database.SereneReadDatabase
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.stats.domain.ReadingSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Local copies of server books, plus reading data that the server has not confirmed yet. */
class OfflineStore(
    database: SereneReadDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val queries = database.offlineQueries

    suspend fun saveBooks(books: List<RemoteBook>) = withContext(ioDispatcher) {
        queries.transaction { books.forEach { queries.saveBook(it.toEntity()) } }
    }

    suspend fun book(id: Long): RemoteBook? = withContext(ioDispatcher) {
        queries.selectBook(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun books(): List<RemoteBook> = withContext(ioDispatcher) {
        queries.selectBooks().executeAsList().map { it.toDomain() }
    }

    suspend fun deleteBook(id: Long) = withContext(ioDispatcher) {
        queries.transaction {
            queries.deleteBook(id)
            queries.deleteBookProgress(id)
        }
    }

    suspend fun queueProgress(bookId: Long, page: Int) = withContext(ioDispatcher) {
        queries.transaction {
            queries.savePendingProgress(PendingProgress(bookId, page.toLong()))
            queries.updateCachedPage(page.toLong(), bookId)
        }
    }

    suspend fun pendingProgress(): Map<Long, Int> = withContext(ioDispatcher) {
        queries.selectPendingProgress().executeAsList().associate { it.bookId to it.page.toInt() }
    }

    /** Matches [page] too, so confirming an old page cannot remove a newer one. */
    suspend fun removeProgress(bookId: Long, page: Int) = withContext(ioDispatcher) {
        queries.deletePendingProgress(bookId, page.toLong())
    }

    suspend fun queueSession(session: ReadingSession) = withContext(ioDispatcher) {
        queries.savePendingSession(session.toEntity())
    }

    suspend fun pendingSessions(): List<ReadingSession> = withContext(ioDispatcher) {
        queries.selectPendingSessions().executeAsList().map { it.toDomain() }
    }

    suspend fun removeSession(clientSessionId: String) = withContext(ioDispatcher) {
        queries.deletePendingSession(clientSessionId)
    }

    suspend fun clear() = withContext(ioDispatcher) {
        queries.transaction {
            queries.clearBooks()
            queries.clearPendingProgress()
            queries.clearPendingSessions()
        }
    }
}

private fun RemoteBook.toEntity() = RemoteBookEntity(
    id = id,
    title = title,
    author = author,
    publishedYear = publishedYear.toLong(),
    lastReadPage = lastReadPage.toLong(),
    totalPages = totalPages.toLong(),
    isFavorite = if (isFavorite) 1 else 0,
)

private fun RemoteBookEntity.toDomain() = RemoteBook(
    id = id,
    title = title,
    author = author,
    publishedYear = publishedYear.toInt(),
    lastReadPage = lastReadPage.toInt(),
    totalPages = totalPages.toInt(),
    isFavorite = isFavorite == 1L,
)

private fun ReadingSession.toEntity() = PendingSession(
    clientSessionId = clientSessionId,
    bookId = bookId,
    startedAt = startedAt.toString(),
    endedAt = endedAt.toString(),
    localDate = localDate.toString(),
    startPage = startPage.toLong(),
    endPage = endPage.toLong(),
    pagesRead = pagesRead.toLong(),
    durationSeconds = durationSeconds.toLong(),
)

private fun PendingSession.toDomain() = ReadingSession(
    clientSessionId = clientSessionId,
    bookId = bookId,
    startedAt = Instant.parse(startedAt),
    endedAt = Instant.parse(endedAt),
    localDate = LocalDate.parse(localDate),
    startPage = startPage.toInt(),
    endPage = endPage.toInt(),
    pagesRead = pagesRead.toInt(),
    durationSeconds = durationSeconds.toInt(),
)
