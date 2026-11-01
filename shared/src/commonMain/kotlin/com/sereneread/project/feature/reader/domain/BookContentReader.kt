package com.sereneread.project.feature.reader.domain

import com.sereneread.project.feature.library.domain.Book

sealed interface BookContent {
    /** Reflowable text (EPUB, TXT). */
    data class Document(val chapters: List<Chapter>) : BookContent

    /** Fixed-layout pages (PDF). Pixels are produced on demand by a page renderer. */
    data class Pages(val pages: List<PageSize>, val outline: List<OutlineEntry>) : BookContent

    data object Unsupported : BookContent
}

data class Chapter(
    val title: String,
    val paragraphs: List<Paragraph>,
)

data class Paragraph(
    val text: String,
    val isHeading: Boolean = false,
)

/** Page size in PDF points, already rotated for display. */
data class PageSize(val width: Float, val height: Float)

data class OutlineEntry(val title: String, val pageIndex: Int)

/** Reads a book file into displayable content. One implementation per platform. */
fun interface BookContentReader {
    suspend fun read(book: Book): BookContent
}
