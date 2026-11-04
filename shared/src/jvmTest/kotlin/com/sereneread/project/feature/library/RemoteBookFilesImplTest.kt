package com.sereneread.project.feature.library

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.createHttpClient
import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.library.data.RemoteBookFilesImpl
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteBookFilesImplTest {

    private val directory = Files.createTempDirectory("sereneread-books").toFile()
    private var requestCount = 0
    private var requestedPath = ""

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun files(
        body: ByteArray,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentLength: Long = body.size.toLong(),
    ): RemoteBookFilesImpl {
        val engine = MockEngine { request ->
            requestCount++
            requestedPath = request.url.encodedPath
            respond(
                body,
                status,
                headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Pdf.toString()),
                    HttpHeaders.ContentLength to listOf(contentLength.toString()),
                ),
            )
        }
        val client = createHttpClient(engine, SessionManager(FakeTokenStore()))
        return RemoteBookFilesImpl(client, Path(directory.path), Dispatchers.IO)
    }

    @Test
    fun downloadsOnceThenUsesCache() = runTest {
        val files = files("%PDF-data".encodeToByteArray())
        val progress = mutableListOf<Float>()

        val first = assertIs<AppResult.Success<String>>(files.getOrDownload(9, progress::add))
        val second = files.getOrDownload(9) {}

        assertEquals("%PDF-data", File(first.data).readText())
        assertEquals(first, second)
        assertEquals(1, requestCount)
        assertEquals("/api/v1/books/9/file", requestedPath)
        assertEquals(1f, progress.last())
    }

    @Test
    fun serverErrorLeavesNoFile() = runTest {
        val body = """{"status":false,"message":"File PDF tidak ditemukan"}""".encodeToByteArray()

        val result = files(body, HttpStatusCode.NotFound).getOrDownload(9) {}

        assertEquals(AppResult.Failure(AppError.NotFound("File PDF tidak ditemukan")), result)
        assertTrue(directory.list().orEmpty().isEmpty())
    }

    @Test
    fun incompleteDownloadIsNotKept() = runTest {
        val result = files("%PDF".encodeToByteArray(), contentLength = 100).getOrDownload(9) {}

        val failure = assertIs<AppResult.Failure>(result)
        assertIs<AppError.Network>(failure.error)
        assertTrue(directory.list().orEmpty().isEmpty())
    }

    @Test
    fun deleteRemovesCachedFile() = runTest {
        val files = files("%PDF".encodeToByteArray())
        val path = assertIs<AppResult.Success<String>>(files.getOrDownload(9) {}).data

        files.delete(9)

        assertFalse(File(path).exists())
    }

    @Test
    fun deleteAllRemovesEveryFile() = runTest {
        val files = files("%PDF".encodeToByteArray())
        files.getOrDownload(9) {}
        files.getOrDownload(10) {}
        assertTrue(files.isDownloaded(9))

        files.deleteAll()

        assertFalse(files.isDownloaded(9))
        assertTrue(directory.list().orEmpty().isEmpty())
    }

    @Test
    fun deleteAllAcceptsMissingDirectory() = runTest {
        directory.deleteRecursively()

        files(ByteArray(0)).deleteAll()
    }
}
