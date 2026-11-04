package com.sereneread.project.feature.offline

import com.sereneread.project.core.di.IoDispatcher
import com.sereneread.project.feature.offline.data.OfflineStore
import org.koin.dsl.module

val offlineModule = module {
    single { OfflineStore(get(), get(IoDispatcher)) }
}
