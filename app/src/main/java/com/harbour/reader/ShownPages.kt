package com.harbour.reader

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The pages as the reader shows them: optionally with plain margins trimmed off, and wide
 * two-page spreads split into their halves. Indexes here are shown pages; progress and saved
 * pages still use the file's own pages ([sourceOf]), so nothing jumps when the toggles change.
 */
class ShownPages private constructor(
    private val source: ComicArchive,
    private val trim: Boolean,
    private val parts: List<Part>,
) : ComicArchive() {
    private class Part(val page: Int, val half: Int)

    override val pageCount: Int get() = parts.size

    /** The file's page that a shown page comes from. */
    fun sourceOf(index: Int): Int = parts[index.coerceIn(0, parts.lastIndex)].page

    /** The first shown page for one of the file's pages. */
    fun indexOf(sourcePage: Int): Int = parts.indexOfFirst { it.page >= sourcePage }.let { if (it < 0) parts.lastIndex else it }

    // Which part of the file's page each shown page actually shows (0..1), once it's been drawn.
    private val crops = ConcurrentHashMap<Int, RectF>()

    private fun halfOf(part: Part): RectF = when (part.half) {
        LEFT -> RectF(0f, 0f, 0.5f, 1f)
        RIGHT -> RectF(0.5f, 0f, 1f, 1f)
        else -> RectF(0f, 0f, 1f, 1f)
    }

    /** The part of the file's page that shown page [index] shows, as 0..1 of the file's page. */
    fun cropOf(index: Int): RectF = crops[index] ?: halfOf(parts[index.coerceIn(0, parts.lastIndex)])

    /** The shown page for a spot on one of the file's pages (which half, when spreads are split). */
    fun indexFor(sourcePage: Int, x: Float?): Int {
        val first = indexOf(sourcePage)
        val second = first + 1
        if (x == null || second > parts.lastIndex || parts[second].page != sourcePage) return first
        return if (halfOf(parts[first]).let { x >= it.left && x <= it.right }) first else second
    }

    override fun decodePage(index: Int, maxPixels: Long, config: Bitmap.Config): Bitmap? {
        val part = parts[index]
        val whole = source.decodePage(part.page, maxPixels, config) ?: return null
        val cut = when (part.half) {
            LEFT -> Bitmap.createBitmap(whole, 0, 0, whole.width / 2, whole.height)
            RIGHT -> Bitmap.createBitmap(whole, whole.width / 2, 0, whole.width - whole.width / 2, whole.height)
            else -> whole
        }
        val half = halfOf(part)
        val kept = if (trim) Trim.bounds(cut) else null
        if (kept == null) {
            crops[index] = half
            return cut
        }
        crops[index] = RectF(
            half.left + half.width() * kept.left / cut.width,
            half.top + half.height() * kept.top / cut.height,
            half.left + half.width() * kept.right / cut.width,
            half.top + half.height() * kept.bottom / cut.height,
        )
        return Bitmap.createBitmap(cut, kept.left, kept.top, kept.width(), kept.height())
    }

    override fun pageSize(index: Int): IntArray? = null

    // The reader opened the file and closes it; this is only a view onto it.
    override fun close() = Unit

    companion object {
        private const val WHOLE = 0
        private const val LEFT = 1
        private const val RIGHT = 2

        /**
         * [wide] marks the file's pages that are spreads (from [findSpreads]), or null to keep every
         * page whole. Right-to-left reading shows a spread's right half first. The cover is never split.
         */
        fun build(source: ComicArchive, trim: Boolean, wide: BooleanArray?, rtl: Boolean): ShownPages {
            val parts = ArrayList<Part>(source.pageCount)
            for (p in 0 until source.pageCount) {
                if (wide != null && p > 0 && wide.getOrElse(p) { false }) {
                    parts += Part(p, if (rtl) RIGHT else LEFT)
                    parts += Part(p, if (rtl) LEFT else RIGHT)
                } else {
                    parts += Part(p, WHOLE)
                }
            }
            return ShownPages(source, trim, parts)
        }

        /** Which pages are clearly wider than tall. Reads every page's size, so keep it off the main thread. */
        fun findSpreads(source: ComicArchive): BooleanArray =
            BooleanArray(source.pageCount) { i -> source.pageSize(i)?.let { it[0] > it[1] * 1.15f } == true }
    }
}

/** Cuts away plain white or black borders around a page, keeping a hair of margin. */
object Trim {
    fun margins(b: Bitmap): Bitmap {
        val r = bounds(b) ?: return b
        return Bitmap.createBitmap(b, r.left, r.top, r.width(), r.height())
    }

    /** The part of the page to keep, or null if there's nothing worth trimming. */
    fun bounds(b: Bitmap): Rect? {
        val w = b.width
        val h = b.height
        if (w < 64 || h < 64) return null
        val buf = IntArray(maxOf(w, h))

        fun row(y: Int): Int {
            b.getPixels(buf, 0, w, 0, y, w, 1)
            return w
        }

        fun col(x: Int): Int {
            b.getPixels(buf, 0, 1, x, 0, 1, h)
            return h
        }

        // The border's colour, from the outermost line: it has to be near white or near black.
        fun background(n: Int): Int? {
            var r = 0L
            var g = 0L
            var bl = 0L
            for (i in 0 until n) {
                val c = buf[i]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                bl += c and 0xFF
            }
            val ar = (r / n).toInt()
            val ag = (g / n).toInt()
            val ab = (bl / n).toInt()
            val lum = (ar * 3 + ag * 6 + ab) / 10
            return if (lum in 60..185) null else (ar shl 16) or (ag shl 8) or ab
        }

        // A line is margin if almost every pixel is close to the border colour (JPEG noise allowed).
        fun plain(n: Int, ref: Int): Boolean {
            val rr = (ref shr 16) and 0xFF
            val rg = (ref shr 8) and 0xFF
            val rb = ref and 0xFF
            val allowed = n / 200
            var off = 0
            var i = 0
            while (i < n) {
                val c = buf[i]
                if (abs(((c shr 16) and 0xFF) - rr) > 40 || abs(((c shr 8) and 0xFF) - rg) > 40 || abs((c and 0xFF) - rb) > 40) {
                    if (++off > allowed) return false
                }
                i += 2
            }
            return true
        }

        // How many plain lines there are from one edge inwards, up to a quarter of the page.
        fun scan(limit: Int, line: (Int) -> Int): Int {
            val ref = background(line(0)) ?: return 0
            var i = 0
            while (i < limit && plain(line(i), ref)) i++
            return i
        }

        val top = scan(h / 4) { row(it) }
        val bottom = scan(h / 4) { row(h - 1 - it) }
        val left = scan(w / 4) { col(it) }
        val right = scan(w / 4) { col(w - 1 - it) }
        if (left + right < w / 50 && top + bottom < h / 50) return null

        val keep = minOf(w, h) / 100
        val l = (left - keep).coerceAtLeast(0)
        val t = (top - keep).coerceAtLeast(0)
        val r = (w - right + keep).coerceAtMost(w)
        val btm = (h - bottom + keep).coerceAtMost(h)
        if (r - l < w / 2 || btm - t < h / 2) return null
        return Rect(l, t, r, btm)
    }
}
