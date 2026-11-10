package com.harbour.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

/** An open CBZ: its page list in reading order, and page decoding on demand. */
class ComicArchive private constructor(
    private val stream: FileInputStream,
    private val pages: List<ZipEntryInfo>,
) : Closeable {
    private val channel: FileChannel = stream.channel

    val pageCount: Int get() = pages.size

    /** Decodes a page, downsampled so it stays under [maxPixels] to keep memory sane. */
    fun decodePage(index: Int, maxPixels: Long, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        val bytes = MiniZip.read(channel, pages[index])
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > maxPixels) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = config
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    override fun close() {
        runCatching { stream.close() }
    }

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

        fun pagesOf(entries: List<ZipEntryInfo>): List<ZipEntryInfo> = entries
            .filter { e ->
                val base = e.name.substringAfterLast('/')
                !e.name.endsWith("/") &&
                    !e.name.startsWith("__MACOSX") &&
                    !base.startsWith(".") &&
                    base.substringAfterLast('.', "").lowercase() in IMAGE_EXT
            }
            .sortedWith(compareBy(NaturalOrder) { it.name })

        fun open(file: File): ComicArchive {
            val stream = FileInputStream(file)
            try {
                return ComicArchive(stream, pagesOf(MiniZip.readEntries(stream.channel)))
            } catch (e: Exception) {
                stream.close()
                throw e
            }
        }
    }
}
