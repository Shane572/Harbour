package com.harbour.reader

import android.content.Context
import android.content.Intent
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything Shane has set up by hand (groups, read orders, covers, Will Read, reading progress,
 * reading modes and settings) saved to one file on the tablet's storage, so a reinstall or a
 * new tablet doesn't lose it. Covers and the library list are rebuilt by a rescan, so they're left out.
 */
object Backup {
    private val PREFS = listOf("settings", "progress", "book_progress", "library", "guide_progress")

    @Suppress("DEPRECATION")
    fun file(): File = File(Environment.getExternalStorageDirectory(), "Harbour/harbour-backup.json")

    /** When the backup was last written, or null if there isn't one (or it can't be seen yet). */
    fun lastSaved(): Long? = runCatching { file().takeIf { it.isFile }?.lastModified() }.getOrNull()

    fun write(context: Context) {
        val prefs = JSONObject()
        for (name in PREFS) {
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                encode(v)?.let { values.put(k, it) }
            }
            prefs.put(name, values)
        }
        val root = JSONObject()
            .put("version", 1)
            .put("saved", System.currentTimeMillis())
            .put("prefs", prefs)
        val groups = File(context.filesDir, "groups.json")
        if (groups.exists()) root.put("groups", JSONArray(groups.readText()))

        val out = file()
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(out)) {
            out.delete()
            tmp.renameTo(out)
        }
    }

    /** Replaces Harbour's data with the backup. The app must restart afterwards to load it. */
    fun restore(context: Context) {
        val root = JSONObject(file().readText())
        val prefs = root.getJSONObject("prefs")
        for (name in PREFS) {
            val values = prefs.optJSONObject(name) ?: continue
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.keys().forEach { k ->
                val e = values.getJSONObject(k)
                when (e.getString("t")) {
                    "s" -> edit.putString(k, e.getString("v"))
                    "b" -> edit.putBoolean(k, e.getBoolean("v"))
                    "i" -> edit.putInt(k, e.getInt("v"))
                    "l" -> edit.putLong(k, e.getLong("v"))
                    "f" -> edit.putFloat(k, e.getDouble("v").toFloat())
                    "set" -> {
                        val arr = e.getJSONArray("v")
                        edit.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            edit.commit()
        }
        root.optJSONArray("groups")?.let { File(context.filesDir, "groups.json").writeText(it.toString()) }
    }

    /** Starts Harbour again so everything is reloaded from the restored data. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun encode(v: Any?): JSONObject? = when (v) {
        is String -> JSONObject().put("t", "s").put("v", v)
        is Boolean -> JSONObject().put("t", "b").put("v", v)
        is Int -> JSONObject().put("t", "i").put("v", v)
        is Long -> JSONObject().put("t", "l").put("v", v)
        is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.filterIsInstance<String>()))
        else -> null
    }
}
