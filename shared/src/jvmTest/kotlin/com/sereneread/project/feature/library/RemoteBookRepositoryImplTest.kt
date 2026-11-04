package com.sereneread.project.feature.library

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.createHttpClient
import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.library.data.RemoteBookRepositoryImpl
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.library.domain.RemoteBookPage
import com.sereneread.project.feature.library.domain.UploadBook
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteBookRepositoryImplTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var sentBody = ""
    private val tempFile = File.createTempFile("private-name", ".pdf").apply { writeText("%PDF-content") }

    @AfterTest
    fun tearDown() {
        tempFile.delete()
    }

    private fun repository(body: String, status: HttpStatusCode = HttpStatusCode.OK): RemoteBookRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            sentBody = request.body.toByteArray().decodeToString()
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return RemoteBookRepositoryImpl(createHttpClient(engine, SessionManager(FakeTokenStore())))
    }

    @Test
    fun parsesServerPage() = runTest {
        val body = """
            {"status":true,"message":"ok","data":[{"id":9,"title":"The Four Agreements","author":"Tester",
            "published_year":2024,"thumbnail_url":"/x.jpg","last_read_page":5,"total_pages":94,
            "is_favorite":false,"finished_at":null}],
            "meta":{"page":2,"limit":10,"total_items":11,"has_next":true}}
        """.trimIndent()

        val result = assertIs<AppResult.Success<*>>(repository(body).getBooks(page = 2, query = " agree "))
        val page = assertIs<com.sereneread.project.feature.library.domain.RemoteBookPage>(result.data)

        assertEquals(listOf(RemoteBook(9, "The Four Agreements", "Tester", 2024, 5, 94, false, "/x.jpg")), page.books)
        assertTrue(page.hasNext)
        val url = requests.single().url
        assertEquals("/api/v1/books", url.encodedPath)
        assertEquals("2", url.parameters["page"])
        assertEquals("agree", url.parameters["q"])
        assertEquals("-created_at", url.parameters["sort"])
    }

    @Test
    fun sendsChosenSortAndTreatsBlankCoverAsMissing() = runTest {
        val body = """{"status":true,"data":[{"id":9,"title":"T","author":"A","published_year":2024,"thumbnail_url":""}]}"""

        val page = assertIs<AppResult.Success<RemoteBookPage>>(repository(body).getBooks(1, "", BookSort.PUBLISHED_YEAR))

        assertEquals(null, page.data.books.single().thumbnailUrl)
        assertEquals("-published_year", requests.single().url.parameters["sort"])
    }

    @Test
    fun blankQueryIsNotSentAndNullDataIsEmpty() = runTest {
        val result = repository("""{"status":true,"data":null}""").getBooks(page = 1, query = "  ")

        val page = assertIs<AppResult.Success<*>>(result).data
        assertEquals(
            com.sereneread.project.feature.library.domain.RemoteBookPage(emptyList(), 1, false),
            page,
        )
        assertFalse("q" in requests.single().url.parameters)
    }

    @Test
    fun uploadSendsDocumentedMultipartFields() = runTest {
        val body = """{"status":true,"data":{"id":1,"title":"T","author":"A","published_year":2024,"pdf_url":"/p"}}"""

        val result = repository(body, HttpStatusCode.Created)
            .uploadBook(UploadBook(tempFile.path, "T", "A", 2024)) {}

        assertEquals(AppResult.Success(RemoteBook(1, "T", "A", 2024, 0, 0, false)), result)
        assertEquals(HttpMethod.Post, requests.single().method)
        listOf(
            """name="title"""",
            """name="author"""",
            """name="published_year"""",
            """form-data; name="pdf_file"; filename="book.pdf"""",
            "%PDF-content",
        ).forEach { assertContains(sentBody, it) }
        assertFalse("private-name" in sentBody)
    }

    @Test
    fun uploadRejectsNonPdfWithoutRequest() = runTest {
        val epub = File.createTempFile("book", ".epub")
        try {
            val result = repository("{}").uploadBook(UploadBook(epub.path, "T", "A", 2024)) {}

            assertEquals(AppResult.Failure(AppError.Validation("Format file harus PDF.")), result)
            assertTrue(requests.isEmpty())
        } finally {
            epub.delete()
        }
    }

    @Test
    fun uploadRejectsOversizedFileWithoutRequest() = runTest {
        java.io.RandomAccessFile(tempFile, "rw").use { it.setLength(75L * 1024 * 1024 + 1) }

        val result = repository("{}").uploadBook(UploadBook(tempFile.path, "T", "A", 2024)) {}

        assertEquals(AppResult.Failure(AppError.Validation("Ukuran file maksimal 75MB.")), result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun getBookReadsDetail() = runTest {
        val body = """{"status":true,"data":{"id":9,"title":"T","author":"A","published_year":2024,"pdf_url":"/p"}}"""

        val result = repository(body).getBook(9)

        assertEquals(AppResult.Success(RemoteBook(9, "T", "A", 2024, 0, 0, false)), result)
        assertEquals("/api/v1/books/9", requests.single().url.encodedPath)
    }

    @Test
    fun missingBookIsNotFound() = runTest {
        val body = """{"status":false,"message":"Buku tidak ditemukan"}"""

        val result = repository(body, HttpStatusCode.NotFound).getBook(9)

        assertEquals(AppResult.Failure(AppError.NotFound("Buku tidak ditemukan")), result)
    }

    @Test
    fun updateSendsAllFieldsWithPut() = runTest {
        val body = """{"status":true,"data":{"id":9,"title":"New","author":"B","published_year":2020}}"""

        val result = repository(body).updateBook(9, BookChanges("New", "B", 2020))

        assertEquals(AppResult.Success(RemoteBook(9, "New", "B", 2020, 0, 0, false)), result)
        assertEquals(HttpMethod.Put, requests.single().method)
        assertEquals("""{"title":"New","author":"B","published_year":2020}""", sentBody)
    }

    @Test
    fun deleteUsesBookPath() = runTest {
        val result = repository("""{"status":true,"message":"ok"}""").deleteBook(9)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(HttpMethod.Delete, requests.single().method)
        assertEquals("/api/v1/books/9", requests.single().url.encodedPath)
    }

    @Test
    fun updateProgressSendsDocumentedField() = runTest {
        val result = repository("""{"status":true,"message":"ok","data":null}""").updateProgress(9, 42)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(HttpMethod.Patch, requests.single().method)
        assertEquals("/api/v1/books/9/progress", requests.single().url.encodedPath)
        assertEquals("""{"last_read_page":42}""", sentBody)
    }

    @Test
    fun setFavoriteSendsValueWithPut() = runTest {
        val body = """{"status":true,"data":{"id":9,"title":"T","author":"A","published_year":2024,"is_favorite":true}}"""

        val result = repository(body).setFavorite(9, true)

        assertEquals(AppResult.Success(RemoteBook(9, "T", "A", 2024, 0, 0, true)), result)
        assertEquals(HttpMethod.Put, requests.single().method)
        assertEquals("/api/v1/books/9/favorite", requests.single().url.encodedPath)
        assertEquals("""{"is_favorite":true}""", sentBody)
    }

    @Test
    fun updateProgressKeepsServerMessage() = runTest {
        val body = """{"status":false,"message":"Nomor halaman melebihi total halaman (94)"}"""

        val result = repository(body, HttpStatusCode.BadRequest).updateProgress(9, 95)

        assertEquals(
            AppResult.Failure(AppError.Validation("Nomor halaman melebihi total halaman (94)")),
            result,
        )
    }

    @Test
    fun uploadKeepsServerMessage() = runTest {
        val body = """{"status":false,"message":"Tahun terbit harus di antara 1000 dan 2026"}"""

        val result = repository(body, HttpStatusCode.BadRequest)
            .uploadBook(UploadBook(tempFile.path, "T", "A", 1)) {}

        assertEquals(
            AppResult.Failure(AppError.Validation("Tahun terbit harus di antara 1000 dan 2026")),
            result,
        )
    }
}
