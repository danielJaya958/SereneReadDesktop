package com.sereneread.project.feature.reader.data

import com.sereneread.project.feature.reader.domain.Chapter
import com.sereneread.project.feature.reader.domain.Paragraph
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.zip.ZipFile

data class EpubMetadata(
    val title: String?,
    val authors: List<String>,
)

data class EpubBook(
    val metadata: EpubMetadata,
    val chapters: List<Chapter>,
)

class EpubException(message: String) : IOException(message)

/**
 * Minimal EPUB 2/3 reader: container.xml -> OPF -> spine order -> plain-text paragraphs.
 * Chapter titles come from the first heading in each spine document, not from the TOC.
 */
class EpubParser {

    /** Reads only container.xml and the OPF, without touching chapter files. */
    fun readMetadata(file: File): EpubMetadata = ZipFile(file).use { zip ->
        zip.readPackage().opf.toMetadata()
    }

    fun parse(file: File): EpubBook = ZipFile(file).use { zip ->
        val (opfDir, opf) = zip.readPackage()

        val manifest = opf.select("manifest > item").associate { it.attr("id") to it.attr("href") }
        val spineHrefs = opf.select("spine > itemref")
            .mapNotNull { manifest[it.attr("idref")] }
            .map { resolvePath(opfDir, it) }

        if (spineHrefs.isEmpty()) throw EpubException("Spine EPUB kosong.")
        zip.ensureNotEncrypted(spineHrefs)

        val chapters = spineHrefs.mapNotNull { path ->
            val entry = zip.getEntry(path) ?: return@mapNotNull null
            val html = zip.getInputStream(entry).use { Jsoup.parse(it, null, path) }
            html.toChapter()
        }.mapIndexed { index, chapter ->
            if (chapter.title.isBlank()) chapter.copy(title = "Bab ${index + 1}") else chapter
        }

        if (chapters.isEmpty()) throw EpubException("EPUB tidak memiliki teks yang bisa dibaca.")

        EpubBook(metadata = opf.toMetadata(), chapters = chapters)
    }

    private data class Package(val dir: String, val opf: Document)

    private fun ZipFile.readPackage(): Package {
        val opfPath = readXml(CONTAINER_PATH)
            .selectFirst("rootfile")
            ?.attr("full-path")
            ?.takeIf { it.isNotBlank() }
            ?: throw EpubException("container.xml tidak memiliki rootfile.")

        return Package(dir = opfPath.substringBeforeLast('/', missingDelimiterValue = ""), opf = readXml(opfPath))
    }

    private fun Document.toMetadata() = EpubMetadata(
        title = selectFirst("dc|title, title")?.text()?.trim()?.takeIf { it.isNotEmpty() },
        authors = select("dc|creator, creator").map { it.text().trim() }.filter { it.isNotEmpty() }.distinct(),
    )

    private fun Document.toChapter(): Chapter? {
        val body = body() ?: return null
        val blocks = body.select(BLOCK_SELECTOR).filter { el -> el.parents().none { it.`is`(BLOCK_SELECTOR) } }

        val paragraphs = if (blocks.isEmpty()) {
            body.wholeText().split(BLANK_LINES).map { Paragraph(it.trim()) }
        } else {
            blocks.map { Paragraph(it.text(), isHeading = it.isHeading()) }
        }.map { it.copy(text = it.text.replace(NBSP, ' ')) }.filter { it.text.isNotBlank() }

        if (paragraphs.isEmpty()) return null

        val title = paragraphs.firstOrNull { it.isHeading }?.text ?: title()
        return Chapter(title = title, paragraphs = paragraphs)
    }

    private fun Element.isHeading() = normalName() in HEADINGS

    private fun ZipFile.readXml(path: String): Document {
        val entry = getEntry(path) ?: throw EpubException("File $path tidak ditemukan di EPUB.")
        return getInputStream(entry).use { Jsoup.parse(it, null, "", Parser.xmlParser()) }
    }

    /** Fails fast on DRM. Font obfuscation also uses encryption.xml, so only content files count. */
    private fun ZipFile.ensureNotEncrypted(contentPaths: List<String>) {
        if (getEntry(ENCRYPTION_PATH) == null) return
        // CipherReference is often namespace-prefixed (enc:CipherReference); match on its URI attribute instead.
        val encrypted = readXml(ENCRYPTION_PATH).select("[URI]").map { it.attr("URI") }.toSet()
        if (contentPaths.any { it in encrypted }) {
            throw EpubException("EPUB ini dilindungi DRM dan tidak bisa dibuka.")
        }
    }

    private fun resolvePath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#')
        return runCatching {
            URI(null, null, "$baseDir/", null).resolve(URI(cleanHref)).path.removePrefix("/")
        }.getOrElse {
            if (baseDir.isEmpty()) cleanHref else "$baseDir/$cleanHref"
        }
    }

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val ENCRYPTION_PATH = "META-INF/encryption.xml"
        val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        const val BLOCK_SELECTOR = "p, h1, h2, h3, h4, h5, h6, li, blockquote, pre"
        val BLANK_LINES = Regex("""\n\s*\n""")
        const val NBSP = ' '
    }
}
