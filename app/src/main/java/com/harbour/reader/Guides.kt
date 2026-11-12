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
}

/** What of one step is in the library: the comics found, and how many of its issues turned up. */
data class GuideMatch(val comics: List<Comic>, val found: Int, val wanted: Int)

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

    /** A series and the issue numbers a step asks for, e.g. "Titans (2016) #1–36". */
    private class Ref(val words: List<String>, val year: String?, val numbers: List<Int>, val fallback: List<String>?)

    // Shorthand used in the crossover orders.
    private val ALIASES = mapOf("gl" to "green lantern", "glc" to "green lantern corps", "bn" to "blackest night")
    private val REF = Regex("""([A-Za-z][^#,→+\n]*?)?\s*#\s*(\d[\d,]*)(?:\s*[–-]\s*(\d+))?""")
    private val YEAR = Regex("""\((\d{4})\)""")
    private val NOT_WORD = Regex("""[^a-z0-9' ]""")

    private fun words(series: String): List<String> {
        val plain = series.lowercase().replace("'", "").replace("’", "")
        val cleaned = NOT_WORD.replace(YEAR.replace(plain, " "), " ").replace(Regex(" +"), " ").trim()
        val expanded = ALIASES[cleaned] ?: cleaned
        return expanded.split(' ').filter { it.isNotBlank() && it != "the" && it != "and" }
    }

    private fun refs(text: String): List<Ref> {
        val out = mutableListOf<Ref>()
        var lastSeries: String? = null
        for (m in REF.findAll(text)) {
            // Lead-ins like "Read the crossover involving Green Lantern" or "Read in this order:" aren't the name.
            var series = m.groupValues[1].substringAfterLast("involving ").substringAfterLast("order:")
                .trim().trimEnd('–', '-', '—', ' ').ifEmpty { lastSeries } ?: continue
            // "Wonder Woman (2016) #1–25 + Annual #1" means that series' annual, not any annual.
            if (words(series) == listOf("annual") && lastSeries != null) series = "$lastSeries Annual"
            lastSeries = series
            val start = m.groupValues[2].replace(",", "").toIntOrNull() ?: continue
            val end = m.groupValues[3].toIntOrNull()?.takeIf { it >= start && it - start < 400 } ?: start
            // "Emerald Twilight — Green Lantern": if the whole name finds nothing, try the part after the dash.
            val fallback = series.substringAfterLast(" — ", "").takeIf { it.isNotBlank() }?.let(::words)
            out += Ref(words(series), YEAR.find(series)?.groupValues?.get(1), (start..end).toList(), fallback)
        }
        return out
    }

    /** One comic's searchable text and the whole numbers in it, worked out once per library. */
    class Indexed(val comic: Comic, val text: String, val numbers: Set<Int>)

    fun index(comics: List<Comic>, groupName: (Comic) -> String?): List<Indexed> = comics.map { c ->
        val text = "${groupName(c) ?: ""} ${c.title} ${c.name}".lowercase().replace("'", "").replace("’", "")
        Indexed(c, text, Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toSet())
    }

    /**
     * Finds a step's issues in the library, the same way search matches (group name and title,
     * whole issue numbers). When a series name matches several volumes, the year in the step
     * picks between them if the files mention it.
     */
    fun match(block: GuideBlock, library: List<Indexed>): GuideMatch {
        val text = if (block.read.contains('#')) block.read else "${block.read} ${block.why}"
        val found = mutableListOf<Comic>()
        var have = 0
        var wanted = 0
        for (ref in refs(text)) {
            var series = library.filter { e -> ref.words.all { it in e.text } }
            if (series.isEmpty() && ref.fallback != null) series = library.filter { e -> ref.fallback.all { it in e.text } }
            for (n in ref.numbers) {
                wanted++
                var hits = series.filter { n in it.numbers }
                if (hits.size > 1 && ref.year != null) {
                    hits.filter { ref.year in it.text }.takeIf { it.isNotEmpty() }?.let { hits = it }
                }
                if (hits.isNotEmpty()) {
                    have++
                    hits.forEach { if (it.comic !in found) found += it.comic }
                }
            }
        }
        return GuideMatch(found, have, wanted)
    }
}
