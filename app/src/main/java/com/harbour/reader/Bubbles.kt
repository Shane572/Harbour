package com.harbour.reader

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A bit of lettering to read: what it says, where it is (0..1 of the page), and whether it's a caption. */
data class Bubble(val text: String, val box: RectF, val narrator: Boolean)

/**
 * Finds the lettering on a comic page with Google's on-device text recognition (ML Kit, offline),
 * then puts it in reading order: panel by panel (found from the gutters between them), and within
 * a panel top to bottom, side by side in reading direction. Caption boxes are told apart from
 * speech balloons by their square corners.
 */
object BubbleFinder {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private class Block(var rect: Rect, var lines: MutableList<String>, val lineHeight: Int, val confidence: Float)

    /** Blocks; run it off the main thread. */
    fun find(page: Bitmap, rtl: Boolean): List<Bubble> {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(page, 0)))
        val heights = result.textBlocks.flatMap { b -> b.lines.mapNotNull { it.boundingBox?.height() } }.sorted()
        if (heights.isEmpty()) return emptyList()
        val lineH = heights[heights.size / 2].coerceAtLeast(4)

        var blocks = result.textBlocks.mapNotNull { b ->
            val r = b.boundingBox ?: return@mapNotNull null
            val lines = b.lines.map { it.text }.toMutableList()
            val h = b.lines.mapNotNull { it.boundingBox?.height() }.sorted().let { it.getOrNull(it.size / 2) } ?: lineH
            val conf = b.lines.map { it.confidence }.average().toFloat().takeIf { !it.isNaN() } ?: 1f
            Block(Rect(r), lines, h, conf)
        }
        blocks = merge(blocks)

        val w = page.width.toFloat()
        val h = page.height.toFloat()
        val found = blocks.mapNotNull { b ->
            val text = Lettering.clean(b.lines)
            val letters = text.count { it.isLetter() }
            val words = text.split(' ').count { word -> word.any { it.isLetter() } }
            when {
                letters < 2 -> null
                b.confidence < 0.35f -> null
                // Big, short lettering is a sound effect or a title, not something to read out.
                b.lineHeight > lineH * 2.2f && words <= 3 -> null
                else -> Bubble(
                    text = text,
                    box = RectF(b.rect.left / w, b.rect.top / h, b.rect.right / w, b.rect.bottom / h),
                    narrator = isCaption(page, b.rect, b.lineHeight),
                )
            }
        }
        return order(found, Panels.find(page, rtl), lineH / h, rtl)
    }

    // Text recognition sometimes splits one balloon into a few blocks: join blocks stacked closely
    // on top of each other with lettering the same size.
    private fun merge(input: List<Block>): List<Block> {
        val blocks = input.sortedBy { it.rect.top }.toMutableList()
        var i = 0
        while (i < blocks.size) {
            val a = blocks[i]
            val j = (i + 1 until blocks.size).firstOrNull { k ->
                val b = blocks[k]
                val gap = b.rect.top - a.rect.bottom
                val overlap = minOf(a.rect.right, b.rect.right) - maxOf(a.rect.left, b.rect.left)
                val narrower = minOf(a.rect.width(), b.rect.width())
                val sizes = maxOf(a.lineHeight, b.lineHeight).toFloat() / minOf(a.lineHeight, b.lineHeight).coerceAtLeast(1)
                gap in -a.lineHeight / 2..(a.lineHeight * 0.8f).roundToInt() && overlap > narrower * 0.5f && sizes < 1.4f
            }
            if (j == null) {
                i++
            } else {
                val b = blocks.removeAt(j)
                a.rect.union(b.rect)
                a.lines.addAll(b.lines)
            }
        }
        return blocks
    }

    /**
     * A caption box has square corners and its fill reaches them; a balloon is rounded, so just past
     * the corners of its lettering you're outside it. Coloured boxes (often yellow) are captions too.
     */
    private fun isCaption(page: Bitmap, r: Rect, lineH: Int): Boolean {
        val d = (lineH * 0.45f).roundToInt().coerceAtLeast(3)
        fun at(x: Int, y: Int): Int = page.getPixel(x.coerceIn(0, page.width - 1), y.coerceIn(0, page.height - 1))
        fun close(a: Int, b: Int): Boolean =
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) < 40 &&
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) < 40 &&
                abs((a and 0xFF) - (b and 0xFF)) < 40

        val sides = listOf(
            at(r.left - d / 2, r.centerY()), at(r.right + d / 2, r.centerY()),
            at(r.centerX(), r.top - d / 2), at(r.centerX(), r.bottom + d / 2),
        )
        // The fill: a side sample that most of the others agree with.
        val fill = sides.firstOrNull { s -> sides.count { close(it, s) } >= 3 } ?: return false
        val corners = listOf(
            at(r.left - d, r.top - d), at(r.right + d, r.top - d),
            at(r.left - d, r.bottom + d), at(r.right + d, r.bottom + d),
        )
        val square = corners.count { close(it, fill) } >= 3
        val red = (fill shr 16) and 0xFF
        val green = (fill shr 8) and 0xFF
        val blue = fill and 0xFF
        val coloured = maxOf(red, green, blue) - minOf(red, green, blue) > 70
        return square || coloured
    }

    private fun order(bubbles: List<Bubble>, panels: List<RectF>, lineH: Float, rtl: Boolean): List<Bubble> {
        fun panelOf(b: Bubble): Int {
            val cx = b.box.centerX()
            val cy = b.box.centerY()
            val inside = panels.indexOfFirst { it.contains(cx, cy) }
            if (inside >= 0) return inside
            return panels.indices.minByOrNull { i ->
                val p = panels[i]
                hypot(cx - cx.coerceIn(p.left, p.right), cy - cy.coerceIn(p.top, p.bottom))
            } ?: 0
        }
        return bubbles.groupBy(::panelOf).toSortedMap().values.flatMap { inPanel ->
            // Rows: bubbles starting at about the same height, read across in reading direction.
            val rows = mutableListOf<MutableList<Bubble>>()
            inPanel.sortedBy { it.box.top }.forEach { b ->
                val row = rows.lastOrNull()
                if (row != null && b.box.top - row.first().box.top < lineH * 1.5f) row += b else rows += mutableListOf(b)
            }
            rows.flatMap { row -> if (rtl) row.sortedByDescending { it.box.centerX() } else row.sortedBy { it.box.centerX() } }
        }
    }
}

