package com.sereneread.project.core.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionState {
    data object Loading : SessionState
    data class Unauthenticated(val expired: Boolean = false) : SessionState
    data object Authenticated : SessionState
}

class SessionManager(private val tokenStore: TokenStore) {

    private val mutex = Mutex()
    private var token: String? = null

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    fun currentToken(): String? = token

    /** Call once at startup. Unreadable storage counts as signed out, so the app can still start. */
    suspend fun restore() = mutex.withLock {
        token = try {
            tokenStore.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.value = if (token != null) SessionState.Authenticated else SessionState.Unauthenticated()
    }

    suspend fun signIn(newToken: String) = mutex.withLock {
        tokenStore.save(newToken)
        token = newToken
        _state.value = SessionState.Authenticated
    }

    suspend fun signOut() = mutex.withLock {
        clear(SessionState.Unauthenticated())
    }

    /** Ignores a stale 401: a late response for an old token must not end a newer session. */
    suspend fun expire(rejectedToken: String) = mutex.withLock {
        if (rejectedToken == token) clear(SessionState.Unauthenticated(expired = true))
    }

    private suspend fun clear(newState: SessionState) {
        // Memory first: a storage failure must never leave the app using a dead session.
        token = null
        _state.value = newState
        try {
            tokenStore.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The stored token is rejected with 401 on next start, which clears it again.
        }
    }
}
