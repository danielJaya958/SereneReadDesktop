package com.sereneread.project.core.network

import com.sereneread.project.core.config.AppConfig
import com.sereneread.project.core.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val BEARER_PREFIX = "Bearer "

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * There is deliberately no request timeout: PDF uploads reach 75MB. The socket timeout still
 * catches a stalled connection. Do not add a logging plugin that prints Authorization headers.
 */
fun createHttpClient(engine: HttpClientEngine, sessionManager: SessionManager): HttpClient =
    HttpClient(engine) {
        expectSuccess = false

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) { json(ApiJson) }
        install(sessionAuth(sessionManager))

        defaultRequest { url(AppConfig.API_BASE_URL) }
    }

/** Adds the bearer token, and expires the session when the server rejects that token. */
private fun sessionAuth(sessionManager: SessionManager) = createClientPlugin("SessionAuth") {
    onRequest { request, _ ->
        sessionManager.currentToken()?.let { request.bearerAuth(it) }
    }
    onResponse { response ->
        if (response.status == HttpStatusCode.Unauthorized) {
            // A login with a wrong password also returns 401, but it sends no token.
            val sentToken = response.call.request.headers[HttpHeaders.Authorization]?.removePrefix(BEARER_PREFIX)
            if (sentToken != null) sessionManager.expire(sentToken)
        }
    }
}
