package com.sereneread.project.feature.offline.data

import com.sereneread.project.core.network.AppResult
import com.sereneread.project.feature.stats.domain.ReadingSession
import com.sereneread.project.feature.stats.domain.StatsRepository
import com.sereneread.project.feature.stats.domain.StatsSummary
import kotlinx.datetime.LocalDate

/** Queues sessions until the server confirms them. Resending is safe because each has a client id. */
class OfflineStatsRepository(
    private val remote: StatsRepository,
    private val store: OfflineStore,
    private val sync: OfflineSync,
) : StatsRepository by remote {

    override suspend fun saveSession(session: ReadingSession): AppResult<Unit> {
        store.queueSession(session)
        return sync.sendSession(session)
    }

    /** Sends what is queued first, so the summary includes reading done while offline. */
    override suspend fun getSummary(today: LocalDate): AppResult<StatsSummary> {
        sync.flush()
        return remote.getSummary(today)
    }
}
