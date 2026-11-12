package com.harbour.reader

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A page saved to the gallery: a JPEG in the tablet's Pictures/Harbour, so it outlives the comic. */
data class SavedPage(
    val id: String,
    val file: String,
    val comicKey: String,
    val title: String,
    /** Your own name for it, if you've renamed it. */
    val name: String?,
    val group: String?,
    val page: Int,
    val pages: Int,
    val time: Long,
) {
    val displayName: String get() = name ?: title
}

/**
 * Saved pages are real pictures on the tablet: Pictures/Harbour, so they show up in its Gallery
 * app, can be copied to a PC, and stay put if the comic (or Harbour) is deleted. The list of them,
 * with where each came from, is Harbour/gallery.json next to the backup.
 */
object GalleryStore {
    fun dir(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Harbour")

    private fun index(): File = File(Environment.getExternalStorageDirectory(), "Harbour/gallery.json")

    // Builds 30–33 kept pictures in Harbour/Gallery, which the Gallery app doesn't show.
    private fun oldDir(): File = File(Environment.getExternalStorageDirectory(), "Harbour/Gallery")

    fun imageOf(p: SavedPage): File = File(dir(), p.file)

    /** Newest first. Entries whose picture has gone (deleted in the Gallery app, say) are dropped. */
    fun load(context: Context): List<SavedPage> {
        migrate(context)
        val f = index()
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedPage(
                id = o.optString("id").ifEmpty { o.getString("file") },
                file = o.getString("file"),
                comicKey = o.getString("comic"),
                title = o.optString("title"),
                name = o.optString("name").ifEmpty { null },
                group = o.optString("group").ifEmpty { null },
                page = o.optInt("page"),
                pages = o.optInt("pages"),
                time = o.optLong("time"),
            )
        }.filter { imageOf(it).exists() }.sortedByDescending { it.time }
    }

    fun save(list: List<SavedPage>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("file", p.file)
                    .put("comic", p.comicKey)
                    .put("title", p.title)
                    .put("name", p.name ?: "")
                    .put("group", p.group ?: "")
                    .put("page", p.page)
                    .put("pages", p.pages)
                    .put("time", p.time),
            )
        }
        val index = index()
        index.parentFile?.mkdirs()
        val tmp = File(index.parentFile, "gallery.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(index)) {
            index.delete()
            tmp.renameTo(index)
        }
    }

    /** Writes the picture as "Title - p12.jpg" (numbered if that name's taken) and tells the Gallery app. */
    fun write(context: Context, bitmap: Bitmap, comic: Comic, group: String?, page: Int, pages: Int): SavedPage {
        val dir = dir().apply { mkdirs() }
        val name = freeName("${comic.title} - p${page + 1}", except = null)
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        scan(context, listOf(File(dir, name).path))
        return SavedPage(UUID.randomUUID().toString(), name, comic.key, comic.title, null, group, page, pages, System.currentTimeMillis())
    }

    /** Renames a picture (the file too, so the Gallery app shows the new name). Blank goes back to the comic's title. */
    fun rename(context: Context, p: SavedPage, newName: String): SavedPage {
        val name = newName.trim().ifEmpty { null }?.takeIf { it != p.title }
        val file = freeName(name ?: "${p.title} - p${p.page + 1}", except = p.file)
        if (file != p.file) {
            val from = imageOf(p)
            val to = File(dir(), file)
            if (!from.renameTo(to)) return p.copy(name = name)
            scan(context, listOf(from.path, to.path))
        }
        return p.copy(file = file, name = name)
    }

    /** Deletes a picture from the tablet, Gallery app included. */
    fun delete(context: Context, p: SavedPage) {
        val f = imageOf(p)
        f.delete()
        scan(context, listOf(f.path))
    }

    // A file name in Pictures/Harbour from [base], numbered if it's taken by another picture.
    private fun freeName(base: String, except: String?): String {
        val clean = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).trim().ifEmpty { "Page" }
        var name = "$clean.jpg"
        var n = 2
        while (name != except && File(dir(), name).exists()) name = "$clean (${n++}).jpg"
        return name
    }

    private fun scan(context: Context, paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun migrate(context: Context) {
        val old = oldDir()
        if (!old.isDirectory) return
        val dir = dir().apply { mkdirs() }
        val moved = mutableListOf<String>()
        old.listFiles()?.forEach { f ->
            when {
                f.name == "gallery.json" -> if (!index().exists()) move(f, index()) else f.delete()
                f.name.endsWith(".jpg", ignoreCase = true) -> {
                    val to = File(dir, f.name)
                    if (!to.exists() && move(f, to)) moved += to.path
                }
            }
        }
        old.delete() // only goes if it's now empty
        scan(context, moved)
    }

    private fun move(from: File, to: File): Boolean {
        to.parentFile?.mkdirs()
        if (from.renameTo(to)) return true
        return runCatching {
            from.copyTo(to)
            from.delete()
            true
        }.getOrDefault(false)
    }
}
