package com.sereneread.project.feature.library.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ImportBookUseCaseTest {

    private val repository = FakeBookRepository()
    private var metadata: BookMetadata? = null
    private val importBook = ImportBookUseCase(repository, metadataReader = { _, _ -> metadata }, now = { 1_000L })

    @Test
    fun importsSupportedFileWithTitleFromFileName() = runTest {
        val result = importBook("C:\\Books\\The_Little_Prince.EPUB")

        assertIs<ImportResult.Imported>(result)
        val book = repository.books.value.single()
        assertEquals("The Little Prince", book.title)
        assertNull(book.author)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals(1_000L, book.addedAt)
    }

    @Test
    fun prefersEmbeddedMetadataOverFileName() = runTest {
        metadata = BookMetadata(title = "  Le Petit Prince ", author = "Antoine de Saint-Exupéry")

        importBook("/books/tlp_v2.epub")

        val book = repository.books.value.single()
        assertEquals("Le Petit Prince", book.title)
        assertEquals("Antoine de Saint-Exupéry", book.author)
    }

    @Test
    fun fallsBackToFileNameWhenMetadataTitleIsBlank() = runTest {
        metadata = BookMetadata(title = " ", author = "")

        importBook("/books/my_book.epub")

        val book = repository.books.value.single()
        assertEquals("my book", book.title)
        assertNull(book.author)
    }

    @Test
    fun rejectsUnsupportedFormat() = runTest {
        assertEquals(ImportResult.UnsupportedFormat, importBook("/home/me/notes.docx"))
        assertEquals(0, repository.books.value.size)
    }

    @Test
    fun returnsExistingBookForDuplicatePath() = runTest {
        val first = assertIs<ImportResult.Imported>(importBook("/books/a.txt"))

        val second = importBook("/books/a.txt")

        assertEquals(ImportResult.AlreadyExists(first.bookId), second)
        assertEquals(1, repository.books.value.size)
    }
}

private class FakeBookRepository : BookRepository {
    val books = MutableStateFlow<List<Book>>(emptyList())
    private var nextId = 1L

    override fun observeBooks(): Flow<List<Book>> = books
    override suspend fun getBook(id: Long) = books.value.firstOrNull { it.id == id }
    override suspend fun findByPath(filePath: String) = books.value.firstOrNull { it.filePath == filePath }

    override suspend fun addBook(title: String, author: String?, filePath: String, format: BookFormat, addedAt: Long): Long {
        val id = nextId++
        books.value += Book(id, title, author, filePath, format, addedAt, lastOpenedAt = null, progress = 0f)
        return id
    }

    override suspend fun markOpened(id: Long, openedAt: Long) = update(id) { it.copy(lastOpenedAt = openedAt) }
    override suspend fun updateProgress(id: Long, progress: Float) = update(id) { it.copy(progress = progress) }
    override suspend fun deleteBook(id: Long) {
        books.value = books.value.filterNot { it.id == id }
    }

    private fun update(id: Long, transform: (Book) -> Book) {
        books.value = books.value.map { if (it.id == id) transform(it) else it }
    }
}
