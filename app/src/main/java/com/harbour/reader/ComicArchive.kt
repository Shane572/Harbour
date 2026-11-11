package com.harbour.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.sqrt

/** Pages in reading order, decoded on demand: a CBZ or CBR comic, or a PDF book. */
abstract class ComicArchive : Closeable {
    abstract val pageCount: Int

    /** The page's image file. Archives of images provide this; PDFs draw pages instead. */
    protected open fun pageBytes(index: Int): ByteArray = throw UnsupportedOperationException()

    /** Decodes a page, downsampled so it stays under [maxPixels] to keep memory sane. */
    open fun decodePage(index: Int, maxPixels: Long, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? =
        decodeImage(pageBytes(index), maxPixels, config)

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

        fun isPage(name: String): Boolean {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
            return !name.endsWith("/") &&
                !name.startsWith("__MACOSX") &&
                !base.startsWith(".") &&
                base.substringAfterLast('.', "").lowercase() in IMAGE_EXT
        }

        fun isComicFile(name: String): Boolean =
            name.endsWith(".cbz", ignoreCase = true) || name.endsWith(".cbr", ignoreCase = true)

        /** PDFs live with the books, but are read page by page like a comic. */
        fun isPdf(name: String): Boolean = name.endsWith(".pdf", ignoreCase = true)

        /** Opens a comic. [cacheDir] is where CBRs are unpacked while being read. */
        fun open(file: File, cacheDir: File): ComicArchive = when {
            isPdf(file.name) -> PdfComic.open(file)
            isZip(file) -> ZipComic.open(file)
            else -> RarComic.open(file, cacheDir)
        }

        /** The first page, for covers, without unpacking a whole CBR. */
        fun coverPage(file: File, maxPixels: Long): Bitmap? {
            if (isPdf(file.name)) {
                return PdfComic.open(file).use { if (it.pageCount == 0) null else it.decodePage(0, maxPixels) }
            }
            if (isZip(file)) {
                return ZipComic.open(file).use { if (it.pageCount == 0) null else it.decodePage(0, maxPixels) }
            }
            val bytes = RarComic.firstPageBytes(file) ?: return null
            return decodeImage(bytes, maxPixels, Bitmap.Config.ARGB_8888)
        }

        /** Many .cbr files are really zips with the wrong name, so check the file itself. */
        private fun isZip(file: File): Boolean = runCatching {
            FileInputStream(file).use { s ->
                val head = ByteArray(4)
                s.read(head) == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
            }
        }.getOrDefault(file.name.endsWith(".cbz", ignoreCase = true))

        private fun decodeImage(bytes: ByteArray, maxPixels: Long, config: Bitmap.Config): Bitmap? {
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
    }
}

/**
 * PDF: Android's own renderer draws each page to a bitmap. It can only have one page open at a
 * time, so drawing is serialised.
 */
private class PdfComic(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer) : ComicArchive() {
    override val pageCount: Int get() = renderer.pageCount

    override fun decodePage(index: Int, maxPixels: Long, config: Bitmap.Config): Bitmap? = synchronized(renderer) {
        renderer.openPage(index).use { page ->
            // Page sizes are in points (1/72 inch); scale up to roughly maxPixels for sharp text.
            val points = page.width.toDouble() * page.height
            val scale = if (points <= 0) 1.0 else sqrt(maxPixels / points).coerceIn(0.5, 6.0)
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE) // PDFs are drawn on transparent; pages should be paper white.
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() {
        synchronized(renderer) { runCatching { renderer.close() } }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(file: File): PdfComic {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                return PdfComic(pfd, PdfRenderer(pfd))
            } catch (e: Exception) {
                pfd.close()
                throw e
            }
        }
    }
}

/** CBZ: pages are read straight out of the zip as needed. */
private class ZipComic(private val stream: FileInputStream, private val pages: List<ZipEntryInfo>) : ComicArchive() {
    override val pageCount: Int get() = pages.size

    override fun pageBytes(index: Int): ByteArray = MiniZip.read(stream.channel, pages[index])

    override fun close() {
        runCatching { stream.close() }
    }

