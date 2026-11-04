package com.sereneread.project.feature.stats.domain

import com.sereneread.project.core.network.AppResult
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** [durationSeconds] counts active reading only, so it can be shorter than the time between the two instants. */
data class ReadingSession(
    val clientSessionId: String,
    val bookId: Long,
    val startedAt: Instant,
    val endedAt: Instant,
    val localDate: LocalDate,
    val startPage: Int,
    val endPage: Int,
    val pagesRead: Int,
    val durationSeconds: Int,
)

data class StatsSummary(
    val todayMinutes: Int,
    val todayPages: Int,
    val weekMinutes: Int,
    val weekPages: Int,
    val weekActiveDays: Int,
    val totalMinutes: Int,
    val totalPages: Int,
    val totalSessions: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val booksFinishedThisYear: Int,
    val booksFinishedAllTime: Int,
)

data class DailyStat(
    val date: LocalDate,
    val minutes: Int,
    val pages: Int,
)

data class BookStat(
    val bookId: Long,
    val title: String,
    val minutes: Int,
    val pages: Int,
)

interface StatsRepository {
    suspend fun saveSession(session: ReadingSession): AppResult<Unit>
    suspend fun getSummary(today: LocalDate): AppResult<StatsSummary>

    /** Returns one item per date, including days without reading. */
    suspend fun getDaily(from: LocalDate, to: LocalDate): AppResult<List<DailyStat>>
    suspend fun getTopBooks(): AppResult<List<BookStat>>
}
