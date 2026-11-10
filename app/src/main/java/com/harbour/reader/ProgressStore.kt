package com.harbour.reader

import android.content.Context

/** Last page read for each comic. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun all(): Map<String, Progress> = prefs.all.mapNotNull { (k, v) ->
        (v as? String)?.let(::parse)?.let { k to it }
    }.toMap()

    fun save(key: String, p: Progress) {
        prefs.edit().putString(key, "${p.page}|${p.total}|${p.time}").apply()
    }

    fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun parse(s: String): Progress? {
        val parts = s.split('|')
        if (parts.size != 3) return null
        val page = parts[0].toIntOrNull() ?: return null
        val total = parts[1].toIntOrNull() ?: return null
        val time = parts[2].toLongOrNull() ?: return null
        return Progress(page, total, time)
    }
}

/** Where you are in each book: chapter, how far through it, and how many chapters there are. */
class BookProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("book_progress", Context.MODE_PRIVATE)

    fun all(): Map<String, BookProgress> = prefs.all.mapNotNull { (k, v) ->
        (v as? String)?.let(::parse)?.let { k to it }
    }.toMap()

    fun save(key: String, p: BookProgress) {
        prefs.edit().putString(key, "${p.chapter}|${p.fraction}|${p.chapters}|${p.time}").apply()
    }

    fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun parse(s: String): BookProgress? {
        val parts = s.split('|')
        if (parts.size != 4) return null
        return BookProgress(
            chapter = parts[0].toIntOrNull() ?: return null,
            fraction = parts[1].toFloatOrNull() ?: return null,
            chapters = parts[2].toIntOrNull() ?: return null,
            time = parts[3].toLongOrNull() ?: return null,
        )
    }
}
