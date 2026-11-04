package com.sereneread.project.feature.stats

import com.sereneread.project.feature.stats.domain.ReadingSessionTracker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReadingSessionTrackerTest {

    private var now = Instant.parse("2026-11-01T20:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }

    private fun tracker(zone: TimeZone = TimeZone.UTC) = ReadingSessionTracker(9, clock, zone)

    private fun pass(duration: Duration) {
        now += duration
    }

    @Test
    fun visitWithoutPagesIsNotASession() {
        assertNull(tracker().finish())
    }

    @Test
    fun veryShortVisitIsNotASession() {
        val tracker = tracker()
        tracker.onPage(5)
        pass(9.seconds)

        assertNull(tracker.finish())
    }

    @Test
    fun countsDistinctPagesExceptStartPage() {
        val tracker = tracker()
        tracker.onPage(5)
        listOf(6, 7, 6, 5).forEach {
            pass(5.seconds)
            tracker.onPage(it)
        }

        val session = assertNotNull(tracker.finish())

        assertEquals(2, session.pagesRead)
        assertEquals(5, session.startPage)
        assertEquals(5, session.endPage)
        assertEquals(20, session.durationSeconds)
    }

    @Test
    fun idleGapCountsAsFiveMinutesOnly() {
        val tracker = tracker()
        tracker.onPage(5)
        pass(3.hours)
        tracker.onPage(6)
        pass(1.minutes)

        val session = assertNotNull(tracker.finish())

        assertEquals(360, session.durationSeconds)
        assertEquals(now, session.endedAt)
    }

    @Test
    fun localDateFollowsTimeZone() {
        val tracker = tracker(TimeZone.of("Asia/Jakarta"))
        tracker.onPage(1)
        pass(1.minutes)

        assertEquals(LocalDate(2026, 11, 2), assertNotNull(tracker.finish()).localDate)
    }

    @Test
    fun everySessionHasItsOwnId() {
        val sessions = List(2) {
            val tracker = tracker()
            tracker.onPage(1)
            pass(1.minutes)
            assertNotNull(tracker.finish())
        }

        assertNotEquals(sessions[0].clientSessionId, sessions[1].clientSessionId)
    }
}
