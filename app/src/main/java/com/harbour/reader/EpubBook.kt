package com.harbour.reader

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException

data class TocEntry(val title: String, val path: String, val depth: Int)

/**
 * An open EPUB: its chapters in reading order (the spine), table of contents and cover, with every
 * file inside readable on demand. Paths are the zip entry names, e.g. "OEBPS/Text/ch1.xhtml".
 */
class EpubBook private constructor(
    private val stream: FileInputStream,
    private val entries: Map<String, ZipEntryInfo>,
    private val mediaTypes: Map<String, String>,
    val title: String,
    val author: String,
    val spine: List<String>,
    val toc: List<TocEntry>,
    val coverPath: String?,
) : Closeable {

    fun read(path: String): ByteArray? = entries[path]?.let { MiniZip.read(stream.channel, it) }

    fun mediaType(path: String): String = mediaTypes[path] ?: guessType(path)

    /** The contents entry for a chapter, if the book has one. */
    fun chapterTitle(index: Int): String? = spine.getOrNull(index)?.let { p -> toc.firstOrNull { it.path == p }?.title }

    override fun close() {
        runCatching { stream.close() }
    }

    companion object {
        fun open(file: File): EpubBook {
            val stream = FileInputStream(file)
            try {
                return parse(stream)
            } catch (e: Exception) {
                stream.close()
                throw e
            }
        }

        private fun parse(stream: FileInputStream): EpubBook {
            val entries = MiniZip.readEntries(stream.channel).associateBy { it.name }
            fun bytes(path: String): ByteArray? = entries[path]?.let { MiniZip.read(stream.channel, it) }

            val container = bytes("META-INF/container.xml") ?: throw IOException("Not an EPUB (no container.xml)")
            var opfPath: String? = null
            parseXml(container) { p ->
                if (p.eventType == XmlPullParser.START_TAG && p.local() == "rootfile" && opfPath == null) {
                    opfPath = p.attr("full-path")
                }
            }
            val opf = opfPath?.let { Uri.decode(it) } ?: throw IOException("Not an EPUB (no package file)")
            val opfBytes = bytes(opf) ?: throw IOException("Missing package file $opf")
            val base = dirOf(opf)

            var title = ""
            var author = ""
            var coverId: String? = null
            var ncxId: String? = null
            class Item(val id: String, val path: String, val type: String, val properties: String)
            val items = LinkedHashMap<String, Item>()
            val spineIds = mutableListOf<String>()

            parseXml(opfBytes) { p ->
                if (p.eventType != XmlPullParser.START_TAG) return@parseXml
                when (p.local()) {
                    "title" -> if (title.isEmpty()) title = p.text().trim()
                    "creator" -> if (author.isEmpty()) author = p.text().trim()
                    "meta" -> if (p.attr("name") == "cover") coverId = p.attr("content")
                    "item" -> {
                        val id = p.attr("id") ?: return@parseXml
                        val href = p.attr("href") ?: return@parseXml
                        items[id] = Item(id, resolve(base, href), p.attr("media-type") ?: "", p.attr("properties") ?: "")
                    }
                    "spine" -> ncxId = p.attr("toc")
                    "itemref" -> p.attr("idref")?.let { spineIds += it }
                }
            }

            val spine = spineIds.mapNotNull { items[it]?.path }.filter { it in entries }
            if (spine.isEmpty()) throw IOException("This EPUB has no chapters")

            val cover = items.values.firstOrNull { "cover-image" in it.properties.split(' ') }?.path
                ?: coverId?.let { items[it]?.path }
                ?: items.values.firstOrNull { it.type.startsWith("image/") && "cover" in it.id.lowercase() }?.path

            val nav = items.values.firstOrNull { "nav" in it.properties.split(' ') }?.path
            val ncx = ncxId?.let { items[it]?.path }
                ?: items.values.firstOrNull { it.type == "application/x-dtbncx+xml" }?.path
            val toc = runCatching { nav?.let { bytes(it) }?.let { parseNav(it, dirOf(nav)) } }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching { ncx?.let { bytes(it) }?.let { parseNcx(it, dirOf(ncx)) } }.getOrNull()
                ?: emptyList()

            return EpubBook(
                stream = stream,
                entries = entries,
                mediaTypes = items.values.associate { it.path to it.type },
                title = title,
                author = author,
                spine = spine,
                toc = toc,
                coverPath = cover?.takeIf { it in entries },
            )
        }

        /** EPUB 3 contents: the links inside the <nav epub:type="toc"> list. */
        private fun parseNav(bytes: ByteArray, base: String): List<TocEntry> {
            val out = mutableListOf<TocEntry>()
            var inToc = false
            var listDepth = 0
            var href: String? = null
            val text = StringBuilder()
            parseXml(bytes) { p ->
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.local()) {
                        "nav" -> {
                            val type = p.attr("epub:type")
                            if (!inToc && (type?.contains("toc") == true || (type == null && out.isEmpty()))) inToc = true
                        }
                        "ol" -> if (inToc) listDepth++
                        "a" -> if (inToc) {
                            href = p.attr("href")
                            text.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> if (href != null) text.append(p.text)
                    XmlPullParser.END_TAG -> when (p.local()) {
                        "a" -> href?.let {
                            out += TocEntry(text.toString().trim().replace(Regex("\\s+"), " "), resolve(base, it), (listDepth - 1).coerceAtLeast(0))
                            href = null
                        }
                        "ol" -> if (inToc) listDepth--
                        "nav" -> if (inToc && out.isNotEmpty()) inToc = false
                    }
                }
            }
            return out.filter { it.title.isNotEmpty() }
        }

        /** EPUB 2 contents: navPoints in the .ncx file. */
        private fun parseNcx(bytes: ByteArray, base: String): List<TocEntry> {
            val out = mutableListOf<TocEntry>()
            var depth = 0
            var label: String? = null
            var inText = false
            parseXml(bytes) { p ->
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.local()) {
                        "navPoint" -> {
                            depth++
                            label = null
                        }
                        "text" -> inText = true
                        "content" -> {
                            val src = p.attr("src")
                            if (src != null && label != null) out += TocEntry(label!!.trim(), resolve(base, src), depth - 1)
                        }
                    }
                    XmlPullParser.TEXT -> if (inText) label = (label ?: "") + p.text
                    XmlPullParser.END_TAG -> when (p.local()) {
                        "navPoint" -> depth--
                        "text" -> inText = false
                    }
                }
            }
            return out.filter { it.title.isNotEmpty() }
        }

        private fun parseXml(bytes: ByteArray, onEvent: (XmlPullParser) -> Unit) {
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Tolerate HTML entities like &nbsp; in contents pages.
            runCatching { p.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
            p.setInput(ByteArrayInputStream(bytes), null)
            while (p.next() != XmlPullParser.END_DOCUMENT) onEvent(p)
        }

        private fun XmlPullParser.local(): String = name.substringAfter(':')

        private fun XmlPullParser.attr(name: String): String? {
            for (i in 0 until attributeCount) {
                if (getAttributeName(i) == name) return getAttributeValue(i)
            }
            return null
        }

        /** Text inside the current element, tolerating nested tags. */
        private fun XmlPullParser.text(): String {
            val sb = StringBuilder()
            var depth = 1
            while (depth > 0) {
                when (next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.TEXT -> sb.append(text)
                    XmlPullParser.END_DOCUMENT -> return sb.toString()
                }
            }
            return sb.toString()
        }

        private fun dirOf(path: String): String = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        /** Turns an href inside the book into a zip path: decoded, relative to [base], no fragment. */
        fun resolve(base: String, href: String): String {
            val clean = Uri.decode(href.substringBefore('#').substringBefore('?'))
            val parts = (if (clean.startsWith("/")) clean.drop(1) else base + clean).split('/')
            val out = ArrayDeque<String>()
            for (part in parts) {
                when (part) {
                    "", "." -> Unit
                    ".." -> out.removeLastOrNull()
                    else -> out.addLast(part)
                }
            }
            return out.joinToString("/")
        }

        private fun guessType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
            "xhtml", "xht" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "js" -> "text/javascript"
            else -> "application/octet-stream"
        }
    }
}
