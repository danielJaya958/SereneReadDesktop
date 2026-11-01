package com.sereneread.project.feature.reader.data

import com.sereneread.project.feature.reader.domain.BookContent
import com.sereneread.project.feature.reader.domain.OutlineEntry
import com.sereneread.project.feature.reader.domain.PageSize
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.IOException

data class PdfMetadata(
    val title: String?,
    val author: String?,
)

class PdfException(message: String, cause: Throwable? = null) : IOException(message, cause)

class PdfParser {

    fun parse(file: File): BookContent.Pages = load(file).use { doc ->
        if (doc.numberOfPages == 0) throw PdfException("PDF tidak memiliki halaman.")
        BookContent.Pages(
            pages = doc.pages.map { it.displaySize() },
            outline = doc.topLevelOutline(),
        )
    }

    fun readMetadata(file: File): PdfMetadata = load(file).use { doc ->
        val info = doc.documentInformation
        PdfMetadata(
            title = info.title?.trim()?.takeIf { it.isNotEmpty() },
            author = info.author?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun PDPage.displaySize(): PageSize {
        val box = cropBox
        val rotated = rotation % 180 != 0
        return if (rotated) PageSize(box.height, box.width) else PageSize(box.width, box.height)
    }

    /** Only top-level bookmarks; nested ones would make the menu too long to scan. */
    private fun PDDocument.topLevelOutline(): List<OutlineEntry> {
        val root = documentCatalog.documentOutline ?: return emptyList()
        return root.children().mapNotNull { item ->
            val title = item.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val page = runCatching { item.findDestinationPage(this) }.getOrNull() ?: return@mapNotNull null
            val index = pages.indexOf(page).takeIf { it >= 0 } ?: return@mapNotNull null
            OutlineEntry(title, index)
        }.sortedBy { it.pageIndex }
    }

    private fun load(file: File): PDDocument = try {
        Loader.loadPDF(file)
    } catch (e: InvalidPasswordException) {
        throw PdfException("PDF ini dilindungi password dan tidak bisa dibuka.", e)
    }
}
