package com.sereneread.project.feature.auth

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.createHttpClient
import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.core.session.SessionState
import com.sereneread.project.feature.auth.data.AuthRepositoryImpl
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthRepositoryImplTest {

    private val store = FakeTokenStore()
    private val session = SessionManager(store)
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(body: String, status: HttpStatusCode = HttpStatusCode.OK): AuthRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return AuthRepositoryImpl(createHttpClient(engine, session), session)
    }

    private fun OutgoingContent.text() = (this as TextContent).text

    @Test
    fun loginSendsCredentialsAndStartsSession() = runTest {
        val result = repository("""{"status":true,"token":"abc"}""").login("a@b.c", "secret")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals("https://sereneread.my.id/api/v1/login", requests.single().url.toString())
        assertEquals("""{"email":"a@b.c","password":"secret"}""", requests.single().body.text())
        assertEquals(SessionState.Authenticated, session.state.value)
        assertEquals("abc", store.token)
    }

    @Test
    fun loginFindsTokenInsideData() = runTest {
        repository("""{"status":true,"data":{"access_token":"abc"}}""").login("a@b.c", "secret")

        assertEquals("abc", session.currentToken())
    }

    @Test
    fun loginWithoutTokenFails() = runTest {
        session.restore()

        val result = repository("""{"status":true,"message":"ok"}""").login("a@b.c", "secret")

        val failure = assertIs<AppResult.Failure>(result)
        assertIs<AppError.Unexpected>(failure.error)
        assertEquals(SessionState.Unauthenticated(), session.state.value)
    }

    @Test
    fun wrongPasswordKeepsServerMessage() = runTest {
        session.restore()
        val body = """{"status":false,"message":"email atau password salah"}"""

        val result = repository(body, HttpStatusCode.Unauthorized).login("a@b.c", "wrong")

        assertEquals(AppResult.Failure(AppError.Unauthorized("email atau password salah")), result)
        assertEquals(SessionState.Unauthenticated(expired = false), session.state.value)
    }

    @Test
    fun invalidInputKeepsServerMessage() = runTest {
        val body = """{"status":false,"message":"Kolom 'Password' wajib diisi."}"""

        val result = repository(body, HttpStatusCode.BadRequest).login("a@b.c", "")

        assertEquals(AppResult.Failure(AppError.Validation("Kolom 'Password' wajib diisi.")), result)
    }

    @Test
    fun tokenStorageFailureIsReturnedNotThrown() = runTest {
        store.failOnSave = true

        val result = repository("""{"token":"abc"}""").login("a@b.c", "secret")

        val failure = assertIs<AppResult.Failure>(result)
        assertIs<AppError.Unexpected>(failure.error)
    }

    @Test
    fun registerSendsAccountAndAcceptsAnyBody() = runTest {
        val result = repository("""{"status":true,"message":"created"}""", HttpStatusCode.Created)
            .register("jay", "a@b.c", "secret")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals("""{"username":"jay","email":"a@b.c","password":"secret"}""", requests.single().body.text())
    }

    @Test
    fun registerAcceptsEmptyBody() = runTest {
        val result = repository("", HttpStatusCode.Created).register("jay", "a@b.c", "secret")

        assertEquals(AppResult.Success(Unit), result)
    }
}
