package com.sereneread.project.feature.library.data

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.network.toAppError
import com.sereneread.project.feature.library.domain.RemoteBookFiles
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class RemoteBookFilesImpl(
    private val client: HttpClient,
    private val directory: Path,
    private val ioDispatcher: CoroutineDispatcher,
) : RemoteBookFiles {

    override suspend fun getOrDownload(bookId: Long, onProgress: (Float) -> Unit): AppResult<String> =
        withContext(ioDispatcher) {
            val target = fileFor(bookId)
            if (SystemFileSystem.exists(target)) return@withContext AppResult.Success(target.toString())

            // Only a complete file reaches the target path, so an interrupted download is never reused.
            val partial = Path(directory, "$bookId.pdf.part")
            try {
                SystemFileSystem.createDirectories(directory)
                client.prepareGet("books/$bookId/file").execute { response ->
                    if (response.status.isSuccess()) {
                        save(response, partial, onProgress)
                        SystemFileSystem.atomicMove(partial, target)
                        AppResult.Success(target.toString())
                    } else {
                        AppResult.Failure(response.toAppError())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                AppResult.Failure(AppError.Network(e))
            } finally {
                SystemFileSystem.delete(partial, mustExist = false)
            }
        }

    override suspend fun delete(bookId: Long) {
        withContext(ioDispatcher) { SystemFileSystem.delete(fileFor(bookId), mustExist = false) }
    }

    override suspend fun isDownloaded(bookId: Long): Boolean =
        withContext(ioDispatcher) { SystemFileSystem.exists(fileFor(bookId)) }

    override suspend fun downloadedPath(bookId: Long): String? =
        withContext(ioDispatcher) { fileFor(bookId).takeIf { SystemFileSystem.exists(it) }?.toString() }

    override suspend fun deleteAll() {
        withContext(ioDispatcher) {
            if (SystemFileSystem.exists(directory)) {
                SystemFileSystem.list(directory).forEach { SystemFileSystem.delete(it, mustExist = false) }
            }
        }
    }

    private suspend fun save(response: HttpResponse, path: Path, onProgress: (Float) -> Unit) {
        val total = response.contentLength()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(BUFFER_BYTES)
        var received = 0L

        SystemFileSystem.sink(path).buffered().use { sink ->
            while (true) {
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                sink.write(buffer, 0, count)
                received += count
                if (total != null && total > 0) onProgress(received.toFloat() / total)
            }
        }
        if (total != null && received != total) throw IOException("Download ended early")
    }

    private fun fileFor(bookId: Long) = Path(directory, "$bookId.pdf")

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
