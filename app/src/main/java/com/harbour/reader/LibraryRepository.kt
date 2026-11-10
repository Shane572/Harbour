package com.harbour.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScanResult(val comics: List<Comic>, val failed: List<LibraryRoot>)

/** Remembers the folders you've picked and finds every comic inside them. Folders only decide where to look. */
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

    fun addRoot(dir: File): List<LibraryRoot> {
        val uri = Uri.fromFile(dir).toString()
        val name = dir.name.ifEmpty { "Storage" }
        val updated = roots().filterNot { it.uri == uri } + LibraryRoot(uri, name)
        saveRoots(updated)
        return updated
    }

    fun removeRoot(root: LibraryRoot): List<LibraryRoot> {
        val updated = roots().filterNot { it.uri == root.uri }
        saveRoots(updated)
        return updated
    }

    fun loadCached(): List<Comic> = runCatching {
        if (!cacheFile.exists()) return emptyList()
        val arr = JSONArray(cacheFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Comic(
                uri = o.getString("uri"),
                name = o.getString("name"),
                folderPath = o.getString("path"),
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
                    .put("uri", it.uri).put("name", it.name).put("path", it.folderPath)
                    .put("size", it.size).put("mod", it.modified),
            )
        }
        runCatching { cacheFile.writeText(arr.toString()) }
    }

    suspend fun scan(roots: List<LibraryRoot>): ScanResult = withContext(Dispatchers.IO) {
        val found = mutableListOf<Comic>()
        val failed = mutableListOf<LibraryRoot>()
        for (root in roots) {
            // Roots are file:// URIs. Anything else is a folder from an early build's system picker.
            val uri = Uri.parse(root.uri)
            val dir = File(uri.path ?: "")
            if (uri.scheme != "file" || !dir.isDirectory || dir.listFiles() == null) {
                failed += root
                continue
            }
            walk(dir, root.name, found)
        }
        val sorted = found.sortedWith(compareBy(NaturalOrder) { it.name })
        saveCache(sorted)
        ScanResult(sorted, failed)
    }

    private fun walk(dir: File, path: String, out: MutableList<Comic>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                walk(f, "$path/${f.name}", out)
            } else if (f.name.endsWith(".cbz", ignoreCase = true)) {
                out += Comic(
                    uri = Uri.fromFile(f).toString(),
                    name = f.name,
                    folderPath = path,
                    size = f.length(),
                    modified = f.lastModified(),
                )
            }
        }
    }
}
