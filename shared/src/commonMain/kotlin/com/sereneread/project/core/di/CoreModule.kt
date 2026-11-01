package com.sereneread.project.core.di

import com.sereneread.project.core.database.SereneReadDatabase
import com.sereneread.project.core.network.createHttpClient
import com.sereneread.project.core.session.SessionManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.qualifier.named
import org.koin.dsl.module

val IoDispatcher = named("io")

val coreModule = module {
    single { SereneReadDatabase(get()) }
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single { SessionManager(get()) }
    single<HttpClient> { createHttpClient(get(), get()) }
}
