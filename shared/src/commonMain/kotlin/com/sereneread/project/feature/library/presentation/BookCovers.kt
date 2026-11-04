package com.sereneread.project.feature.library.presentation

import androidx.compose.ui.graphics.ImageBitmap

/** Loads server book covers. Lives in presentation because its output is a UI type. */
interface BookCovers {
    /** Returns null when the cover cannot be loaded, so the card shows its placeholder. */
    suspend fun load(thumbnailUrl: String): ImageBitmap?
}
