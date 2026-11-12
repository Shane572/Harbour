package com.harbour.reader

import android.content.Context
import org.json.JSONObject

/**
 * One line of a reading guide: a numbered step to read ([type] "entry"), a section heading, or a
 * note. Shane's guides are stored word for word in assets/guides.json.
 */
data class GuideBlock(
    val type: String,
    val n: String = "",
    val read: String = "",
    val label: String = "",
    val why: String = "",
    val extra: String = "",
    val text: String = "",
) {
    val isEntry: Boolean get() = type == "entry"
    val isHeading: Boolean get() = type == "heading"
}

data class Guide(val id: String, val title: String, val about: String, val blocks: List<GuideBlock>) {
    /** Indexes (into [blocks]) of the steps to read, in order. */
    val entries: List<Int> get() = blocks.indices.filter { blocks[it].isEntry }

    /** The steps in the section a block belongs to (between the headings around it). */
    fun sectionOf(index: Int): List<Int> {
        val start = (index downTo 0).firstOrNull { blocks[it].isHeading } ?: -1
        val end = (index + 1 until blocks.size).firstOrNull { blocks[it].isHeading } ?: blocks.size
        return (start + 1 until end).filter { blocks[it].isEntry }
    }
}

/** Where you are with a step: not yet, read, or skipped (left out on purpose). */
enum class StepState { None, Read, Skipped }

object Guides {
    fun load(context: Context): List<Guide> {
        val json = JSONObject(context.assets.open("guides.json").bufferedReader().use { it.readText() })
        val saved = json.getJSONArray("saved")
        return (0 until saved.length()).map { i ->
            val g = saved.getJSONObject(i)
            val blocks = g.getJSONArray("blocks")
            Guide(
                id = g.getString("id"),
                title = g.getString("title"),
                about = g.optString("about"),
                blocks = (0 until blocks.length()).map { j ->
                    val b = blocks.getJSONObject(j)
                    GuideBlock(
                        type = b.getString("type"),
                        n = b.optString("n"),
                        read = b.optString("read"),
                        label = b.optString("label"),
                        why = b.optString("why"),
                        extra = b.optString("extra"),
                        text = b.optString("text"),
                    )
                },
            )
        }
    }
}
