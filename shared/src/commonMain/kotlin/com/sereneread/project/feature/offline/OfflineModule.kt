package com.sereneread.project.feature.offline

import com.sereneread.project.core.di.IoDispatcher
import com.sereneread.project.feature.library.data.RemoteBookRepositoryImpl
import com.sereneread.project.feature.offline.data.OfflineStore
import com.sereneread.project.feature.offline.data.OfflineSync
import com.sereneread.project.feature.stats.data.StatsRepositoryImpl
import org.koin.dsl.module

val offlineModule = module {
    single { OfflineStore(get(), get(IoDispatcher)) }
    // Plain clients of its own, so sending a queued page never loops back into the queue.
    single { OfflineSync(get(), RemoteBookRepositoryImpl(get()), StatsRepositoryImpl(get())) }
}
