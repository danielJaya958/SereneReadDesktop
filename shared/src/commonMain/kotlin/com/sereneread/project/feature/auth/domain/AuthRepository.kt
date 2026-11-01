package com.sereneread.project.feature.auth.domain

import com.sereneread.project.core.network.AppResult

interface AuthRepository {
    /** Signs in and starts the session. */
    suspend fun login(email: String, password: String): AppResult<Unit>

    /** Creates the account. It does not start a session. */
    suspend fun register(username: String, email: String, password: String): AppResult<Unit>
}
