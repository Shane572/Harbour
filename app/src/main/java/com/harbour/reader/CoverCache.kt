package com.harbour.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/** Small cover thumbnails (a comic's first page, a book's cover image), cached on disk and in memory. */
class CoverCache(context: Context) {
    private val dir = File(context.cacheDir, "covers").apply { mkdirs() }
    private val memory = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }
    private val failed: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val gate = Semaphore(3)

    private fun keyOf(c: LibraryItem): String =
        "${c.key}|${c.size}|${c.modified}".hashCode().toUInt().toString(16)

    fun peek(c: LibraryItem): ImageBitmap? = memory.get(keyOf(c))

    suspend fun load(c: LibraryItem): ImageBitmap? {
        val k = keyOf(c)
        memory.get(k)?.let { return it }
        if (k in failed) return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                memory.get(k)?.let { return@withContext it }
                val file = File(dir, "$k.jpg")
                if (!file.exists() && !generate(c, file)) {
                    failed += k
                    return@withContext null
                }
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()?.also { memory.put(k, it) }
            }
        }
    }

    private fun generate(c: LibraryItem, out: File): Boolean = runCatching {
        val page = when (c) {
            is Book -> EpubBook.open(c.file).use { book ->
                book.coverPath?.let(book::read)?.let { bytes ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bytes, 900L * 1400L) }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            }
            else -> ComicArchive.coverPage(c.file, 900L * 1400L)
        } ?: return@runCatching false
        val w = 360
        val h = (w * page.height.toFloat() / page.width).toInt().coerceIn(1, 1080)
        val thumb = Bitmap.createScaledBitmap(page, w, h, true)
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        tmp.renameTo(out)
    }.getOrDefault(false)

    private fun sampleFor(bytes: ByteArray, maxPixels: Long): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth > 0 && (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > maxPixels) sample *= 2
        return sample
    }
}
