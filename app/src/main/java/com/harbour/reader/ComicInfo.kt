package com.harbour.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.text.DateFormatSymbols

/**
 * Issue details from the ComicInfo.xml that tagging tools (ComicRack, ComicTagger, Mylar…) put
 * inside many CBZ and CBR files: series, number, writer, artists, summary and so on.
 */
class ComicInfo private constructor(private val fields: Map<String, String>) {
    operator fun get(name: String): String? = fields[name]?.trim()?.takeIf { it.isNotEmpty() }

    /** "Green Lantern #48 (Vol. 4)", if the file says which series it is. */
    val heading: String?
        get() {
            val series = this["Series"] ?: return null
            return buildString {
                append(series)
                this@ComicInfo["Number"]?.let { append(" #").append(it) }
                this@ComicInfo["Volume"]?.let { append(" (Vol. ").append(it).append(')') }
            }
        }

    /** "March 2009", or just the year. */
    val date: String?
        get() {
            val year = this["Year"]?.takeIf { it != "-1" } ?: return null
            val month = this["Month"]?.toIntOrNull()?.takeIf { it in 1..12 }
            return if (month == null) year else "${DateFormatSymbols.getInstance().months[month - 1]} $year"
        }

    /** Credits and the rest, in a sensible order, for showing as label / value rows. */
    val details: List<Pair<String, String>>
        get() = LABELS.mapNotNull { (field, label) -> this[field]?.let { label to it } }

    companion object {
        private val LABELS = listOf(
            "Writer" to "Writer",
            "Penciller" to "Pencils",
            "Inker" to "Inks",
            "Colorist" to "Colours",
            "Letterer" to "Letters",
            "CoverArtist" to "Cover",
            "Editor" to "Editor",
            "Publisher" to "Publisher",
            "Imprint" to "Imprint",
            "StoryArc" to "Story arc",
            "SeriesGroup" to "Series group",
            "Characters" to "Characters",
            "Teams" to "Teams",
            "Locations" to "Locations",
            "Genre" to "Genre",
            "AgeRating" to "Age rating",
            "PageCount" to "Pages",
        )

        /** Reads the file's ComicInfo.xml; null if it has none (or it can't be read). */
        fun read(file: File): ComicInfo? {
            val bytes = runCatching { ComicArchive.comicInfoBytes(file) }.getOrNull() ?: return null
            return runCatching { parse(bytes) }.getOrNull()
        }

        /** Takes the plain text of each element directly under the root (Pages and the like are skipped). */
        private fun parse(bytes: ByteArray): ComicInfo? {
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(ByteArrayInputStream(bytes), null)
            val fields = HashMap<String, String>()
            var current: String? = null
            val text = StringBuilder()
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (p.depth == 2) {
                        current = p.name
                        text.setLength(0)
                    }
                    XmlPullParser.TEXT -> if (current != null && p.depth == 2) text.append(p.text)
                    XmlPullParser.END_TAG -> if (p.depth == 2 && current != null) {
                        fields[current] = text.toString()
                        current = null
                    }
                }
                event = p.next()
            }
            return if (fields.isEmpty()) null else ComicInfo(fields)
        }
    }
}
