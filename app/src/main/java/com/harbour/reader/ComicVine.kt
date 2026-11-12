package com.harbour.reader

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A Comic Vine page opened lately, for the Recently viewed list. [type] is character, volume, issue or person. */
data class CvRecent(val type: String, val id: Int, val name: String, val image: String?, val detail: String)

/** A guide step's cover, saved on the tablet, and what it is ("Uncanny X-Men (1963) #176 · 1983"). */
data class StepCover(val file: File, val caption: String)

/**
 * Covers for guide steps from Comic Vine, with Shane's own free API key (personal, non-commercial
 * use). Each lookup is two requests (find the series, then the issue) and the cover is saved, so a
 * step only ever goes online once. The key is kept in its own prefs file: not in the backup, and
 * not wiped by a restore.
 */
object ComicVine {
    private const val API = "https://comicvine.gamespot.com/api/"
    private const val AGENT = "Harbour/1.0 (personal comic reader)"

    class LookupException(message: String) : Exception(message)

    private fun prefs(context: Context) = context.getSharedPreferences("comicvine", Context.MODE_PRIVATE)

    fun key(context: Context): String = prefs(context).getString("key", "") ?: ""

    fun setKey(context: Context, key: String) {
        prefs(context).edit().putString("key", key.trim()).apply()
    }

    private fun dir(context: Context) = File(context.filesDir, "guide_covers").apply { mkdirs() }

    /** What a step points at: the first series and issue it names. */
    private data class Target(val name: String, val year: Int?, val number: String?)

    private val REF = Regex("""([A-Za-z][^#,→+\n]*?)?\s*#\s*(\d[\d,]*)""")
    private val YEAR = Regex("""\((\d{4})\)""")
    private val ALIASES = mapOf("GL" to "Green Lantern", "GLC" to "Green Lantern Corps", "BN" to "Blackest Night")

    private fun target(block: GuideBlock): Target? {
        val text = if (block.read.contains('#') || !block.why.contains('#')) block.read else block.why
        val m = REF.find(text)
        val raw = if (m != null) m.groupValues[1] else text
        var name = raw.substringAfterLast("involving ").substringAfterLast("order:")
        val year = YEAR.find(name)?.groupValues?.get(1)?.toIntOrNull()
        name = YEAR.replace(name, " ")
            .replace(" — ", " ").replace(" – ", " ")
            .trim().trimEnd('–', '-', '—', ' ', ':')
        name = ALIASES[name] ?: name
        if (name.isBlank()) return null
        return Target(name, year, m?.groupValues?.get(2)?.replace(",", ""))
    }

    /** The saved cover for a step, if it's been looked up before. */
    fun cached(context: Context, block: GuideBlock): StepCover? {
        val t = target(block) ?: return null
        val entry = prefs(context).getString(cacheKey(t), null) ?: return null
        val file = File(dir(context), entry.substringBefore('|'))
        return if (file.exists()) StepCover(file, entry.substringAfter('|')) else null
    }

    private fun cacheKey(t: Target) = "cover:${t.name.lowercase()}|${t.year ?: ""}|${t.number ?: ""}"

