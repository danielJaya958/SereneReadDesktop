package com.sereneread.project.feature.library.data

import com.sereneread.project.core.network.ApiEnvelope
import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.safeApiCall
import com.sereneread.project.feature.library.domain.BookChanges
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook
import com.sereneread.project.feature.library.domain.RemoteBookPage
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.library.domain.UploadBook
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class BookDto(
    val id: Long,
    val title: String,
    val author: String,
    @SerialName("published_year") val publishedYear: Int,
    @SerialName("last_read_page") val lastReadPage: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
) {
    fun toDomain() = RemoteBook(
        id, title, author, publishedYear, lastReadPage, totalPages, isFavorite,
        thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
    )
}

@Serializable
private data class BookRequest(
    val title: String,
    val author: String,
    @SerialName("published_year") val publishedYear: Int,
)

@Serializable
private data class ProgressRequest(@SerialName("last_read_page") val lastReadPage: Int)

@Serializable
private data class FavoriteRequest(@SerialName("is_favorite") val isFavorite: Boolean)

class RemoteBookRepositoryImpl(private val client: HttpClient) : RemoteBookRepository {

    override suspend fun getBooks(page: Int, query: String, sort: BookSort): AppResult<RemoteBookPage> {
        val response = safeApiCall<ApiEnvelope<List<BookDto>>> {
            client.get("books") {
                parameter("page", page)
                parameter("limit", PAGE_SIZE)
                parameter("sort", sort.apiValue)
                if (query.isNotBlank()) parameter("q", query.trim())
            }
        }
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> AppResult.Success(
                RemoteBookPage(
                    books = response.data.data.orEmpty().map { it.toDomain() },
                    page = page,
                    hasNext = response.data.meta?.hasNext == true,
                ),
            )
        }
    }

    override suspend fun getBook(id: Long): AppResult<RemoteBook> =
        safeApiCall<ApiEnvelope<BookDto>> { client.get("books/$id") }.toBook()

    override suspend fun updateBook(id: Long, changes: BookChanges): AppResult<RemoteBook> =
        safeApiCall<ApiEnvelope<BookDto>> {
            client.put("books/$id") {
                contentType(ContentType.Application.Json)
                setBody(BookRequest(changes.title, changes.author, changes.publishedYear))
            }
        }.toBook()

    override suspend fun deleteBook(id: Long): AppResult<Unit> =
        safeApiCall { client.delete("books/$id") }

    override suspend fun updateProgress(id: Long, page: Int): AppResult<Unit> =
        safeApiCall {
            client.patch("books/$id/progress") {
                contentType(ContentType.Application.Json)
                setBody(ProgressRequest(page))
            }
        }

    override suspend fun setFavorite(id: Long, isFavorite: Boolean): AppResult<RemoteBook> =
        safeApiCall<ApiEnvelope<BookDto>> {
            client.put("books/$id/favorite") {
                contentType(ContentType.Application.Json)
                setBody(FavoriteRequest(isFavorite))
            }
        }.toBook()

    override suspend fun uploadBook(book: UploadBook, onProgress: (Float) -> Unit): AppResult<RemoteBook> {
        val path = Path(book.filePath)
        val size = SystemFileSystem.metadataOrNull(path)?.size ?: return invalid("File tidak ditemukan.")
        if (!book.filePath.endsWith(".pdf", ignoreCase = true)) return invalid("Format file harus PDF.")
        if (size > MAX_UPLOAD_BYTES) return invalid("Ukuran file maksimal 75MB.")

        return safeApiCall<ApiEnvelope<BookDto>> {
            client.post("books") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("title", book.title)
                            append("author", book.author)
                            append("published_year", book.publishedYear.toString())
                            append(
                                key = "pdf_file",
                                value = InputProvider(size) { SystemFileSystem.source(path).buffered() },
                                headers = PDF_PART_HEADERS,
                            )
                        },
                    ),
                )
                onUpload { sent, total -> if (total != null && total > 0) onProgress(sent.toFloat() / total) }
            }
        }.toBook()
    }

    private fun AppResult<ApiEnvelope<BookDto>>.toBook(): AppResult<RemoteBook> = when (this) {
        is AppResult.Failure -> this
        is AppResult.Success -> data.data?.let { AppResult.Success(it.toDomain()) }
            ?: AppResult.Failure(AppError.Unexpected(IllegalStateException("Response has no book")))
    }

    /** Year sorts newest first, because that is what a reader scanning by year usually wants. */
    private val BookSort.apiValue: String
        get() = when (this) {
            BookSort.NEWEST -> "-created_at"
            BookSort.TITLE -> "title"
            BookSort.AUTHOR -> "author"
            BookSort.PUBLISHED_YEAR -> "-published_year"
        }

    private fun invalid(message: String) = AppResult.Failure(AppError.Validation(message))

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_UPLOAD_BYTES = 75L * 1024 * 1024

        // The server renames the file and only checks its extension, so the local name stays private.
        val PDF_PART_HEADERS = Headers.build {
            append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
            append(HttpHeaders.ContentDisposition, "filename=\"book.pdf\"")
        }
    }
}
