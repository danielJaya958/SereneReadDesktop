package com.sereneread.project.feature.stats.data

import com.sereneread.project.core.network.ApiEnvelope
import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.safeApiCall
import com.sereneread.project.feature.stats.domain.BookStat
import com.sereneread.project.feature.stats.domain.DailyStat
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsRepository
import com.sereneread.project.feature.stats.domain.StatsSummary
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class SessionsRequest(val sessions: List<SessionDto>)

@Serializable
private data class SessionDto(
    @SerialName("client_session_id") val clientSessionId: String,
    @SerialName("book_id") val bookId: Long,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("local_date") val localDate: String,
    @SerialName("start_page") val startPage: Int,
    @SerialName("end_page") val endPage: Int,
    @SerialName("pages_read") val pagesRead: Int,
    @SerialName("duration_seconds") val durationSeconds: Int,
)

@Serializable
private data class PeriodDto(
    val minutes: Int = 0,
    val pages: Int = 0,
    @SerialName("active_days") val activeDays: Int = 0,
    val sessions: Int = 0,
)

@Serializable
private data class BooksFinishedDto(
    @SerialName("this_year") val thisYear: Int = 0,
    @SerialName("all_time") val allTime: Int = 0,
)

@Serializable
private data class SummaryDto(
    val today: PeriodDto,
    @SerialName("this_week") val thisWeek: PeriodDto,
    @SerialName("all_time") val allTime: PeriodDto,
    @SerialName("current_streak_days") val currentStreakDays: Int = 0,
    @SerialName("longest_streak_days") val longestStreakDays: Int = 0,
    @SerialName("books_finished") val booksFinished: BooksFinishedDto = BooksFinishedDto(),
) {
    fun toDomain() = StatsSummary(
        todayMinutes = today.minutes,
        todayPages = today.pages,
        weekMinutes = thisWeek.minutes,
        weekPages = thisWeek.pages,
        weekActiveDays = thisWeek.activeDays,
        totalMinutes = allTime.minutes,
        totalPages = allTime.pages,
        totalSessions = allTime.sessions,
        currentStreakDays = currentStreakDays,
        longestStreakDays = longestStreakDays,
        booksFinishedThisYear = booksFinished.thisYear,
        booksFinishedAllTime = booksFinished.allTime,
    )
}

@Serializable
private data class DailyDto(
    val date: String,
    val minutes: Int = 0,
    val pages: Int = 0,
)

@Serializable
private data class BookStatDto(
    @SerialName("book_id") val bookId: Long,
    val title: String,
    val minutes: Int = 0,
    val pages: Int = 0,
)

class StatsRepositoryImpl(private val client: HttpClient) : StatsRepository {

    override suspend fun saveSession(session: ReadingSession): AppResult<Unit> =
        safeApiCall {
            client.post("reading-sessions") {
                contentType(ContentType.Application.Json)
                setBody(SessionsRequest(listOf(session.toDto())))
            }
        }

    override suspend fun getSummary(today: LocalDate): AppResult<StatsSummary> {
        val response = safeApiCall<ApiEnvelope<SummaryDto>> {
            client.get("stats/summary") { parameter("today", today.toString()) }
        }
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> response.data.data?.let { AppResult.Success(it.toDomain()) }
                ?: AppResult.Failure(AppError.Unexpected(IllegalStateException("Response has no summary")))
        }
    }

    override suspend fun getDaily(from: LocalDate, to: LocalDate): AppResult<List<DailyStat>> {
        val response = safeApiCall<ApiEnvelope<List<DailyDto>>> {
            client.get("stats/daily") {
                parameter("from", from.toString())
                parameter("to", to.toString())
            }
        }
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> AppResult.Success(
                response.data.data.orEmpty().map { DailyStat(LocalDate.parse(it.date), it.minutes, it.pages) },
            )
        }
    }

    override suspend fun getTopBooks(): AppResult<List<BookStat>> =
        when (val response = safeApiCall<ApiEnvelope<List<BookStatDto>>> { client.get("stats/books") }) {
            is AppResult.Failure -> response
            is AppResult.Success -> AppResult.Success(
                response.data.data.orEmpty().map { BookStat(it.bookId, it.title, it.minutes, it.pages) },
            )
        }

    private fun ReadingSession.toDto() = SessionDto(
        clientSessionId = clientSessionId,
        bookId = bookId,
        startedAt = startedAt.toString(),
        endedAt = endedAt.toString(),
        localDate = localDate.toString(),
        startPage = startPage,
        endPage = endPage,
        pagesRead = pagesRead,
        durationSeconds = durationSeconds,
    )
}
