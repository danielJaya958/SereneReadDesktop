package com.sereneread.project.feature.stats

import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.createHttpClient
import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.stats.data.StatsRepositoryImpl
import com.sereneread.project.feature.stats.domain.BookStat
import com.sereneread.project.feature.stats.domain.DailyStat
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsSummary
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
import kotlinx.datetime.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Instant

class StatsRepositoryImplTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var sentBody = ""

    private fun repository(body: String): StatsRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            sentBody = request.body.toByteArray().decodeToString()
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return StatsRepositoryImpl(createHttpClient(engine, SessionManager(FakeTokenStore())))
    }

    @Test
    fun saveSessionSendsDocumentedFields() = runTest {
        val session = ReadingSession(
            clientSessionId = "6f1c7f1e-2a55-4b70-9b0a-3d7f7c7a1111",
            bookId = 9,
            startedAt = Instant.parse("2026-11-02T01:00:00.123Z"),
            endedAt = Instant.parse("2026-11-02T01:20:00Z"),
            localDate = LocalDate(2026, 11, 2),
            startPage = 5,
            endPage = 12,
            pagesRead = 7,
            durationSeconds = 900,
        )
        val body = """{"status":true,"data":{"accepted":1,"duplicates":0,"rejected":[]}}"""

        val result = repository(body).saveSession(session)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(HttpMethod.Post, requests.single().method)
        assertEquals("/api/v1/reading-sessions", requests.single().url.encodedPath)
        listOf(
            """{"sessions":[{""",
            """"client_session_id":"6f1c7f1e-2a55-4b70-9b0a-3d7f7c7a1111"""",
            """"book_id":9""",
            """"started_at":"2026-11-02T01:00:00.123Z"""",
            """"ended_at":"2026-11-02T01:20:00Z"""",
            """"local_date":"2026-11-02"""",
            """"start_page":5""",
            """"end_page":12""",
            """"pages_read":7""",
            """"duration_seconds":900""",
        ).forEach { assertContains(sentBody, it) }
        OffsetDateTime.parse(session.startedAt.toString())
    }

    @Test
    fun parsesSummary() = runTest {
        val body = """
            {"status":true,"data":{
            "today":{"minutes":1,"pages":2},
            "this_week":{"minutes":3,"pages":4,"active_days":5},
            "all_time":{"minutes":6,"pages":7,"active_days":20,"sessions":8},
            "current_streak_days":9,"longest_streak_days":10,
            "books_finished":{"this_year":11,"all_time":12},
            "last_read_date":null}}
        """.trimIndent()

        val result = repository(body).getSummary(LocalDate(2026, 11, 2))

        assertEquals(AppResult.Success(StatsSummary(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)), result)
        assertEquals("/api/v1/stats/summary", requests.single().url.encodedPath)
        assertEquals("2026-11-02", requests.single().url.parameters["today"])
    }

    @Test
    fun parsesDaily() = runTest {
        val body = """{"status":true,"data":[{"date":"2026-11-02","minutes":30,"pages":12,"sessions":2}]}"""

        val result = repository(body).getDaily(LocalDate(2026, 10, 27), LocalDate(2026, 11, 2))

        assertEquals(AppResult.Success(listOf(DailyStat(LocalDate(2026, 11, 2), 30, 12))), result)
        assertEquals("2026-10-27", requests.single().url.parameters["from"])
        assertEquals("2026-11-02", requests.single().url.parameters["to"])
    }

    @Test
    fun parsesTopBooks() = runTest {
        val body = """
            {"status":true,"data":[{"book_id":9,"title":"T","thumbnail_url":"/x.jpg","minutes":30,
            "pages":12,"sessions":2,"last_read_date":"2026-11-02"}]}
        """.trimIndent()

        val result = repository(body).getTopBooks()

        assertEquals(AppResult.Success(listOf(BookStat(9, "T", 30, 12))), result)
        assertEquals("/api/v1/stats/books", requests.single().url.encodedPath)
    }
}
