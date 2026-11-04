package com.sereneread.project.feature.reader.data

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.createHttpClient
import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.reader.domain.Bookmark
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
import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkRepositoryImplTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var sentBody = ""

    private fun repository(body: String, status: HttpStatusCode = HttpStatusCode.OK): BookmarkRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            sentBody = request.body.toByteArray().decodeToString()
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return BookmarkRepositoryImpl(createHttpClient(engine, SessionManager(FakeTokenStore())))
    }

    @Test
    fun parsesList() = runTest {
        val body = """
            {"status":true,"message":"ok","data":[
            {"id":1,"book_id":9,"page":3,"note":null,"created_at":"2026-11-01T10:00:00Z","updated_at":"2026-11-01T10:00:00Z"},
            {"id":2,"book_id":9,"page":8,"note":"ingat","created_at":"2026-11-01T10:00:00Z","updated_at":"2026-11-01T10:00:00Z"}
            ]}
        """.trimIndent()

        val result = repository(body).getBookmarks(9)

        assertEquals(AppResult.Success(listOf(Bookmark(1, 3, null), Bookmark(2, 8, "ingat"))), result)
        assertEquals("/api/v1/books/9/bookmarks", requests.single().url.encodedPath)
    }

    @Test
    fun saveSendsPageAndNote() = runTest {
        val body = """{"status":true,"data":{"id":5,"book_id":9,"page":3,"note":"ingat"}}"""

        val result = repository(body, HttpStatusCode.Created).saveBookmark(9, 3, "ingat")

        assertEquals(AppResult.Success(Bookmark(5, 3, "ingat")), result)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertEquals("""{"page":3,"note":"ingat"}""", sentBody)
    }

    @Test
    fun saveAcceptsOkForExistingPage() = runTest {
        val body = """{"status":true,"data":{"id":5,"book_id":9,"page":3,"note":null}}"""

        val result = repository(body).saveBookmark(9, 3, null)

        assertEquals(AppResult.Success(Bookmark(5, 3, null)), result)
    }

    @Test
    fun saveKeepsServerMessage() = runTest {
        val body = """{"status":false,"message":"Catatan maksimal 500 karakter"}"""

        val result = repository(body, HttpStatusCode.BadRequest).saveBookmark(9, 3, "x")

        assertEquals(AppResult.Failure(AppError.Validation("Catatan maksimal 500 karakter")), result)
    }

    @Test
    fun deleteUsesBookmarkPath() = runTest {
        val result = repository("""{"status":true,"message":"ok","data":null}""").deleteBookmark(9, 5)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(HttpMethod.Delete, requests.single().method)
        assertEquals("/api/v1/books/9/bookmarks/5", requests.single().url.encodedPath)
    }
}