    companion object {
        fun open(file: File): ZipComic {
            val stream = FileInputStream(file)
            try {
                val pages = MiniZip.readEntries(stream.channel)
                    .filter { ComicArchive.isPage(it.name) }
                    .sortedWith(compareBy(NaturalOrder) { it.name })
                return ZipComic(stream, pages)
            } catch (e: Exception) {
                stream.close()
                throw e
            }
        }
    }
}

/**
 * CBR: RAR can't be read page-by-page cheaply, so the pages are unpacked once into the cache
 * (the last few comics are kept) and read from there.
 */
private class RarComic(private val pages: List<File>) : ComicArchive() {
    override val pageCount: Int get() = pages.size

    override fun pageBytes(index: Int): ByteArray = pages[index].readBytes()

    override fun close() = Unit

    companion object {
        private const val KEEP = 3

        fun open(file: File, cacheDir: File): RarComic {
            val root = File(cacheDir, "cbr").apply { mkdirs() }
            val id = "${file.path}|${file.length()}|${file.lastModified()}".hashCode().toUInt().toString(16)
            val dir = File(root, id)
            val manifest = File(dir, "pages.txt")

            if (!manifest.exists()) {
                dir.deleteRecursively()
                dir.mkdirs()
                val names = mutableListOf<String>()
                try {
                    readEntries(file) { name, archive ->
                        if (ComicArchive.isPage(name)) {
                            extractTo(archive, File(dir, names.size.toString()))
                            names += name
                        }
                        true
                    }
                } catch (e: Exception) {
                    dir.deleteRecursively()
                    throw e
                }
                // Written last, so a half-unpacked comic is never mistaken for a finished one.
                val tmp = File(dir, "pages.tmp")
                tmp.writeText(names.joinToString("\n"))
                tmp.renameTo(manifest)
            }

            dir.setLastModified(System.currentTimeMillis())
            root.listFiles()
                ?.filter { it.isDirectory && it != dir }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEEP - 1)
                ?.forEach { it.deleteRecursively() }

            val names = manifest.readText().let { if (it.isEmpty()) emptyList() else it.split('\n') }
            val order = names.indices.sortedWith(compareBy(NaturalOrder) { names[it] })
            return RarComic(order.map { File(dir, it.toString()) })
        }

        fun firstPageBytes(file: File): ByteArray? {
            val names = mutableListOf<String>()
            readEntries(file) { name, _ ->
                if (ComicArchive.isPage(name)) names += name
                true
            }
            val first = names.minWithOrNull(NaturalOrder) ?: return null
            var bytes: ByteArray? = null
            readEntries(file) { name, archive ->
                if (name == first) {
                    val tmp = File.createTempFile("cover", ".img")
                    try {
                        extractTo(archive, tmp)
                        bytes = tmp.readBytes()
                    } finally {
                        tmp.delete()
                    }
                    false
                } else {
                    true
                }
            }
            return bytes
        }

        /** Walks the archive's files; [onEntry] returns false to stop early. */
        private fun readEntries(file: File, onEntry: (name: String, archive: Long) -> Boolean) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val archive = Archive.readNew()
                try {
                    // Android's native code has no locale, so libarchive would try to turn file names
                    // into plain ASCII and fail on anything else (e.g. RARs with UTF-16 names).
                    Archive.setCharset(archive, "UTF-8".toByteArray())
                    Archive.readSupportFilterAll(archive)
                    Archive.readSupportFormatAll(archive)
                    Archive.readOpenFd(archive, pfd.fd, 64 * 1024)
                    var failures = 0
                    while (true) {
                        // libarchive also throws on mere warnings (e.g. an oddly encoded file name).
                        // Skip that entry; a truly broken archive fails repeatedly and is reported.
                        val entry = try {
                            Archive.readNextHeader(archive).also { failures = 0 }
                        } catch (e: ArchiveException) {
                            if (++failures >= 3) throw e
                            continue
                        }
                        if (entry == 0L) break
                        if (ArchiveEntry.filetype(entry) != ArchiveEntry.AE_IFREG) continue
                        val name = ArchiveEntry.pathnameUtf8(entry)
                            ?: ArchiveEntry.pathname(entry)?.let { String(it, Charsets.UTF_8) }
                            ?: continue
                        if (!onEntry(name, archive)) break
                    }
                } catch (e: ArchiveException) {
                    throw IOException("Couldn't read this CBR (${e.message})", e)
                } finally {
                    runCatching { Archive.readFree(archive) }
                }
            }
        }

        private fun extractTo(archive: Long, out: File) {
            ParcelFileDescriptor.open(
                out,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            ).use { Archive.readDataIntoFd(archive, it.fd) }
        }
    }
}
