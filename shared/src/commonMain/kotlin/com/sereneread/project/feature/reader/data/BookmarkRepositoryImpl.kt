package com.sereneread.project.feature.reader.data

import com.sereneread.project.core.network.ApiEnvelope
import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.safeApiCall
import com.sereneread.project.feature.reader.domain.Bookmark
import com.sereneread.project.feature.reader.domain.BookmarkRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class BookmarkDto(
    val id: Long,
    val page: Int,
    val note: String? = null,
) {
    fun toDomain() = Bookmark(id, page, note)
}

@Serializable
private data class BookmarkRequest(
    val page: Int,
    val note: String?,
)

class BookmarkRepositoryImpl(private val client: HttpClient) : BookmarkRepository {

    override suspend fun getBookmarks(bookId: Long): AppResult<List<Bookmark>> =
        when (val response = safeApiCall<ApiEnvelope<List<BookmarkDto>>> { client.get("books/$bookId/bookmarks") }) {
            is AppResult.Failure -> response
            is AppResult.Success -> AppResult.Success(response.data.data.orEmpty().map { it.toDomain() })
        }

    override suspend fun saveBookmark(bookId: Long, page: Int, note: String?): AppResult<Bookmark> {
        val response = safeApiCall<ApiEnvelope<BookmarkDto>> {
            client.post("books/$bookId/bookmarks") {
                contentType(ContentType.Application.Json)
                setBody(BookmarkRequest(page, note))
            }
        }
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> response.data.data?.let { AppResult.Success(it.toDomain()) }
                ?: AppResult.Failure(AppError.Unexpected(IllegalStateException("Response has no bookmark")))
        }
    }

    override suspend fun deleteBookmark(bookId: Long, bookmarkId: Long): AppResult<Unit> =
        safeApiCall { client.delete("books/$bookId/bookmarks/$bookmarkId") }
}
