package com.sereneread.project.feature.reader.data

import com.sereneread.project.feature.library.domain.Book
import com.sereneread.project.feature.library.domain.BookFormat
import com.sereneread.project.feature.reader.domain.BookContent
import com.sereneread.project.feature.reader.domain.BookContentReader
import com.sereneread.project.feature.reader.domain.Chapter
import com.sereneread.project.feature.reader.domain.Paragraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

class FileBookContentReader(
    private val epubParser: EpubParser,
    private val pdfParser: PdfParser,
    private val ioDispatcher: CoroutineDispatcher,
) : BookContentReader {
    override suspend fun read(book: Book): BookContent = withContext(ioDispatcher) {
        val file = File(book.filePath)
        when (book.format) {
            BookFormat.EPUB -> BookContent.Document(epubParser.parse(file).chapters)
            BookFormat.TXT -> BookContent.Document(listOf(readTxt(file, book.title)))
            BookFormat.PDF -> pdfParser.parse(file)
        }
    }

    private fun readTxt(file: File, title: String) = Chapter(
        title = title,
        paragraphs = file.readText()
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Paragraph(it) },
    )
}
