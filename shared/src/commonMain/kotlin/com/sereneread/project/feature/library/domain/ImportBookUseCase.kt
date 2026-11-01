package com.sereneread.project.feature.library.domain

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface ImportResult {
    data class Imported(val bookId: Long) : ImportResult
    data class AlreadyExists(val bookId: Long) : ImportResult
    data object UnsupportedFormat : ImportResult
}

@OptIn(ExperimentalTime::class)
class ImportBookUseCase(
    private val repository: BookRepository,
    private val metadataReader: BookMetadataReader,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend operator fun invoke(filePath: String): ImportResult {
        val format = BookFormat.fromPath(filePath) ?: return ImportResult.UnsupportedFormat

        repository.findByPath(filePath)?.let { return ImportResult.AlreadyExists(it.id) }

        val metadata = metadataReader.read(filePath, format)
        val id = repository.addBook(
            title = metadata?.title?.trim()?.takeIf { it.isNotEmpty() } ?: titleFromPath(filePath),
            author = metadata?.author?.trim()?.takeIf { it.isNotEmpty() },
            filePath = filePath,
            format = format,
            addedAt = now(),
        )
        return ImportResult.Imported(id)
    }

    private fun titleFromPath(filePath: String): String =
        filePath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .replace('_', ' ')
            .trim()
}
