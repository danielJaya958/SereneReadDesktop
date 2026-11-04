package com.sereneread.project.feature.library.domain

import com.sereneread.project.core.network.AppResult

/** Keeps downloaded server PDFs on disk. A book's file never changes, so a cached copy stays valid. */
interface RemoteBookFiles {
    /** Returns the local path, downloading first when needed. [onProgress] receives 0 to 1. */
    suspend fun getOrDownload(bookId: Long, onProgress: (Float) -> Unit): AppResult<String>

    suspend fun isDownloaded(bookId: Long): Boolean
    suspend fun delete(bookId: Long)
    suspend fun deleteAll()
}
