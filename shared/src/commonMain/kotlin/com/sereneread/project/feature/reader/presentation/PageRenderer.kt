package com.sereneread.project.feature.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Renders fixed-layout pages to bitmaps. Lives in presentation because its output is a UI type.
 * Implementations may keep the file open between calls; [release] closes it.
 */
interface PageRenderer {
    suspend fun render(filePath: String, pageIndex: Int, widthPx: Int): ImageBitmap
    fun release(filePath: String)
}
