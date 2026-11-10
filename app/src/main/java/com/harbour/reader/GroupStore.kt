package com.harbour.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves Shane's groups (and which comics are in each) to a small JSON file. */
class GroupStore(context: Context) {
    private val file = File(context.filesDir, "groups.json")

    fun load(): List<Group> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val keys = o.getJSONArray("keys")
            val order = o.optJSONArray("order")
            Group(
                id = o.getString("id"),
                name = o.getString("name"),
                keys = (0 until keys.length()).map { keys.getString(it) },
                readOrder = order?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                coverKey = o.optString("cover").ifEmpty { null },
            )
        }
    }.getOrDefault(emptyList())

    fun save(groups: List<Group>) {
        val arr = JSONArray()
        groups.forEach { g ->
            arr.put(
                JSONObject().put("id", g.id).put("name", g.name)
                    .put("keys", JSONArray(g.keys)).put("order", JSONArray(g.readOrder))
                    .put("cover", g.coverKey),
            )
        }
        // Write then rename, so a crash mid-write can't wipe the groups.
        val tmp = File(file.path + ".tmp")
        runCatching {
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }
}
