package com.sereneread.project.core.network

import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.core.session.SessionState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class HttpClientTest {

    @Serializable
    private data class Item(val id: Long, val title: String? = null)

    private val session = SessionManager(FakeTokenStore())
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        createHttpClient(
            MockEngine { request ->
                requests += request
                handler(request)
            },
            session,
        )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private suspend fun call(status: HttpStatusCode, body: String = "{}"): AppResult<Item> {
        val client = client { json(body, status) }
        return safeApiCall { client.get("books") }
    }

    @Test
    fun resolvesRelativePathAgainstBaseUrl() = runTest {
        call(HttpStatusCode.OK, """{"id":1}""")

        assertEquals("https://sereneread.my.id/api/v1/books", requests.single().url.toString())
    }

    @Test
    fun sendsBearerTokenWhenSignedIn() = runTest {
        session.signIn("abc")

        call(HttpStatusCode.OK, """{"id":1}""")

        assertEquals("Bearer abc", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun sendsNoAuthorizationHeaderWhenSignedOut() = runTest {
        call(HttpStatusCode.OK, """{"id":1}""")

        assertFalse(HttpHeaders.Authorization in requests.single().headers)
    }

    @Test
    fun decodesSuccessAndIgnoresUnknownFields() = runTest {
        val result = call(HttpStatusCode.OK, """{"id":7,"title":"Buku","new_field":true}""")

        assertEquals(AppResult.Success(Item(7, "Buku")), result)
    }

    @Test
    fun treatsNullFieldAsAbsent() = runTest {
        val result = call(HttpStatusCode.OK, """{"id":7,"title":null}""")

        assertEquals(AppResult.Success(Item(7, null)), result)
    }

    @Test
    fun rejectedTokenExpiresSession() = runTest {
        session.signIn("abc")

        val result = call(HttpStatusCode.Unauthorized, """{"error":"token expired"}""")

        assertEquals(AppResult.Failure(AppError.Unauthorized("token expired")), result)
        assertEquals(SessionState.Unauthenticated(expired = true), session.state.value)
        assertNull(session.currentToken())
    }

    @Test
    fun unauthorizedWithoutTokenKeepsSessionState() = runTest {
        session.restore()

        val result = call(HttpStatusCode.Unauthorized, """{"error":"wrong password"}""")

        assertEquals(AppResult.Failure(AppError.Unauthorized("wrong password")), result)
        assertEquals(SessionState.Unauthenticated(expired = false), session.state.value)
    }

    @Test
    fun staleUnauthorizedDoesNotEndNewerSession() = runTest {
        session.signIn("old")
        val client = client {
            // The user signs in again while the old request is still in flight.
            session.signIn("new")
            json("{}", HttpStatusCode.Unauthorized)
        }

        safeApiCall<Item> { client.get("books") }

        assertEquals("new", session.currentToken())
        assertEquals(SessionState.Authenticated, session.state.value)
    }

    @Test
    fun mapsStatusCodesToErrors() = runTest {
        val cases = mapOf(
            HttpStatusCode.BadRequest to AppError.Validation("x"),
            HttpStatusCode.Forbidden to AppError.Forbidden("x"),
            HttpStatusCode.NotFound to AppError.NotFound("x"),
            HttpStatusCode.Conflict to AppError.Conflict("x"),
            HttpStatusCode.InternalServerError to AppError.Server(500, "x"),
        )

        cases.forEach { (status, expected) ->
            assertEquals(AppResult.Failure(expected), call(status, """{"message":"x"}"""))
        }
    }

    @Test
    fun unknownStatusIsUnexpected() = runTest {
        val result = call(HttpStatusCode.Gone)

        assertEquals(AppResult.Failure(AppError.Unexpected(cause = null, status = 410)), result)
    }

    @Test
    fun nonJsonErrorBodyHasNoMessage() = runTest {
        // Cloudflare answers with plain text when the tunnel is down.
        val client = client { respond("error code: 1033", HttpStatusCode.fromValue(530)) }

        val result = safeApiCall<Item> { client.get("books") }

        assertEquals(AppResult.Failure(AppError.Server(530, null)), result)
    }

    @Test
    fun malformedSuccessBodyIsUnexpected() = runTest {
        val result = call(HttpStatusCode.OK, """{"title":"no id"}""")

        val failure = assertIs<AppResult.Failure>(result)
        assertIs<AppError.Unexpected>(failure.error)
    }

    @Test
    fun connectionFailureIsNetworkError() = runTest {
        val client = client { throw IOException("connection refused") }

        val result = safeApiCall<Item> { client.get("books") }

        val failure = assertIs<AppResult.Failure>(result)
        assertIs<AppError.Network>(failure.error)
    }
}
