package com.sereneread.project.feature.library.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sereneread.project.core.database.BookEntity
import com.sereneread.project.core.database.SereneReadDatabase
import com.sereneread.project.feature.library.domain.Book
import com.sereneread.project.feature.library.domain.BookFormat
import com.sereneread.project.feature.library.domain.BookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookRepositoryImpl(
    database: SereneReadDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : BookRepository {

    private val queries = database.bookQueries

    override fun observeBooks(): Flow<List<Book>> =
        queries.selectAll().asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getBook(id: Long): Book? = withContext(ioDispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun findByPath(filePath: String): Book? = withContext(ioDispatcher) {
        queries.selectByPath(filePath).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun addBook(title: String, author: String?, filePath: String, format: BookFormat, addedAt: Long): Long =
        withContext(ioDispatcher) {
            queries.transactionWithResult {
                queries.insert(title, author, filePath, format.name, addedAt)
                queries.lastInsertId().executeAsOne()
            }
        }

    override suspend fun markOpened(id: Long, openedAt: Long) {
        withContext(ioDispatcher) { queries.markOpened(openedAt, id) }
    }

    override suspend fun updateProgress(id: Long, progress: Float) {
        withContext(ioDispatcher) { queries.updateProgress(progress.toDouble(), id) }
    }

    override suspend fun deleteBook(id: Long) {
        withContext(ioDispatcher) { queries.deleteById(id) }
    }
}

private fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    filePath = filePath,
    format = BookFormat.valueOf(format),
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    progress = progress.toFloat(),
)
