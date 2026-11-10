package com.harbour.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel

/** An open CBZ: its page list in reading order, and page decoding on demand. */
class ComicArchive private constructor(
    private val channel: FileChannel,
    private val resources: List<Closeable>,
    private val pages: List<ZipEntryInfo>,
) : Closeable {

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
        resources.forEach { runCatching { it.close() } }
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

        fun open(context: Context, uri: Uri): ComicArchive {
            // Fast path: read the zip in place through the file descriptor.
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Can't open file")
            val stream = FileInputStream(pfd.fileDescriptor)
            try {
                val ch = stream.channel
                val entries = MiniZip.readEntries(ch)
                return ComicArchive(ch, listOf(stream, pfd), pagesOf(entries))
            } catch (e: Exception) {
                runCatching { stream.close() }
                runCatching { pfd.close() }
            }

            // Slow path (e.g. cloud-backed files that aren't seekable): copy to cache first.
            val tmp = File.createTempFile("open", ".cbz", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open file")
                input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                val fs = FileInputStream(tmp)
                try {
                    val ch = fs.channel
                    val entries = MiniZip.readEntries(ch)
                    return ComicArchive(ch, listOf(fs, Closeable { tmp.delete() }), pagesOf(entries))
                } catch (e: Exception) {
                    fs.close()
                    throw e
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
    }
}
