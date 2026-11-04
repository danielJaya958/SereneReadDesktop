package com.sereneread.project.feature.stats

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.feature.stats.domain.BookStat
import com.sereneread.project.feature.stats.domain.DailyStat
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsRepository
import com.sereneread.project.feature.stats.domain.StatsSummary
import com.sereneread.project.feature.stats.presentation.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val summary = StatsSummary(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    private val days = listOf(DailyStat(LocalDate(2026, 11, 2), 30, 12))
    private val books = listOf(BookStat(9, "T", 30, 12))

    private inner class FakeRepository : StatsRepository {
        var summaryResult: AppResult<StatsSummary> = AppResult.Success(summary)
        var dailyResult: AppResult<List<DailyStat>> = AppResult.Success(days)
        var requestedToday: LocalDate? = null
        var requestedRange: Pair<LocalDate, LocalDate>? = null

        override suspend fun saveSession(session: ReadingSession) = error("not used")

        override suspend fun getSummary(today: LocalDate): AppResult<StatsSummary> {
            requestedToday = today
            return summaryResult
        }

        override suspend fun getDaily(from: LocalDate, to: LocalDate): AppResult<List<DailyStat>> {
            requestedRange = from to to
            return dailyResult
        }

        override suspend fun getTopBooks() = AppResult.Success(books)
    }

    private val repository = FakeRepository()
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-01T20:00:00Z")
    }

    private fun viewModel() =
        StatsViewModel(repository, clock, TimeZone.of("Asia/Jakarta")).apply { load() }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsAllSections() {
        val state = viewModel().state.value

        assertEquals(summary, state.summary)
        assertEquals(days, state.daily)
        assertEquals(books, state.books)
        assertFalse(state.isLoading)
    }

    @Test
    fun datesUseLocalDayAndLastSevenDays() {
        viewModel()

        assertEquals(LocalDate(2026, 11, 2), repository.requestedToday)
        assertEquals(LocalDate(2026, 10, 27) to LocalDate(2026, 11, 2), repository.requestedRange)
    }

    @Test
    fun anyFailureShowsMessage() {
        repository.dailyResult = AppResult.Failure(AppError.Server(500, "gagal"))

        val state = viewModel().state.value

        assertEquals("gagal", state.error)
        assertNull(state.summary)
    }

    @Test
    fun retryClearsError() {
        repository.summaryResult = AppResult.Failure(AppError.Server(500, "gagal"))
        val viewModel = viewModel()
        repository.summaryResult = AppResult.Success(summary)

        viewModel.load()

        assertNull(viewModel.state.value.error)
        assertEquals(summary, viewModel.state.value.summary)
    }
}
