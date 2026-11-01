package com.sereneread.project.core.network

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Runs a request and keeps Ktor and serialization exceptions out of the repositories. */
suspend inline fun <reified T> safeApiCall(block: () -> HttpResponse): AppResult<T> =
    try {
        val response = block()
        if (response.status.isSuccess()) {
            AppResult.Success(response.body<T>())
        } else {
            AppResult.Failure(response.toAppError())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        AppResult.Failure(AppError.Network(e))
    } catch (e: ContentConvertException) {
        AppResult.Failure(AppError.Unexpected(e))
    } catch (e: SerializationException) {
        AppResult.Failure(AppError.Unexpected(e))
    }

suspend fun HttpResponse.toAppError(): AppError {
    val message = readErrorMessage()
    return when (val code = status.value) {
        400, 422 -> AppError.Validation(message)
        401 -> AppError.Unauthorized(message)
        403 -> AppError.Forbidden(message)
        404 -> AppError.NotFound(message)
        409 -> AppError.Conflict(message)
        in 500..599 -> AppError.Server(code, message)
        else -> AppError.Unexpected(cause = null, status = code)
    }
}

/**
 * The API docs do not define the error body, so this is tolerant: it looks for the common
 * "error" and "message" fields and returns null for anything else, including non-JSON bodies.
 */
private suspend fun HttpResponse.readErrorMessage(): String? {
    val text = runCatching { bodyAsText() }.getOrNull() ?: return null
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    return ERROR_FIELDS.firstNotNullOfOrNull { field ->
        (root[field] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}

private val ERROR_FIELDS = listOf("error", "message")