    /** Finds and saves a step's cover. Runs off the main thread; throws with a readable message. */
    fun lookup(context: Context, block: GuideBlock): StepCover {
        cached(context, block)?.let { return it }
        val key = key(context)
        if (key.isBlank()) throw LookupException("Add your Comic Vine key in Settings → Comic Vine to see covers.")
        val t = target(block) ?: throw LookupException("This step doesn't name an issue to look up.")

        val volumes = get(
            key,
            "search/?resources=volume&limit=20&field_list=id,name,start_year,count_of_issues,publisher,image" +
                "&query=" + URLEncoder.encode(t.name, "UTF-8"),
        ).optJSONArray("results") ?: throw LookupException("Comic Vine didn't find ${t.name}.")
        val n = t.number?.toIntOrNull()
        val wanted = norm(t.name)
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until volumes.length()) {
            val v = volumes.getJSONObject(i)
            val name = norm(v.optString("name"))
            val count = v.optInt("count_of_issues")
            val start = v.optString("start_year").toIntOrNull()
            val publisher = v.optJSONObject("publisher")?.optString("name") ?: ""
            var score = 0
            if (name == wanted) score += 100 else if (name.contains(wanted) || wanted.contains(name)) score += 20 else score -= 40
            // A guide's year is usually when the series began ("Titans (2016)"), but sometimes when the
            // run began inside an older series ("Wonder Woman (2003) #195" is the 1987 series). So:
            // an exact start year wins, then the series that began closest before it.
            if (t.year != null) {
                score += when {
                    start == null -> -20
                    start == t.year -> 60
                    start < t.year -> 40 - minOf(40, (t.year - start) / 2)
                    else -> -20
                }
            }
            if (publisher.contains("DC") || publisher.contains("Marvel")) score += 10
            if (n != null) score += if (count >= n) 20 else -60
            score += minOf(count, 300) / 30 // the long-running main series over reprints and one-offs
            // On a tie, the older series: with no year given, classic runs are the likelier meaning.
            if (score > bestScore || (score == bestScore && start != null && start < (best?.optString("start_year")?.toIntOrNull() ?: Int.MAX_VALUE))) {
                bestScore = score
                best = v
            }
        }
        val volume = best ?: throw LookupException("Comic Vine didn't find ${t.name}.")
        // Without an issue number there's nothing to check against, so only a series that's really
        // called that will do (not "Wonder Woman" for "Wonder Woman: Year One").
        if (t.number == null && !norm(volume.optString("name")).contains(wanted)) {
            throw LookupException("Comic Vine has no series called ${t.name}, so there's no cover to show.")
        }
        val volumeTitle = volume.optString("name") + (volume.optString("start_year").takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")

        var imageUrl: String? = null
        var caption = volumeTitle
        if (t.number != null) {
            val issues = get(
                key,
                "issues/?field_list=id,issue_number,image,cover_date&filter=volume:${volume.getInt("id")},issue_number:${t.number}",
            ).optJSONArray("results")
            val issue = issues?.takeIf { it.length() > 0 }?.getJSONObject(0)
            if (issue != null) {
                imageUrl = imageOf(issue.optJSONObject("image"))
                caption = "$volumeTitle #${t.number}" + (issue.optString("cover_date").take(4).takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            }
        }
        // No issue number (or that issue isn't listed): the series' own picture, which is its first cover.
        if (imageUrl == null) imageUrl = imageOf(volume.optJSONObject("image"))
        if (imageUrl == null) throw LookupException("Comic Vine has no cover for $caption.")

        val file = File(dir(context), "${cacheKey(t).hashCode().toUInt().toString(16)}.jpg")
        download(imageUrl, file)
        prefs(context).edit().putString(cacheKey(t), "${file.name}|$caption").apply()
        return StepCover(file, caption)
    }

    // Browsing: any Comic Vine request, saved on the tablet so pages open offline afterwards and each
    // is only fetched again once it's a week old (or never, if the tablet's offline).

    private fun cacheDir(context: Context) = File(context.filesDir, "cv_cache").apply { mkdirs() }

    /** A Comic Vine API request ("character/4005-1699/?field_list=…"), from the saved copy when there is one. */
    fun fetch(context: Context, path: String, maxAgeHours: Long = 24 * 7): JSONObject {
        val file = File(cacheDir(context), path.hashCode().toUInt().toString(16) + "-" + path.length + ".json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeHours * 3_600_000
        if (fresh) runCatching { return JSONObject(file.readText()) }
        val key = key(context)
        if (key.isBlank()) throw LookupException("Add your Comic Vine key in Settings → Comic Vine first.")
        return try {
            get(key, path).also { json ->
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.toString())
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        } catch (e: Exception) {
            // Offline or refused: an older saved copy is better than nothing.
            if (file.exists()) runCatching { return JSONObject(file.readText()) }
            throw e
        }
    }

    /** A Comic Vine picture, downloaded once into the cache folder (Android may clear it if space runs low). */
    fun image(context: Context, url: String): File {
        val dir = File(context.cacheDir, "cv_images").apply { mkdirs() }
        val file = File(dir, url.hashCode().toUInt().toString(16) + "-" + url.length + ".img")
        if (!file.exists()) download(url, file)
        return file
    }

    /** How much space browsing has saved, and clearing it. */
    fun savedBytes(context: Context): Long =
        (cacheDir(context).listFiles()?.sumOf { it.length() } ?: 0L) +
            (File(context.cacheDir, "cv_images").listFiles()?.sumOf { it.length() } ?: 0L)

    fun clearSaved(context: Context) {
        cacheDir(context).deleteRecursively()
        File(context.cacheDir, "cv_images").deleteRecursively()
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("^the "), "").replace(Regex("[^a-z0-9]"), "")

    private fun imageOf(image: JSONObject?): String? =
        image?.let { i -> listOf("super_url", "medium_url", "original_url").map { i.optString(it) }.firstOrNull { it.startsWith("http") } }

    private fun get(key: String, path: String): JSONObject {
        val sep = if (path.contains('?')) "&" else "?"
        val conn = URL("$API$path${sep}format=json&api_key=${URLEncoder.encode(key, "UTF-8")}").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", AGENT)
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) throw LookupException("Comic Vine turned the request down. Check your key in Settings.")
            if (code == 420 || code == 429) throw LookupException("Comic Vine's hourly limit was reached. Try again later.")
            if (code != 200) throw IOException("Comic Vine answered $code")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            when (json.optInt("status_code", 1)) {
                1 -> return json
                100 -> throw LookupException("That Comic Vine key didn't work. Check it in Settings → Comic Vine.")
                else -> throw LookupException("Comic Vine: ${json.optString("error", "lookup failed")}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, to: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", AGENT)
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw LookupException("Couldn't download the cover (${conn.responseCode}).")
            val tmp = File(to.path + ".tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(to)) {
                to.delete()
                tmp.renameTo(to)
            }
        } finally {
            conn.disconnect()
        }
    }
}
