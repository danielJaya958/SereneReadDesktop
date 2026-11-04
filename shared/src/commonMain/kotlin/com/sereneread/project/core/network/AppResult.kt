package com.sereneread.project.core.network

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    /** No usable connection: DNS, refused connection, or timeout. */
    data class Network(val cause: Throwable) : AppError

    /** The session is invalid. HttpClient has already expired it when a token was sent. */
    data class Unauthorized(val message: String?) : AppError
    data class Forbidden(val message: String?) : AppError
    data class NotFound(val message: String?) : AppError
    data class Conflict(val message: String?) : AppError
    data class Validation(val message: String?) : AppError
    data class Server(val status: Int, val message: String?) : AppError

    /** A response this client cannot handle, such as a body that does not match its DTO. */
    data class Unexpected(val cause: Throwable?, val status: Int? = null) : AppError
}

/** True when the same request can succeed later. Other errors will fail again, so resending is pointless. */
val AppError.isRetryable: Boolean
    get() = this is AppError.Network || this is AppError.Server || this is AppError.Unauthorized
