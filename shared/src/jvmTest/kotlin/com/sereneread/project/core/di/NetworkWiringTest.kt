package com.sereneread.project.core.di

import com.sereneread.project.core.session.SessionManager
import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class NetworkWiringTest {

    @Test
    fun resolvesHttpClientAndSessionManager() {
        val koin = koinApplication { modules(platformModule, coreModule) }.koin

        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<SessionManager>())
    }
}
