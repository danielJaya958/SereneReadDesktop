package com.sereneread.project.feature.offline.data

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.isRetryable
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsRepository
import com.sereneread.project.feature.stats.domain.StatsSummary
import kotlinx.datetime.LocalDate

/** Queues sessions until the server confirms them. Resending is safe because each has a client id. */
class OfflineStatsRepository(
    private val remote: StatsRepository,
    private val store: OfflineStore,
) : StatsRepository by remote {

    override suspend fun saveSession(session: ReadingSession): AppResult<Unit> {
        store.queueSession(session)
        return send(session)
    }

    override suspend fun getSummary(today: LocalDate): AppResult<StatsSummary> {
        for (session in store.pendingSessions()) {
            val result = send(session)
            if (result is AppResult.Failure && result.error is AppError.Network) break
        }
        return remote.getSummary(today)
    }

    private suspend fun send(session: ReadingSession): AppResult<Unit> {
        val result = remote.saveSession(session)
        if (result is AppResult.Success || (result is AppResult.Failure && !result.error.isRetryable)) {
            store.removeSession(session.clientSessionId)
        }
        return result
    }
}
