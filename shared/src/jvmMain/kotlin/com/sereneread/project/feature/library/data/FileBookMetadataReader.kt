package com.sereneread.project.feature.library.data

import com.sereneread.project.feature.library.domain.BookFormat
import com.sereneread.project.feature.library.domain.BookMetadata
import com.sereneread.project.feature.library.domain.BookMetadataReader
import com.sereneread.project.feature.reader.data.EpubParser
import com.sereneread.project.feature.reader.data.PdfParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileBookMetadataReader(
    private val epubParser: EpubParser,
    private val pdfParser: PdfParser,
    private val ioDispatcher: CoroutineDispatcher,
) : BookMetadataReader {
    override suspend fun read(filePath: String, format: BookFormat): BookMetadata? = withContext(ioDispatcher) {
        val file = File(filePath)
        try {
            when (format) {
                BookFormat.EPUB -> epubParser.readMetadata(file).let {
                    BookMetadata(title = it.title, author = it.authors.joinToString(", ").ifEmpty { null })
                }
                BookFormat.PDF -> pdfParser.readMetadata(file).let { BookMetadata(title = it.title, author = it.author) }
                BookFormat.TXT -> null
            }
        } catch (e: IOException) {
            // Broken metadata should not block import; the title falls back to the file name.
            null
        }
    }
}
