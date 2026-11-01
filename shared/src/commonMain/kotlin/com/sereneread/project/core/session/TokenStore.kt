package com.sereneread.project.core.session

/** Persists the access token. Implementations must use OS-protected storage, never plain text. */
interface TokenStore {
    suspend fun read(): String?
    suspend fun save(token: String)
    suspend fun clear()
}
