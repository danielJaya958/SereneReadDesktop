package com.sereneread.project.feature.stats

import com.sereneread.project.feature.offline.data.OfflineStatsRepository
import com.sereneread.project.feature.stats.data.StatsRepositoryImpl
import com.sereneread.project.feature.stats.domain.StatsRepository
import com.sereneread.project.feature.stats.presentation.StatsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val statsModule = module {
    single<StatsRepository> { OfflineStatsRepository(StatsRepositoryImpl(get()), get()) }
    viewModel { StatsViewModel(get()) }
}
