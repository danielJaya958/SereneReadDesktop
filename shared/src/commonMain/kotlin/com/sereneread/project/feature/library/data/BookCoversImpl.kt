package com.sereneread.project.feature.library.data

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntSize
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
            val bitmap = withContext(ioDispatcher) { response.readRawBytes().decodeToImageBitmap().shrinkTo(MAX_WIDTH_PX) }
            mutex.withLock { cache[thumbnailUrl] = bitmap }
            bitmap
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Server covers can be far larger than the 300dp poster. Scaling once on load keeps the cache small
     * and spares the GPU from resampling a full-size image on every animation frame.
     */
    private fun ImageBitmap.shrinkTo(maxWidth: Int): ImageBitmap {
        if (width <= maxWidth) return this
        val height = (height.toLong() * maxWidth / width).toInt()
        val target = ImageBitmap(maxWidth, height)
        Canvas(target).drawImageRect(
            image = this,
            dstSize = IntSize(maxWidth, height),
            paint = Paint().apply { filterQuality = FilterQuality.High },
        )
        return target
    }

    private companion object {
        /** The detail poster is 300dp wide; 600px stays sharp at 200% display scaling. */
        const val MAX_WIDTH_PX = 600
    }
}
