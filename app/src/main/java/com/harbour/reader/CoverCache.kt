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

/** Makes a small cover thumbnail from each comic's first page, cached on disk and in memory. */
class CoverCache(context: Context) {
    private val dir = File(context.cacheDir, "covers").apply { mkdirs() }
    private val memory = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount
    }
    private val failed: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val gate = Semaphore(3)

    private fun keyOf(c: Comic): String =
        "${c.key}|${c.size}|${c.modified}".hashCode().toUInt().toString(16)

    fun peek(c: Comic): ImageBitmap? = memory.get(keyOf(c))

    suspend fun load(c: Comic): ImageBitmap? {
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

    private fun generate(c: Comic, out: File): Boolean = runCatching {
        val page = ComicArchive.coverPage(c.file, 900L * 1400L) ?: return@runCatching false
        val w = 360
        val h = (w * page.height.toFloat() / page.width).toInt().coerceIn(1, 1080)
        val thumb = Bitmap.createScaledBitmap(page, w, h, true)
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        tmp.renameTo(out)
    }.getOrDefault(false)
}
