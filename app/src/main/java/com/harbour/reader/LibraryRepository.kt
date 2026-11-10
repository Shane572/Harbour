package com.harbour.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScanResult(val comics: List<Comic>, val failed: List<LibraryRoot>)

/** Remembers the folders you've picked and finds every .cbz inside them. */
class LibraryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "library.json")

    fun roots(): List<LibraryRoot> {
        val raw = prefs.getString("roots", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LibraryRoot(o.getString("uri"), o.getString("name"))
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRoots(roots: List<LibraryRoot>) {
        val arr = JSONArray()
        roots.forEach { arr.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        prefs.edit().putString("roots", arr.toString()).apply()
    }

    fun addRoot(tree: Uri): List<LibraryRoot> {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = displayName(tree)
            ?: tree.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: "Folder"
        val updated = roots().filterNot { it.uri == tree.toString() } + LibraryRoot(tree.toString(), name)
        saveRoots(updated)
        return updated
    }

    fun addFileRoot(dir: File): List<LibraryRoot> {
        val uri = Uri.fromFile(dir).toString()
        val name = dir.name.ifEmpty { "Storage" }
        val updated = roots().filterNot { it.uri == uri } + LibraryRoot(uri, name)
        saveRoots(updated)
        return updated
    }

    fun removeRoot(root: LibraryRoot): List<LibraryRoot> {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(root.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val updated = roots().filterNot { it.uri == root.uri }
        saveRoots(updated)
        return updated
    }

    private fun displayName(tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun loadCached(): List<Comic> = runCatching {
        if (!cacheFile.exists()) return emptyList()
        val arr = JSONArray(cacheFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Comic(
                uri = o.getString("uri"),
                name = o.getString("name"),
                seriesPath = o.getString("path"),
                seriesName = o.getString("series"),
                size = o.optLong("size"),
                modified = o.optLong("mod"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveCache(comics: List<Comic>) {
        val arr = JSONArray()
        comics.forEach {
            arr.put(
                JSONObject()
                    .put("uri", it.uri).put("name", it.name).put("path", it.seriesPath)
                    .put("series", it.seriesName).put("size", it.size).put("mod", it.modified),
            )
        }
        runCatching { cacheFile.writeText(arr.toString()) }
    }

    suspend fun scan(roots: List<LibraryRoot>): ScanResult = withContext(Dispatchers.IO) {
        val found = mutableListOf<Comic>()
        val failed = mutableListOf<LibraryRoot>()
        for (root in roots) {
            try {
                val uri = Uri.parse(root.uri)
                if (uri.scheme == "file") {
                    val dir = File(uri.path ?: "")
                    if (!dir.isDirectory || dir.listFiles() == null) throw SecurityException("Can't read ${dir.path}")
                    walkFiles(dir, root.name, found)
                } else {
                    walk(uri, DocumentsContract.getTreeDocumentId(uri), root.name, found)
                }
            } catch (e: Exception) {
                failed += root
            }
        }
        val sorted = found.sortedWith { a, b ->
            val c = NaturalOrder.compare(a.seriesPath, b.seriesPath)
            if (c != 0) c else NaturalOrder.compare(a.name, b.name)
        }
        saveCache(sorted)
        ScanResult(sorted, failed)
    }

    private fun walk(tree: Uri, docId: String, path: String, out: MutableList<Comic>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val dirs = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val mime = c.getString(2)
                if (mime == Document.MIME_TYPE_DIR) {
                    dirs += id to name
                } else if (name.endsWith(".cbz", ignoreCase = true)) {
                    out += Comic(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                        name = name,
                        seriesPath = path,
                        seriesName = path.substringAfterLast('/'),
                        size = if (c.isNull(3)) 0L else c.getLong(3),
                        modified = if (c.isNull(4)) 0L else c.getLong(4),
                    )
                }
            }
        }
        for ((id, name) in dirs) walk(tree, id, "$path/$name", out)
    }

    /** Same as [walk], for folders picked with Harbour's own browser (plain storage paths). */
    private fun walkFiles(dir: File, path: String, out: MutableList<Comic>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                walkFiles(f, "$path/${f.name}", out)
            } else if (f.name.endsWith(".cbz", ignoreCase = true)) {
                out += Comic(
                    uri = Uri.fromFile(f).toString(),
                    name = f.name,
                    seriesPath = path,
                    seriesName = path.substringAfterLast('/'),
                    size = f.length(),
                    modified = f.lastModified(),
                )
            }
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
