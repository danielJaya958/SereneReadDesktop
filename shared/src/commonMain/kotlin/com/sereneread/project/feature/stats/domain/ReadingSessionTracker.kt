package com.sereneread.project.feature.stats.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Measures one reader visit. A long gap between page changes counts as [MAX_PAGE_GAP], so idle time is left out. */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class ReadingSessionTracker(
    private val bookId: Long,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    private var startedAt: Instant? = null
    private var lastActivity: Instant? = null
    private var active = Duration.ZERO
    private var startPage = 0
    private var endPage = 0
    private val visitedPages = mutableSetOf<Int>()

    fun onPage(page: Int) {
        val now = clock.now()
        if (startedAt == null) {
            startedAt = now
            startPage = page
        } else {
            addActiveTime(now)
            visitedPages += page
        }
        lastActivity = now
        endPage = page
    }

    /** Returns null when nothing worth counting happened. A second call also returns null. */
    fun finish(): ReadingSession? {
        val started = startedAt ?: return null
        val now = clock.now()
        addActiveTime(now)
        startedAt = null
        if (active < MIN_SESSION) return null

        return ReadingSession(
            clientSessionId = Uuid.random().toString(),
            bookId = bookId,
            startedAt = started,
            endedAt = now,
            localDate = started.toLocalDateTime(timeZone).date,
            startPage = startPage,
            endPage = endPage,
            pagesRead = (visitedPages - startPage).size,
            durationSeconds = active.coerceAtMost(MAX_SESSION).inWholeSeconds.toInt(),
        )
    }

    private fun addActiveTime(now: Instant) {
        lastActivity?.let { active += (now - it).coerceAtMost(MAX_PAGE_GAP) }
    }

    private companion object {
        val MIN_SESSION = 10.seconds
        val MAX_PAGE_GAP = 5.minutes
        val MAX_SESSION = 12.hours
    }
}