/**
 * Panels from the gutters: recursively cut the page along rows or columns that are all gutter
 * colour (an XY cut), on a small copy of the page. Returned in reading order, as 0..1 of the page.
 */
private object Panels {
    fun find(page: Bitmap, rtl: Boolean): List<RectF> {
        val sw = 240
        val sh = (page.height * sw / page.width.toFloat()).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(page, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small != page) small.recycle()
        val lum = IntArray(px.size) { i ->
            val c = px[i]
            (((c shr 16) and 0xFF) * 3 + ((c shr 8) and 0xFF) * 6 + (c and 0xFF)) / 10
        }
        // The gutter colour is the page's outer edge.
        var sum = 0L
        var n = 0
        for (x in 0 until sw) {
            sum += lum[x] + lum[(sh - 1) * sw + x]
            n += 2
        }
        for (y in 0 until sh) {
            sum += lum[y * sw] + lum[y * sw + sw - 1]
            n += 2
        }
        val gutter = (sum / n).toInt()
        fun isGutter(x: Int, y: Int) = abs(lum[y * sw + x] - gutter) < 35

        val out = mutableListOf<RectF>()

        // Runs of content between gutters along one direction, ignoring slivers.
        fun runs(from: Int, to: Int, blank: (Int) -> Boolean): List<IntRange> {
            val result = mutableListOf<IntRange>()
            var start = -1
            for (i in from until to) {
                if (!blank(i)) {
                    if (start < 0) start = i
                } else if (start >= 0) {
                    if (i - start >= 3) result += start until i
                    start = -1
                }
            }
            if (start >= 0 && to - start >= 3) result += start until to
            return result
        }

        fun cut(x0: Int, y0: Int, x1: Int, y1: Int, rowsFirst: Boolean, depth: Int) {
            val rowBlank = { y: Int -> (x0 until x1).count { isGutter(it, y) } >= (x1 - x0) * 0.97f }
            val colBlank = { x: Int -> (y0 until y1).count { isGutter(x, it) } >= (y1 - y0) * 0.97f }
            if (depth < 8) {
                for (rows in if (rowsFirst) listOf(true, false) else listOf(false, true)) {
                    val parts = if (rows) runs(y0, y1, rowBlank) else runs(x0, x1, colBlank)
                    if (parts.size > 1) {
                        val ordered = if (!rows && rtl) parts.reversed() else parts
                        ordered.forEach { p ->
                            if (rows) cut(x0, p.first, x1, p.last + 1, false, depth + 1)
                            else cut(p.first, y0, p.last + 1, y1, true, depth + 1)
                        }
                        return
                    }
                }
            }
            out += RectF(x0 / sw.toFloat(), y0 / sh.toFloat(), x1 / sw.toFloat(), y1 / sh.toFloat())
        }

        cut(0, 0, sw, sh, rowsFirst = true, depth = 0)
        return out
    }
}

/** Turns comic lettering into sentences a voice reads naturally. */
object Lettering {
    private val SPACES = Regex("\\s+")
    private val SENTENCE = Regex("([.!?…]\\s+|^\\s*)(\\p{Ll})")
    private val LONE_I = Regex("\\bi\\b")

    fun clean(lines: List<String>): String {
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (sb.isNotEmpty()) {
                // "INCRED-" + "IBLE" is one word; "WAIT--" is a dash.
                if (sb.endsWith("-") && !sb.endsWith("--") && line.first().isLetter()) sb.setLength(sb.length - 1) else sb.append(' ')
            }
            sb.append(line)
        }
        var s = sb.toString()
            .replace("--", " — ")
            .replace(Regex("[*_~|♪♫<>]"), " ")
            .replace(SPACES, " ")
            .trim()
        val letters = s.count { it.isLetter() }
        val upper = s.count { it.isUpperCase() }
        // Lettering is usually all capitals, which voices tend to spell out. Read it as sentences.
        if (letters > 0 && upper >= letters * 0.7f) {
            s = s.lowercase()
            s = SENTENCE.replace(s) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }
            s = LONE_I.replace(s, "I")
        }
        return s
    }
}
