package com.sereneread.project.feature.library.data

import androidx.compose.ui.graphics.ImageBitmap
import com.sereneread.project.feature.library.presentation.BookCovers
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Keeps recent covers in memory only, so covers need a network after the app restarts. */
class BookCoversImpl(
    private val client: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val cacheSize: Int = 100,
) : BookCovers {

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, ImageBitmap>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > cacheSize
    }

    override suspend fun load(thumbnailUrl: String): ImageBitmap? {
        mutex.withLock { cache[thumbnailUrl] }?.let { return it }
        return try {
            // The leading slash resolves against the server host, not the API base path.
            val response = client.get(thumbnailUrl)
            if (!response.status.isSuccess()) return null
            val bitmap = withContext(ioDispatcher) { response.readRawBytes().decodeToImageBitmap() }
            mutex.withLock { cache[thumbnailUrl] = bitmap }
            bitmap
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
