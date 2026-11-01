package com.sereneread.project.feature.reader.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sereneread.project.feature.reader.presentation.PageRenderer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.File

/**
 * Keeps one PDF open at a time. PDFBox documents are not thread-safe, so every render is serialized.
 * A small LRU cache avoids re-rendering pages that scroll back into view.
 */
class PdfPageRenderer(
    private val ioDispatcher: CoroutineDispatcher,
    private val cacheSize: Int = 8,
) : PageRenderer {

    private val mutex = Mutex()
    private var openPath: String? = null
    private var document: PDDocument? = null
    private var renderer: PDFRenderer? = null

    private data class CacheKey(val filePath: String, val pageIndex: Int, val widthPx: Int)

    private val cache = object : LinkedHashMap<CacheKey, ImageBitmap>(cacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, ImageBitmap>) = size > cacheSize
    }

    override suspend fun render(filePath: String, pageIndex: Int, widthPx: Int): ImageBitmap =
        withContext(ioDispatcher) {
            mutex.withLock {
                val key = CacheKey(filePath, pageIndex, widthPx)
                cache[key] ?: renderImage(filePath, pageIndex, widthPx).toComposeImageBitmap().also { cache[key] = it }
            }
        }

    /** Caller must hold [mutex]. */
    internal fun renderImage(filePath: String, pageIndex: Int, widthPx: Int): BufferedImage {
        val pdfRenderer = rendererFor(filePath)
        val page = document!!.getPage(pageIndex)
        val pageWidthPt = if (page.rotation % 180 != 0) page.cropBox.height else page.cropBox.width
        val scale = widthPx / pageWidthPt
        return pdfRenderer.renderImage(pageIndex, scale, ImageType.RGB)
    }

    override fun release(filePath: String) {
        // Non-suspending so it can run from ViewModel.onCleared; tryLock skips if a render is in flight,
        // in which case the document is closed on the next open or release.
        if (!mutex.tryLock()) return
        try {
            if (openPath == filePath) closeDocument()
            cache.keys.removeAll { it.filePath == filePath }
        } finally {
            mutex.unlock()
        }
    }

    private fun rendererFor(filePath: String): PDFRenderer {
        if (openPath != filePath) {
            closeDocument()
            val doc = Loader.loadPDF(File(filePath))
            document = doc
            renderer = PDFRenderer(doc)
            openPath = filePath
        }
        return renderer!!
    }

    private fun closeDocument() {
        document?.close()
        document = null
        renderer = null
        openPath = null
    }
}
