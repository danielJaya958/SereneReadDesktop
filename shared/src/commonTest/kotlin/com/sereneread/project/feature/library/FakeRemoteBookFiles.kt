package com.sereneread.project.feature.library

import com.sereneread.project.core.network.AppResult
import com.sereneread.project.feature.library.domain.RemoteBookFiles

class FakeRemoteBookFiles : RemoteBookFiles {
    var result: AppResult<String> = AppResult.Success("/cache/9.pdf")
    val deleted = mutableListOf<Long>()
    val downloaded = mutableSetOf<Long>()
    var deleteAllCalls = 0

    override suspend fun getOrDownload(bookId: Long, onProgress: (Float) -> Unit): AppResult<String> {
        onProgress(0.5f)
        if (result is AppResult.Success) downloaded += bookId
        return result
    }

    override suspend fun isDownloaded(bookId: Long) = bookId in downloaded

    override suspend fun downloadedPath(bookId: Long) = if (bookId in downloaded) "/cache/$bookId.pdf" else null

    override suspend fun delete(bookId: Long) {
        deleted += bookId
        downloaded -= bookId
    }

    override suspend fun deleteAll() {
        downloaded.clear()
        deleteAllCalls++
    }
}
