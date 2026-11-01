package com.sereneread.project.feature.auth.data

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.safeApiCall
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.auth.domain.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
private data class LoginRequest(val email: String, val password: String)

@Serializable
private data class RegisterRequest(val username: String, val email: String, val password: String)

class AuthRepositoryImpl(
    private val client: HttpClient,
    private val sessionManager: SessionManager,
) : AuthRepository {

    override suspend fun login(email: String, password: String): AppResult<Unit> {
        val response = safeApiCall<JsonObject> {
            client.post("login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
        }
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> startSession(response.data)
        }
    }

    override suspend fun register(username: String, email: String, password: String): AppResult<Unit> =
        safeApiCall {
            client.post("register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(username, email, password))
            }
        }

    private suspend fun startSession(body: JsonObject): AppResult<Unit> {
        val token = body.findToken()
            ?: return AppResult.Failure(AppError.Unexpected(IllegalStateException("Login response has no token")))
        return try {
            sessionManager.signIn(token)
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unexpected(e))
        }
    }

    /** The API docs leave the login response untyped, so this accepts the common token locations. */
    private fun JsonObject.findToken(): String? =
        listOfNotNull(this, this["data"] as? JsonObject).firstNotNullOfOrNull { container ->
            TOKEN_FIELDS.firstNotNullOfOrNull { field ->
                (container[field] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            }
        }

    private companion object {
        val TOKEN_FIELDS = listOf("token", "access_token", "accessToken")
    }
}
