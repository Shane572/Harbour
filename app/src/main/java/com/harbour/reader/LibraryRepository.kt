package com.harbour.reader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScanResult(val comics: List<Comic>, val books: List<Book>, val failed: List<LibraryRoot>)

/**
 * Remembers the folders you've picked and finds every comic and book inside them.
 * Folders only decide where to look; they don't organise anything.
 */
class LibraryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "library.json")
    private val booksFile = File(context.filesDir, "books.json")

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

    fun loadCached(): List<Comic> = readArray(cacheFile).map { o ->
        Comic(
            uri = o.getString("uri"),
            name = o.getString("name"),
            folderPath = o.getString("path"),
            size = o.optLong("size"),
            modified = o.optLong("mod"),
        )
    }

    fun loadCachedBooks(): List<Book> = readArray(booksFile).map { o ->
        Book(
            uri = o.getString("uri"),
            name = o.getString("name"),
            folderPath = o.getString("path"),
            size = o.optLong("size"),
            modified = o.optLong("mod"),
            bookTitle = o.optString("title"),
            author = o.optString("author"),
        )
    }

    private fun readArray(file: File): List<JSONObject> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun writeArray(file: File, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        runCatching { file.writeText(arr.toString()) }
    }

    suspend fun scan(roots: List<LibraryRoot>): ScanResult = withContext(Dispatchers.IO) {
        val comics = mutableListOf<Comic>()
        val bookFiles = mutableListOf<Pair<File, String>>()
        val failed = mutableListOf<LibraryRoot>()
        for (root in roots) {
            // Roots are file:// URIs. Anything else is a folder from an early build's system picker.
            val uri = Uri.parse(root.uri)
            val dir = File(uri.path ?: "")
            if (uri.scheme != "file" || !dir.isDirectory || dir.listFiles() == null) {
                failed += root
                continue
            }
            walk(dir, root.name, comics, bookFiles)
        }

        // Reading a book's title and author means opening it, so reuse what we know for unchanged files.
        val known = loadCachedBooks().associateBy { it.uri }
        val books = bookFiles.map { (f, path) ->
            val uri = Uri.fromFile(f).toString()
            val old = known[uri]
            if (old != null && old.size == f.length() && old.modified == f.lastModified() && old.folderPath == path) {
                old
            } else {
                val meta = runCatching { EpubBook.open(f).use { it.title to it.author } }.getOrNull()
                Book(uri, f.name, path, f.length(), f.lastModified(), meta?.first ?: "", meta?.second ?: "")
            }
        }

        val sortedComics = comics.sortedWith(compareBy(NaturalOrder) { it.name })
        val sortedBooks = books.sortedWith(compareBy(NaturalOrder) { it.title })
        writeArray(cacheFile, sortedComics.map {
            JSONObject().put("uri", it.uri).put("name", it.name).put("path", it.folderPath)
                .put("size", it.size).put("mod", it.modified)
        })
        writeArray(booksFile, sortedBooks.map {
            JSONObject().put("uri", it.uri).put("name", it.name).put("path", it.folderPath)
                .put("size", it.size).put("mod", it.modified).put("title", it.bookTitle).put("author", it.author)
        })
        ScanResult(sortedComics, sortedBooks, failed)
    }

    private fun walk(dir: File, path: String, comics: MutableList<Comic>, books: MutableList<Pair<File, String>>) {
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            when {
                f.isDirectory -> walk(f, "$path/${f.name}", comics, books)
                ComicArchive.isComicFile(f.name) -> comics += Comic(
                    uri = Uri.fromFile(f).toString(),
                    name = f.name,
                    folderPath = path,
                    size = f.length(),
                    modified = f.lastModified(),
                )
                f.name.endsWith(".epub", ignoreCase = true) -> books += f to path
            }
        }
    }
}
