package com.harbour.reader

import android.net.Uri
import java.io.File

data class Comic(
    val uri: String,
    val name: String,
    val seriesPath: String,
    val seriesName: String,
    val size: Long,
    val modified: Long,
) {
    val title: String get() = name.substringBeforeLast('.')

    val file: File get() = File(Uri.parse(uri).path ?: "")

    /** Stable id for progress and settings, independent of where the folder lives. */
    val key: String get() = "$seriesPath/$name"
}

data class Series(val path: String, val name: String, val comics: List<Comic>)

data class Progress(val page: Int, val total: Int, val time: Long) {
    val finished: Boolean get() = total > 0 && page >= total - 1
    val fraction: Float get() = if (total <= 1) (if (finished) 1f else 0f) else page.toFloat() / (total - 1)
}

data class LibraryRoot(val uri: String, val name: String)

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
