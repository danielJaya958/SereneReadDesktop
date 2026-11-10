package com.sereneread.project.feature.offline.data

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.isRetryable
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sends reading progress and sessions that were saved while offline. Anything queued stays in the store
 * until the server confirms it, so closing the app or losing the connection never drops it.
 */
class OfflineSync(
    private val store: OfflineStore,
    private val books: RemoteBookRepository,
    private val stats: StatsRepository,
) {
    /** Progress pages and reading sessions still waiting for the server. */
    val pendingCount: Flow<Long> = store.pendingCount()

    suspend fun sendProgress(bookId: Long, page: Int): AppResult<Unit> {
        val result = books.updateProgress(bookId, page)
        if (result.isSettled()) store.removeProgress(bookId, page)
        return result
    }

    suspend fun sendSession(session: ReadingSession): AppResult<Unit> {
        val result = stats.saveSession(session)
        if (result.isSettled()) store.removeSession(session.clientSessionId)
        return result
    }

    /** Sends everything queued. Stops at the first network failure, because the rest would fail too. */
    suspend fun flush() {
        for ((bookId, page) in store.pendingProgress()) {
            if (sendProgress(bookId, page).isNetworkFailure()) return
        }
        for (session in store.pendingSessions()) {
            if (sendSession(session).isNetworkFailure()) return
        }
    }

    /** Retries on a fixed interval for as long as the caller stays active, such as while signed in. */
    suspend fun keepSending(interval: Duration = 30.seconds) {
        while (true) {
            flush()
            delay(interval)
        }
    }

    /** Confirmed, or rejected for a reason a retry cannot fix, such as a deleted book. */
    private fun AppResult<Unit>.isSettled() =
        this is AppResult.Success || (this is AppResult.Failure && !error.isRetryable)

    private fun AppResult<Unit>.isNetworkFailure() = this is AppResult.Failure && error is AppError.Network
}
