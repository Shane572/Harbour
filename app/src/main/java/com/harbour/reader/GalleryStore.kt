package com.harbour.reader

import android.graphics.Bitmap
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A page saved to the gallery. The picture is a JPEG in Harbour/Gallery, so it outlives the app. */
data class SavedPage(
    val file: String,
    val comicKey: String,
    val title: String,
    val group: String?,
    val page: Int,
    val pages: Int,
    val time: Long,
)

/**
 * Saved pages live on the tablet in Harbour/Gallery: the pictures plus gallery.json listing them,
 * next to the backup. Reinstalling Harbour keeps them, and they can be copied to a PC.
 */
object GalleryStore {
    fun dir(): File = File(Environment.getExternalStorageDirectory(), "Harbour/Gallery")

    private fun index(): File = File(dir(), "gallery.json")

    fun imageOf(p: SavedPage): File = File(dir(), p.file)

    /** Newest first. Entries whose picture has gone are dropped. */
    fun load(): List<SavedPage> {
        val f = index()
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedPage(
                file = o.getString("file"),
                comicKey = o.getString("comic"),
                title = o.optString("title"),
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
                    .put("file", p.file)
                    .put("comic", p.comicKey)
                    .put("title", p.title)
                    .put("group", p.group ?: "")
                    .put("page", p.page)
                    .put("pages", p.pages)
                    .put("time", p.time),
            )
        }
        dir().mkdirs()
        val tmp = File(dir(), "gallery.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(index())) {
            index().delete()
            tmp.renameTo(index())
        }
    }

    /** Writes the picture as "Title - p12.jpg" (numbered if that name's taken). */
    fun write(bitmap: Bitmap, comic: Comic, group: String?, page: Int, pages: Int): SavedPage {
        val dir = dir().apply { mkdirs() }
        val base = comic.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).trim().ifEmpty { "Page" } + " - p${page + 1}"
        var name = "$base.jpg"
        var n = 2
        while (File(dir, name).exists()) name = "$base (${n++}).jpg"
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return SavedPage(name, comic.key, comic.title, group, page, pages, System.currentTimeMillis())
    }
}
