package com.harbour.reader

import android.net.Uri
import java.io.File

/** Anything in the library with a file and a cover: a comic or a book. */
interface LibraryItem {
    val uri: String
    val name: String
    val title: String
    val key: String
    val size: Long
    val modified: Long
    val file: File get() = File(Uri.parse(uri).path ?: "")
}

data class Comic(
    override val uri: String,
    override val name: String,
    /** Where the file sits under its library folder. Only used for stable ids, never for display. */
    val folderPath: String,
    override val size: Long,
    override val modified: Long,
) : LibraryItem {
    override val title: String get() = name.substringBeforeLast('.')

    val folderName: String get() = folderPath.substringAfterLast('/')

    /** Stable id for progress, groups and settings. */
    override val key: String get() = "$folderPath/$name"
}

data class Book(
    override val uri: String,
    override val name: String,
    val folderPath: String,
    override val size: Long,
    override val modified: Long,
    val bookTitle: String,
    val author: String,
) : LibraryItem {
    override val title: String get() = bookTitle.ifBlank { name.substringBeforeLast('.') }

    override val key: String get() = "book:$folderPath/$name"

    /** PDFs can't be re-flowed like EPUBs, so they're read page by page in the comic reader. */
    val isPdf: Boolean get() = ComicArchive.isPdf(name)

    fun asComic(): Comic = Comic(uri, name, folderPath, size, modified)
}

/** Where you are in a book: a chapter (spine item) and how far through it. */
data class BookProgress(val chapter: Int, val fraction: Float, val chapters: Int, val time: Long) {
    val overall: Float get() = if (chapters <= 0) 0f else ((chapter + fraction) / chapters).coerceIn(0f, 1f)
    val finished: Boolean get() = chapters > 0 && chapter >= chapters - 1 && fraction >= 0.99f
}

enum class BookTheme(val label: String, val background: Long, val text: Long) {
    Dark("Dark", 0xFF121214, 0xFFD9D6CF),
    Black("Black", 0xFF000000, 0xFFCFCCC5),
    Sepia("Sepia", 0xFFF3EAD7, 0xFF433422),
    Light("Light", 0xFFFAFAF7, 0xFF1C1C1C),
}

enum class BookFont(val label: String, val css: String?) {
    Original("Book's own", null),
    Serif("Serif", "Georgia, 'Noto Serif', serif"),
    Sans("Sans", "'Roboto', 'Noto Sans', sans-serif"),
}

enum class BookSpacing(val label: String, val lineHeight: Float) { Normal("Normal", 1.45f), Relaxed("Relaxed", 1.65f), Loose("Loose", 1.9f) }

enum class BookMargins(val label: String, val px: Int) { Narrow("Narrow", 24), Normal("Normal", 48), Wide("Wide", 96) }

data class BookStyle(
    val sizePercent: Int = 110,
    val font: BookFont = BookFont.Original,
    val spacing: BookSpacing = BookSpacing.Normal,
    val margins: BookMargins = BookMargins.Normal,
    val theme: BookTheme = BookTheme.Dark,
)

/**
 * A collection Shane makes himself. Groups have nothing to do with folders on disk.
 * [readOrder] is an optional hand-picked reading sequence (comic keys, all of them in [keys]).
 */
data class Group(val id: String, val name: String, val keys: List<String>, val readOrder: List<String> = emptyList())

data class Progress(val page: Int, val total: Int, val time: Long) {
    val finished: Boolean get() = total > 0 && page >= total - 1
    val fraction: Float get() = if (total <= 1) (if (finished) 1f else 0f) else page.toFloat() / (total - 1)
}

data class LibraryRoot(val uri: String, val name: String)

enum class SortOrder(val label: String) { Title("Title"), Added("Recently added"), Read("Recently read") }

enum class CoverSize(val label: String) { Small("Small"), Medium("Medium"), Large("Large") }

enum class ReadingMode(val label: String) { LeftToRight("Left to right"), RightToLeft("Right to left"), Vertical("Vertical scroll") }

enum class PageLayout(val label: String) { Single("One"), Auto("Auto"), Double("Two") }

/** Sorts "Issue 2" before "Issue 10", ignoring case. */
object NaturalOrder : Comparator<String> {
    private fun Char.isAsciiDigit() = this in '0'..'9'

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
                val si = i
                while (i < a.length && a[i].isAsciiDigit()) i++
                val sj = j
                while (j < b.length && b[j].isAsciiDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
