package com.folio.reader

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
