package com.sereneread.project.feature.reader.data

import com.sereneread.project.feature.reader.domain.Paragraph
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EpubParserTest {

    private val parser = EpubParser()
    private val tempDir = Files.createTempDirectory("epub-test").toFile()

    @AfterTest
    fun cleanUp() {
        tempDir.deleteRecursively()
    }

    @Test
    fun readsTitleAndChaptersInSpineOrder() {
        val book = parser.parse(sampleEpub())

        assertEquals("Buku Contoh", book.metadata.title)
        assertEquals(listOf("Bab Pertama", "Bab 2"), book.chapters.map { it.title })
    }

    @Test
    fun readsMetadataWithAllCreators() {
        val metadata = parser.readMetadata(sampleEpub())

        assertEquals(EpubMetadata(title = "Buku Contoh", authors = listOf("Ani", "Budi")), metadata)
    }

    @Test
    fun readsMetadataEvenWhenContentIsEncrypted() {
        val metadata = parser.readMetadata(sampleEpub(extra = mapOf("META-INF/encryption.xml" to ENCRYPTION_XML)))

        assertEquals("Buku Contoh", metadata.title)
    }

    @Test
    fun skipsChaptersWithoutText() {
        val book = parser.parse(sampleEpub())

        assertTrue(book.chapters.none { it.title.contains("cover", ignoreCase = true) })
    }

    @Test
    fun extractsParagraphsWithoutDuplicatingNestedBlocks() {
        val chapter = parser.parse(sampleEpub()).chapters.first()

        assertEquals(
            listOf(
                Paragraph("Bab Pertama", isHeading = true),
                Paragraph("Halo dunia."),
                Paragraph("Kutipan di dalam blockquote."),
            ),
            chapter.paragraphs,
        )
    }

    @Test
    fun resolvesPercentEncodedHrefInSubfolder() {
        val chapter = parser.parse(sampleEpub()).chapters[1]

        assertEquals(listOf(Paragraph("Teks bab kedua.")), chapter.paragraphs)
    }

    @Test
    fun rejectsDrmProtectedContent() {
        val file = sampleEpub(extra = mapOf("META-INF/encryption.xml" to ENCRYPTION_XML))

        assertFailsWith<EpubException> { parser.parse(file) }
    }

    @Test
    fun rejectsArchiveWithoutContainer() {
        val file = writeZip("broken.epub", mapOf("mimetype" to "application/epub+zip"))

        assertFailsWith<EpubException> { parser.parse(file) }
    }

    private fun sampleEpub(extra: Map<String, String> = emptyMap()): File = writeZip(
        "sample.epub",
        mapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Buku Contoh</dc:title>
                    <dc:creator>Ani</dc:creator>
                    <dc:creator> Budi </dc:creator>
                  </metadata>
                  <manifest>
                    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch2" href="text/bab%202.xhtml#start" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="cover"/>
                    <itemref idref="ch1"/>
                    <itemref idref="ch2"/>
                  </spine>
                </package>
            """.trimIndent(),
            "OEBPS/cover.xhtml" to """
                <html><head><title>Cover</title></head><body><img src="cover.jpg"/></body></html>
            """.trimIndent(),
            "OEBPS/ch1.xhtml" to """
                <html><head><title>ch1</title></head><body>
                  <h1>Bab Pertama</h1>
                  <p>Halo&nbsp;dunia.</p>
                  <blockquote><p>Kutipan di dalam blockquote.</p></blockquote>
                  <p>   </p>
                </body></html>
            """.trimIndent(),
            "OEBPS/text/bab 2.xhtml" to """
                <html><head><title></title></head><body><div><p>Teks bab kedua.</p></div></body></html>
            """.trimIndent(),
        ) + extra,
    )

    private fun writeZip(name: String, entries: Map<String, String>): File {
        val file = File(tempDir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private companion object {
        val ENCRYPTION_XML = """
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:CipherData><enc:CipherReference URI="OEBPS/ch1.xhtml"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
        """.trimIndent()
    }
}
