package com.sereneread.project.feature.reader.data

import com.sereneread.project.feature.reader.domain.OutlineEntry
import com.sereneread.project.feature.reader.domain.PageSize
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PdfTest {

    private val tempDir = Files.createTempDirectory("pdf-test").toFile()
    private val parser = PdfParser()

    @AfterTest
    fun cleanUp() {
        tempDir.deleteRecursively()
    }

    @Test
    fun readsPageSizesWithRotation() {
        val pages = parser.parse(samplePdf()).pages

        assertEquals(
            listOf(
                PageSize(PDRectangle.A4.width, PDRectangle.A4.height),
                PageSize(PDRectangle.A4.height, PDRectangle.A4.width),
                PageSize(PDRectangle.A4.width, PDRectangle.A4.height),
            ),
            pages,
        )
    }

    @Test
    fun readsTopLevelOutlineSortedByPage() {
        val outline = parser.parse(samplePdf()).outline

        assertEquals(listOf(OutlineEntry("Pendahuluan", 0), OutlineEntry("Penutup", 2)), outline)
    }

    @Test
    fun readsDocumentInformation() {
        val metadata = parser.readMetadata(samplePdf())

        assertEquals(PdfMetadata(title = "Laporan", author = "Citra"), metadata)
    }

    @Test
    fun returnsNullForBlankMetadata() {
        val file = writePdf("blank.pdf") { doc ->
            doc.addPage(PDPage())
            doc.documentInformation.title = "  "
        }

        assertNull(parser.readMetadata(file).title)
    }

    @Test
    fun rejectsPasswordProtectedPdf() {
        val file = writePdf("locked.pdf") { doc ->
            doc.addPage(PDPage())
            doc.protect(StandardProtectionPolicy("owner", "user", AccessPermission()).apply { encryptionKeyLength = 128 })
        }

        assertFailsWith<PdfException> { parser.parse(file) }
    }

    @Test
    fun rendersPageAtRequestedWidth() {
        val file = samplePdf()
        val renderer = PdfPageRenderer(ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined)

        assertEquals(600, renderer.renderImage(file.path, 0, 600).width)
        // Rotated page: width follows the displayed (landscape) width.
        val landscape = renderer.renderImage(file.path, 1, 600)
        assertEquals(600, landscape.width)
        assertEquals((600 * PDRectangle.A4.width / PDRectangle.A4.height).toInt(), landscape.height, absoluteTolerance = 1)

        renderer.release(file.path)
        assertEquals(300, renderer.renderImage(file.path, 2, 300).width)
        renderer.release(file.path)
    }

    private fun assertEquals(expected: Int, actual: Int, absoluteTolerance: Int) {
        kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= absoluteTolerance, "expected $expected ± $absoluteTolerance, got $actual")
    }

    private fun samplePdf(): File = writePdf("sample.pdf") { doc ->
        val first = PDPage(PDRectangle.A4)
        val rotated = PDPage(PDRectangle.A4).apply { rotation = 90 }
        val last = PDPage(PDRectangle.A4)
        listOf(first, rotated, last).forEach(doc::addPage)

        val outline = PDDocumentOutline()
        doc.documentCatalog.documentOutline = outline
        // Added out of order on purpose; the parser sorts by page.
        outline.addLast(PDOutlineItem().apply { title = "Penutup"; setDestination(last) })
        outline.addLast(PDOutlineItem().apply { title = "Pendahuluan"; setDestination(first) })
        outline.addLast(PDOutlineItem().apply { title = "  "; setDestination(rotated) })

        doc.documentInformation.title = "Laporan"
        doc.documentInformation.author = " Citra "
    }

    private fun writePdf(name: String, build: (PDDocument) -> Unit): File {
        val file = File(tempDir, name)
        PDDocument().use { doc ->
            build(doc)
            doc.save(file)
        }
        return file
    }
}
