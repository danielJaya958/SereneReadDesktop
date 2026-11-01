package com.sereneread.project.core.di

import com.sereneread.project.core.database.SereneReadDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.qualifier.named
import org.koin.dsl.module

val IoDispatcher = named("io")

val coreModule = module {
    single { SereneReadDatabase(get()) }
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
}
