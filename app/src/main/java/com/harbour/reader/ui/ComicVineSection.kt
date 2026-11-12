package com.harbour.reader.ui

import android.graphics.BitmapFactory
import android.text.Html
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.harbour.reader.ComicVine
import com.harbour.reader.CvRecent
import com.harbour.reader.MainViewModel
import com.harbour.reader.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

// ---------- Loading

/** A Comic Vine page loading, loaded, or failed with a message to show. */
private sealed interface Cv {
    data object Loading : Cv
    data class Ok(val json: JSONObject) : Cv
    data class Err(val message: String) : Cv
}

private fun errorText(e: Throwable): String =
    (e as? ComicVine.LookupException)?.message ?: "Couldn't reach Comic Vine. Check the tablet's internet connection."

@Composable
private fun rememberCv(path: String?): Cv {
    val context = LocalContext.current
    val state by produceState<Cv>(Cv.Loading, path) {
        if (path != null) {
            value = Cv.Loading
            value = withContext(Dispatchers.IO) {
                runCatching { ComicVine.fetch(context, path) }.fold({ Cv.Ok(it) }, { Cv.Err(errorText(it)) })
            }
        }
    }
    return state
}

private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun JSONObject.str(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.img(size: String = "medium_url"): String? =
    optJSONObject("image")?.let { it.str(size) ?: it.str("original_url") }

private fun JSONObject.list(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

private fun JSONObject.count(): Int = optString("count").toIntOrNull() ?: 0

/** "1983-12-01" as "December 1983". */
private fun formatDate(s: String?): String? {
    if (s.isNullOrBlank()) return null
    return runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s) ?: return null
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(d)
    }.getOrNull() ?: s
}

// ---------- Pictures

private object CvImages {
    val memory = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
}

@Composable
private fun CvImage(url: String?, modifier: Modifier, maxPixels: Long = 700_000, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val key = "$url|$maxPixels"
    val bmp by produceState(url?.let { CvImages.memory.get(key) }, key) {
        if (value == null && url != null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val file = ComicVine.image(context, url)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    var sample = 1
                    while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > maxPixels) sample *= 2
                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }.getOrNull()?.also { CvImages.memory.put(key, it) }
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HarbourColors.SurfaceHigh),
    ) {
        bmp?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize()) }
    }
}

// ---------- Write-ups (Comic Vine's descriptions are HTML)

private sealed interface Doc {
    val text: String
    data class Heading(override val text: String) : Doc
    data class Para(override val text: String) : Doc
    data class Bullet(override val text: String) : Doc
}

private val BLOCK = Regex("<(h[1-6]|p|li)(?:\\s[^>]*)?>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val SPACES = Regex("\\s+")

private fun parseDoc(html: String?): List<Doc> {
    if (html.isNullOrBlank()) return emptyList()
    val found = BLOCK.findAll(html).mapNotNull { m ->
        val text = Html.fromHtml(m.groupValues[2], Html.FROM_HTML_MODE_COMPACT).toString().replace(SPACES, " ").trim()
        if (text.isEmpty()) {
            null
        } else {
            when (m.groupValues[1].lowercase()) {
                "li" -> Doc.Bullet(text)
                "p" -> Doc.Para(text)
                else -> Doc.Heading(text)
            }
        }
    }.toList()
    if (found.isNotEmpty()) return found
    // Plain text with no paragraphs: show it as one.
    val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().replace(SPACES, " ").trim()
    return if (plain.isEmpty()) emptyList() else listOf(Doc.Para(plain))
}

/** A write-up: the first part shown, the rest behind Read more. */
@Composable
private fun DocView(html: String?, shown: Int = 8) {
    val blocks = remember(html) { parseDoc(html) }
    if (blocks.isEmpty()) return
    var all by remember(html) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (if (all) blocks else blocks.take(shown)).forEach { b ->
            when (b) {
                is Doc.Heading -> Text(b.text, style = MaterialTheme.typography.titleMedium, color = HarbourColors.Text, modifier = Modifier.padding(top = 6.dp))
                is Doc.Para -> Text(b.text, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Text)
                is Doc.Bullet -> Text("•  ${b.text}", style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Text)
            }
        }
        if (blocks.size > shown) {
            TextButton(onClick = { all = !all }) {
                Text(if (all) "Show less" else "Read more", color = HarbourColors.Accent)
            }
        }
    }
}

// ---------- Bits shared by the pages

@Composable
private fun PageTop(vm: MainViewModel, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::back, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HarbourColors.Text)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = HarbourColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = HarbourColors.Muted)
        }
    }
}

@Composable
private fun Fact(label: String, value: String?, onClick: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = HarbourColors.Muted, modifier = Modifier.width(130.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) HarbourColors.Accent else HarbourColors.Text,
            modifier = Modifier
                .weight(1f)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
    }
}

@Composable
private fun Chip(text: String, accent: Boolean = false, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (accent) HarbourColors.OnAccent else if (onClick != null) HarbourColors.Text else HarbourColors.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (accent) HarbourColors.Accent else HarbourColors.SurfaceHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A titled row of chips; long lists show the first [shown] with a Show all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, items: List<Pair<String, (() -> Unit)?>>, accent: Boolean = false, shown: Int = 24) {
    if (items.isEmpty()) return
    var all by remember(title, items.size) { mutableStateOf(false) }
    Column {
        SectionLabel(title)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (all) items else items.take(shown)).forEach { (text, onClick) -> Chip(text, accent, onClick) }
            if (items.size > shown) {
                Text(
                    if (all) "Show fewer" else "Show all ${items.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = HarbourColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { all = !all }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Loading(text: String = "Asking Comic Vine…") {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = HarbourColors.Accent, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
    }
}

@Composable
private fun Problem(message: String) {
    Hint("Couldn't load this", message)
}

/** A search result or list entry: picture, name, a line of detail, and a summary. */
@Composable
private fun CvCard(image: String?, title: String, subtitle: String?, summary: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HarbourColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        CvImage(image, Modifier.width(72.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = HarbourColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = HarbourColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun seriesLine(v: JSONObject): String = listOfNotNull(
    v.str("start_year"),
    v.optJSONObject("publisher")?.str("name"),
    v.optInt("count_of_issues").takeIf { it > 0 }?.let { if (it == 1) "1 issue" else "$it issues" },
).joinToString(" · ")

private fun openSeries(vm: MainViewModel, v: JSONObject) = vm.show(Section.CvSeries(v.optInt("id"), v.optString("name")))

private fun openCharacter(vm: MainViewModel, c: JSONObject) = vm.show(Section.CvCharacter(c.optInt("id"), c.optString("name")))

private fun openPerson(vm: MainViewModel, p: JSONObject) = vm.show(Section.CvPerson(p.optInt("id"), p.optString("name")))

private fun openIssue(vm: MainViewModel, i: JSONObject, title: String) = vm.show(Section.CvIssue(i.optInt("id"), title))

@Composable
private fun OpenOnSite(json: JSONObject) {
    val uri = LocalUriHandler.current
    json.str("site_detail_url")?.let { url ->
        TextButton(onClick = { runCatching { uri.openUri(url) } }) {
            Text("Open on Comic Vine", color = HarbourColors.Accent)
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }, key = key) { Box(Modifier.widthIn(max = 1100.dp)) { content() } }
}

private val CARD_CELL: Dp = 340.dp

// ---------- Search (the top search bar searches Comic Vine on these pages)

/** The cover-date range for a year (and month), as Comic Vine's "from|to" filter. */
private fun dateRange(year: Int?, month: Int?): String? {
    if (year == null) return null
    return if (month == null) {
        "$year-01-01|$year-12-31"
    } else {
        val ym = java.time.YearMonth.of(year, month)
        "${ym.atDay(1)}|${ym.atEndOfMonth()}"
    }
}

private fun monthName(month: Int): String = java.text.DateFormatSymbols.getInstance().months[month - 1]

/** "Released": any time, or a year (and month) by cover date. */
@Composable
private fun DateFilter(vm: MainViewModel) {
    var yearText by remember { mutableStateOf(vm.cvYear?.toString() ?: "") }
    var monthMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Released", style = MaterialTheme.typography.labelLarge, color = HarbourColors.Muted)
        Choice("Any time", vm.cvYear == null) {
            yearText = ""
            vm.setComicVineDate(null, null)
        }
        OutlinedTextField(
            value = yearText,
            onValueChange = { t ->
                val digits = t.filter { it.isDigit() }.take(4)
                yearText = digits
                val y = digits.toIntOrNull()?.takeIf { digits.length == 4 && it in 1930..2100 }
                vm.setComicVineDate(y, vm.cvMonth)
            },
            label = { Text("Year") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
        )
        Box {
            OutlinedButton(onClick = { monthMenu = true }, enabled = vm.cvYear != null) {
                Text(vm.cvMonth?.let(::monthName) ?: "All year")
            }
            DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                DropdownMenuItem(text = { Text("All year") }, onClick = {
                    monthMenu = false
                    vm.setComicVineDate(vm.cvYear, null)
                })
                (1..12).forEach { m ->
                    DropdownMenuItem(text = { Text(monthName(m)) }, onClick = {
                        monthMenu = false
                        vm.setComicVineDate(vm.cvYear, m)
                    })
                }
            }
        }
    }
}

@Composable
fun ComicVineSection(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val q = vm.cvQuery.trim()
    // Ask once typing pauses (Comic Vine limits lookups per hour), or straight away on Search.
    var asked by remember { mutableStateOf(q) }
    LaunchedEffect(q) {
        if (q.length < 2) {
            asked = ""
        } else {
            delay(800)
            asked = q
        }
    }
    LaunchedEffect(vm.cvSubmits) { if (q.length >= 2) asked = q }
    val result = rememberCv(
        asked.takeIf { it.length >= 2 }?.let {
            "search/?resources=character,volume,person&limit=40" +
                "&field_list=id,name,resource_type,image,deck,start_year,publisher,count_of_issues,count_of_issue_appearances" +
                "&query=" + encode(it)
        },
    )
    val all = (result as? Cv.Ok)?.json?.list("results") ?: emptyList()
    val year = vm.cvYear
    val range = dateRange(year, vm.cvMonth)
    val dateLabel = year?.let { y -> vm.cvMonth?.let { "${monthName(it)} $y" } ?: "$y" }

    // Issues by cover date: everything from then, or just the matching series' issues if there's a search.
    val volumeIds = all.filter { it.optString("resource_type") == "volume" }
        .filter { v -> year == null || (v.optString("start_year").toIntOrNull() ?: 0) <= year }
        .map { it.optInt("id") }
    val datedKey = Triple(range, asked, volumeIds)
    val dated = remember(datedKey) { mutableStateListOf<JSONObject>() }
    var datedTotal by remember(datedKey) { mutableIntStateOf(-1) }
    var datedLoading by remember(datedKey) { mutableStateOf(false) }
    var datedError by remember(datedKey) { mutableStateOf<String?>(null) }
    val datedReady = range != null && (asked.isBlank() || result is Cv.Ok)
    fun loadDated() {
        if (datedLoading || range == null) return
        if (asked.isNotBlank() && volumeIds.isEmpty()) {
            datedTotal = 0
            return
        }
        datedLoading = true
        datedError = null
        val offset = dated.size
        val filter = (if (asked.isBlank()) "" else "volume:" + volumeIds.joinToString("|") + ",") + "cover_date:$range"
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    ComicVine.fetch(
                        context,
                        "issues/?filter=$filter&sort=cover_date:asc&limit=100&offset=$offset" +
                            "&field_list=id,issue_number,name,image,cover_date,volume",
                    )
                }
            }.onSuccess { json ->
                datedTotal = json.optInt("number_of_total_results", 0)
                dated.addAll(json.list("results"))
            }.onFailure { datedError = errorText(it) }
            datedLoading = false
        }
    }
    LaunchedEffect(datedKey, datedReady) { if (datedReady && dated.isEmpty()) loadDated() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CARD_CELL),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full("header") {
            Column {
                Text("Comic Vine", style = MaterialTheme.typography.displaySmall, color = HarbourColors.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Search characters, series and creators with the search bar above, and pick a year (and month) " +
                        "to see what came out then. Pages you open are kept on the tablet, so they work offline afterwards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarbourColors.Muted,
                )
                Spacer(Modifier.height(12.dp))
                DateFilter(vm)
            }
        }
        if (vm.comicVineKey.isBlank()) {
            full("nokey") {
                Hint("Add your Comic Vine key", "Paste your free Comic Vine API key in Settings → Comic Vine to browse.")
            }
            return@LazyVerticalGrid
        }

        // What came out then (first when a date is picked).
        if (range != null) {
            full("dated-label") {
                SectionLabel(
                    "Issues from $dateLabel" +
                        (if (asked.isNotBlank()) " in series matching “$asked”" else "") +
                        (if (datedTotal > 0) " ($datedTotal)" else ""),
                )
            }
            items(dated, key = { "d:" + it.optInt("id") }) { i ->
                val title = "${i.optJSONObject("volume")?.optString("name") ?: ""} #${i.optString("issue_number")}".trim()
                CvCard(i.img(), title, formatDate(i.str("cover_date")), i.str("name")) { openIssue(vm, i, title) }
            }
            full("dated-more") {
                when {
                    !datedReady || datedLoading -> Loading("Finding issues from $dateLabel…")
                    datedError != null -> Problem(datedError ?: "")
                    datedTotal == 0 -> Text(
                        if (asked.isBlank()) "Comic Vine has no issues dated $dateLabel."
                        else "None of the series matching “$asked” have issues dated $dateLabel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarbourColors.Muted,
                    )
                    datedTotal > dated.size -> OutlinedButton(onClick = ::loadDated) {
                        Text("Show more (${datedTotal - dated.size} left)")
                    }
                    else -> Unit
                }
            }
            if (asked.isBlank()) {
                full("dated-note") {
                    Text(
                        "By cover date, which for older comics is usually two or three months after they went on sale. " +
                            "Type a name in the search bar to narrow it to one series or character.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HarbourColors.Muted,
                    )
                }
            }
        }

        when {
            asked.isBlank() -> {
                if (range == null) {
                    if (vm.cvRecent.isEmpty()) {
                        full("start") { Hint("Start with a search", "Try a character (Raven), a series (New Teen Titans) or a creator (Marv Wolfman).") }
                    } else {
                        full("recent-label") { SectionLabel("Recently viewed") }
                        items(vm.cvRecent, key = { "recent:${it.type}:${it.id}" }) { r ->
                            CvCard(r.image, r.name, r.detail, null) { vm.openCvRecent(r) }
                        }
                    }
                }
            }
            result is Cv.Loading -> full("loading") { Loading() }
            result is Cv.Err -> full("error") { Problem(result.message) }
            result is Cv.Ok -> {
                val characters = all.filter { it.optString("resource_type") == "character" }
                // With a date picked, only series that had started by then.
                val series = all.filter { it.optString("resource_type") == "volume" }
                    .filter { v -> year == null || (v.optString("start_year").toIntOrNull() ?: 0) <= year }
                val people = all.filter { it.optString("resource_type") == "person" }
                if (all.isEmpty()) full("none") { Hint("Nothing found", "Comic Vine has nothing called \"$asked\". Check the spelling or try fewer words.") }
                if (characters.isNotEmpty()) {
                    full("c-label") { SectionLabel("Characters") }
                    items(characters, key = { "c:" + it.optInt("id") }) { c ->
                        CvCard(
                            c.img(),
                            c.optString("name"),
                            listOfNotNull(
                                c.optJSONObject("publisher")?.str("name"),
                                c.optInt("count_of_issue_appearances").takeIf { it > 0 }?.let { "in $it issues" },
                            ).joinToString(" · "),
                            c.str("deck"),
                        ) { openCharacter(vm, c) }
                    }
                }
                if (series.isNotEmpty()) {
                    full("v-label") { SectionLabel(if (year != null) "Series running by $year" else "Series") }
                    items(series, key = { "v:" + it.optInt("id") }) { v ->
                        CvCard(v.img(), v.optString("name"), seriesLine(v), v.str("deck")) { openSeries(vm, v) }
                    }
                }
                if (people.isNotEmpty()) {
                    full("p-label") { SectionLabel("Creators") }
                    items(people, key = { "p:" + it.optInt("id") }) { p ->
                        CvCard(p.img(), p.optString("name"), null, p.str("deck")) { openPerson(vm, p) }
                    }
                }
            }
        }
    }
}

// ---------- A character

@Composable
fun CvCharacterPage(vm: MainViewModel, id: Int, name: String) {
    val result = rememberCv(
        "character/4005-$id/?field_list=id,name,real_name,aliases,deck,description,image,publisher,first_appeared_in_issue," +
            "count_of_issue_appearances,powers,teams,creators,origin,birth,site_detail_url",
    )
    // Series with the character's name in the title: their own books, biggest first.
    val series = rememberCv(
        "search/?resources=volume&limit=60&field_list=id,name,start_year,publisher,count_of_issues,image,deck&query=" + encode(name),
    )
    val ok = (result as? Cv.Ok)?.json?.optJSONObject("results")
    LaunchedEffect(ok) {
        ok?.let { vm.noteCvVisit("character", id, it.optString("name"), it.img(), it.str("deck") ?: "Character") }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = CARD_CELL),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full("top") { PageTop(vm, ok?.optString("name") ?: name, "Character · from Comic Vine") }
        when {
            result is Cv.Loading -> full("loading") { Loading() }
            result is Cv.Err -> full("error") { Problem(result.message) }
            ok != null -> {
                full("details") {
                    Row {
                        CvImage(ok.img("super_url"), Modifier.width(200.dp).aspectRatio(2f / 3f), maxPixels = 1_500_000)
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            ok.str("deck")?.let {
                                Text(it, style = MaterialTheme.typography.bodyLarge, color = HarbourColors.Text)
                                Spacer(Modifier.height(10.dp))
                            }
                            Fact("Real name", ok.str("real_name"))
                            Fact("Publisher", ok.optJSONObject("publisher")?.str("name"))
                            Fact("Origin", ok.optJSONObject("origin")?.str("name"))
                            Fact("Appears in", ok.optInt("count_of_issue_appearances").takeIf { it > 0 }?.let { "$it issues" })
                            ok.optJSONObject("first_appeared_in_issue")?.let { first ->
                                val label = listOfNotNull(first.str("issue_number")?.let { "#$it" }, first.str("name")).joinToString(" · ")
                                Fact("First appearance", label.ifBlank { "See issue" }) { openIssue(vm, first, label) }
                            }
                            Fact("Also known as", ok.str("aliases")?.lines()?.filter { it.isNotBlank() }?.take(6)?.joinToString(", "))
                            Fact("Powers", ok.list("powers").joinToString(", ") { it.optString("name") }.ifBlank { null })
                            OpenOnSite(ok)
                        }
                    }
                }
                full("creators") { ChipGroup("Created by", ok.list("creators").map { p -> p.optString("name") to { openPerson(vm, p) } }) }
                full("teams") { ChipGroup("Teams", ok.list("teams").map { it.optString("name") to null }) }
                full("about") {
                    Column {
                        SectionLabel("About")
                        Spacer(Modifier.height(8.dp))
                        DocView(ok.str("description"))
                    }
                }
                when (series) {
                    is Cv.Ok -> {
                        val wanted = name.lowercase()
                        val own = series.json.list("results")
                            .filter { it.optString("name").lowercase().contains(wanted) }
                            .sortedByDescending { it.optInt("count_of_issues") }
                        if (own.isNotEmpty()) {
                            full("series-label") { SectionLabel("Series named after ${ok.optString("name")}") }
                            items(own, key = { "v:" + it.optInt("id") }) { v ->
                                CvCard(v.img(), v.optString("name"), seriesLine(v), v.str("deck")) { openSeries(vm, v) }
                            }
                        }
                    }
                    is Cv.Loading -> full("series-loading") { Loading("Finding their series…") }
                    is Cv.Err -> Unit
                }
            }
        }
    }
}

// ---------- A series

@Composable
fun CvSeriesPage(vm: MainViewModel, id: Int, name: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result = rememberCv(
        "volume/4050-$id/?field_list=id,name,start_year,publisher,count_of_issues,deck,description,image,first_issue," +
            "last_issue,characters,people,locations,concepts,site_detail_url",
    )
    val ok = (result as? Cv.Ok)?.json?.optJSONObject("results")
    LaunchedEffect(ok) { ok?.let { vm.noteCvVisit("volume", id, it.optString("name"), it.img(), seriesLine(it)) } }

    // Issues, 100 at a time.
    val issues = remember(id) { mutableStateListOf<JSONObject>() }
    var total by remember(id) { mutableIntStateOf(-1) }
    var loadingIssues by remember(id) { mutableStateOf(false) }
    var issuesError by remember(id) { mutableStateOf<String?>(null) }
    fun loadMore() {
        if (loadingIssues) return
        loadingIssues = true
        issuesError = null
        val offset = issues.size
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                runCatching {
                    ComicVine.fetch(
                        context,
                        "issues/?filter=volume:$id&sort=cover_date:asc&limit=100&offset=$offset" +
                            "&field_list=id,issue_number,name,image,cover_date",
                    )
                }
            }
            page.onSuccess { json ->
                total = json.optInt("number_of_total_results", 0)
                issues.addAll(json.list("results"))
            }.onFailure { issuesError = errorText(it) }
            loadingIssues = false
        }
    }
    LaunchedEffect(id) { if (issues.isEmpty()) loadMore() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full("top") {
            PageTop(vm, ok?.let { "${it.optString("name")}" + (it.str("start_year")?.let { y -> " ($y)" } ?: "") } ?: name, "Series · from Comic Vine")
        }
        when {
            result is Cv.Loading -> full("loading") { Loading() }
            result is Cv.Err -> full("error") { Problem(result.message) }
            ok != null -> {
                full("details") {
                    Row {
                        CvImage(ok.img("super_url"), Modifier.width(200.dp).aspectRatio(2f / 3f), maxPixels = 1_500_000)
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            ok.str("deck")?.let {
                                Text(it, style = MaterialTheme.typography.bodyLarge, color = HarbourColors.Text)
                                Spacer(Modifier.height(10.dp))
                            }
                            Fact("Publisher", ok.optJSONObject("publisher")?.str("name"))
                            Fact("Started", ok.str("start_year"))
                            Fact("Issues", ok.optInt("count_of_issues").takeIf { it > 0 }?.toString())
                            ok.optJSONObject("first_issue")?.let { f ->
                                val label = listOfNotNull(f.str("issue_number")?.let { "#$it" }, f.str("name")).joinToString(" · ")
                                Fact("First issue", label) { openIssue(vm, f, label) }
                            }
                            ok.optJSONObject("last_issue")?.let { l ->
                                val label = listOfNotNull(l.str("issue_number")?.let { "#$it" }, l.str("name")).joinToString(" · ")
                                Fact("Latest issue", label) { openIssue(vm, l, label) }
                            }
                            OpenOnSite(ok)
                        }
                    }
                }
                full("about") {
                    Column {
                        SectionLabel("About")
                        Spacer(Modifier.height(8.dp))
                        if (parseDoc(ok.str("description")).isEmpty()) {
                            Text("Comic Vine has no write-up for this series yet.", style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
                        } else {
                            DocView(ok.str("description"))
                        }
                    }
                }
                full("characters") {
                    ChipGroup(
                        "Main characters (issues they're in)",
                        ok.list("characters").sortedByDescending { it.count() }.map { c ->
                            "${c.optString("name")} (${c.count()})" to { openCharacter(vm, c) }
                        },
                        shown = 20,
                    )
                }
                full("people") {
                    ChipGroup(
                        "Creators (issues they worked on)",
                        ok.list("people").sortedByDescending { it.count() }.map { p ->
                            "${p.optString("name")} (${p.count()})" to { openPerson(vm, p) }
                        },
                        shown = 16,
                    )
                }
                full("places") {
                    ChipGroup("Places", ok.list("locations").sortedByDescending { it.count() }.map { it.optString("name") to null }, shown = 12)
                }
                full("concepts") {
                    ChipGroup("Ideas and things", ok.list("concepts").sortedByDescending { it.count() }.map { it.optString("name") to null }, shown = 12)
                }
                full("issues-label") {
                    SectionLabel(if (total > 0) "Issues ($total)" else "Issues")
                }
                items(issues, key = { "i:" + it.optInt("id") }) { i ->
                    val title = "${ok.optString("name")} #${i.optString("issue_number")}"
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { openIssue(vm, i, title) },
                    ) {
                        CvImage(i.img(), Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                        Spacer(Modifier.height(6.dp))
                        Text("#${i.optString("issue_number")}", style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
                        listOfNotNull(i.str("name"), formatDate(i.str("cover_date"))).forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                full("issues-more") {
                    when {
                        loadingIssues -> Loading("Getting issues…")
                        issuesError != null -> Problem(issuesError ?: "")
                        total > issues.size -> OutlinedButton(onClick = ::loadMore) { Text("Show more issues (${total - issues.size} left)") }
                        else -> Unit
                    }
                }
            }
        }
    }
}

// ---------- An issue

/** Credits in a sensible order: story first, then art, then the rest. */
private val ROLE_ORDER = listOf("writer", "plotter", "scripter", "penciler", "artist", "inker", "colorist", "letterer", "cover", "editor")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CvIssuePage(vm: MainViewModel, id: Int, name: String) {
    val result = rememberCv(
        "issue/4000-$id/?field_list=id,name,issue_number,volume,cover_date,store_date,image,associated_images,deck,description," +
            "person_credits,character_credits,team_credits,story_arc_credits,location_credits," +
            "first_appearance_characters,first_appearance_teams,site_detail_url",
    )
    val ok = (result as? Cv.Ok)?.json?.optJSONObject("results")
    val title = ok?.let {
        "${it.optJSONObject("volume")?.optString("name") ?: ""} #${it.optString("issue_number")}".trim()
    } ?: name
    LaunchedEffect(ok) { ok?.let { vm.noteCvVisit("issue", id, title, it.img(), it.str("name") ?: formatDate(it.str("cover_date")) ?: "Issue") } }
    var bigCover by remember(id) { mutableStateOf<String?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = sectionPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full("top") { PageTop(vm, title, "Issue · from Comic Vine") }
        when {
            result is Cv.Loading -> full("loading") { Loading() }
            result is Cv.Err -> full("error") { Problem(result.message) }
            ok != null -> {
                full("details") {
                    Row {
                        CvImage(
                            ok.img("super_url"),
                            Modifier
                                .width(260.dp)
                                .aspectRatio(2f / 3f)
                                .clickable { bigCover = ok.img("original_url") ?: ok.img("super_url") },
                            maxPixels = 2_000_000,
                        )
                        Spacer(Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            ok.str("name")?.let { Text("“$it”", style = MaterialTheme.typography.titleLarge, color = HarbourColors.Text) }
                            ok.str("deck")?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = MaterialTheme.typography.bodyLarge, color = HarbourColors.Text)
                            }
                            Spacer(Modifier.height(10.dp))
                            ok.optJSONObject("volume")?.let { v -> Fact("Series", v.optString("name")) { openSeries(vm, v) } }
                            Fact("Cover date", formatDate(ok.str("cover_date")))
                            Fact("On sale", formatDate(ok.str("store_date")))
                            // People by role: "penciler, inker" counts for both.
                            val byRole = linkedMapOf<String, MutableList<JSONObject>>()
                            ok.list("person_credits").forEach { p ->
                                p.optString("role").split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach { r ->
                                    byRole.getOrPut(r) { mutableListOf() } += p
                                }
                            }
                            val roles = byRole.keys.sortedBy { r -> ROLE_ORDER.indexOf(r).let { if (it < 0) 99 else it } }
                            roles.forEach { role ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text(role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = HarbourColors.Muted, modifier = Modifier.width(130.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                        byRole.getValue(role).forEach { p ->
                                            Text(
                                                p.optString("name"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = HarbourColors.Accent,
                                                modifier = Modifier.clickable { openPerson(vm, p) },
                                            )
                                        }
                                    }
                                }
                            }
                            OpenOnSite(ok)
                        }
                    }
                }
                full("story") {
                    Column {
                        SectionLabel("The story")
                        Spacer(Modifier.height(8.dp))
                        if (parseDoc(ok.str("description")).isEmpty()) {
                            Text("Comic Vine has no write-up for this issue yet.", style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
                        } else {
                            DocView(ok.str("description"), shown = 12)
                        }
                    }
                }
                full("firsts") {
                    ChipGroup(
                        "First appearances",
                        (ok.list("first_appearance_characters") + ok.list("first_appearance_teams")).map { it.optString("name") to null },
                        accent = true,
                    )
                }
                full("arcs") { ChipGroup("Story arc", ok.list("story_arc_credits").map { it.optString("name") to null }) }
                full("characters") { ChipGroup("Characters", ok.list("character_credits").map { c -> c.optString("name") to { openCharacter(vm, c) } }, shown = 30) }
                full("teams") { ChipGroup("Teams", ok.list("team_credits").map { it.optString("name") to null }) }
                full("places") { ChipGroup("Places", ok.list("location_credits").map { it.optString("name") to null }) }
                val variants = ok.list("associated_images")
                if (variants.isNotEmpty()) {
                    full("variants") {
                        Column {
                            SectionLabel("Other covers (${variants.size})")
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(variants) { v ->
                                    val url = v.str("original_url")
                                    Column(Modifier.width(110.dp)) {
                                        CvImage(url, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clickable { bigCover = url })
                                        v.str("caption")?.let {
                                            Text(it, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    bigCover?.let { url ->
        Dialog(onDismissRequest = { bigCover = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { bigCover = null },
            ) {
                CvImage(
                    url,
                    Modifier
                        .heightIn(max = 900.dp)
                        .fillMaxWidth(0.9f)
                        .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true),
                    maxPixels = 4_000_000,
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

// ---------- A creator

@Composable
fun CvPersonPage(vm: MainViewModel, id: Int, name: String) {
    val result = rememberCv(
        "person/4040-$id/?field_list=id,name,deck,description,image,birth,death,country,hometown,created_characters," +
            "volume_credits,site_detail_url",
    )
    val ok = (result as? Cv.Ok)?.json?.optJSONObject("results")
    LaunchedEffect(ok) { ok?.let { vm.noteCvVisit("person", id, it.optString("name"), it.img(), it.str("deck") ?: "Creator") } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = sectionPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full("top") { PageTop(vm, ok?.optString("name") ?: name, "Creator · from Comic Vine") }
        when {
            result is Cv.Loading -> full("loading") { Loading() }
            result is Cv.Err -> full("error") { Problem(result.message) }
            ok != null -> {
                full("details") {
                    Row {
                        CvImage(ok.img("super_url"), Modifier.width(180.dp).aspectRatio(3f / 4f), maxPixels = 1_200_000)
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            ok.str("deck")?.let {
                                Text(it, style = MaterialTheme.typography.bodyLarge, color = HarbourColors.Text)
                                Spacer(Modifier.height(10.dp))
                            }
                            Fact("Born", formatDate(ok.str("birth")?.take(10)))
                            // Comic Vine gives a death date either as text or as {"date": …}.
                            Fact("Died", formatDate((ok.optJSONObject("death")?.str("date") ?: ok.str("death"))?.take(10)))
                            Fact("From", listOfNotNull(ok.str("hometown"), ok.str("country")).joinToString(", ").ifBlank { null })
                            Fact("Series worked on", ok.list("volume_credits").size.takeIf { it > 0 }?.toString())
                            OpenOnSite(ok)
                        }
                    }
                }
                full("about") {
                    Column {
                        SectionLabel("About")
                        Spacer(Modifier.height(8.dp))
                        DocView(ok.str("description"))
                    }
                }
                full("created") {
                    ChipGroup("Characters they created", ok.list("created_characters").map { c -> c.optString("name") to { openCharacter(vm, c) } }, shown = 30)
                }
                full("series") {
                    ChipGroup("Series they worked on", ok.list("volume_credits").map { v -> v.optString("name") to { openSeries(vm, v) } }, shown = 40)
                }
            }
        }
    }
}

/** Opens a recently viewed page again. */
fun MainViewModel.openCvRecent(r: CvRecent) {
    show(
        when (r.type) {
            "character" -> Section.CvCharacter(r.id, r.name)
            "volume" -> Section.CvSeries(r.id, r.name)
            "issue" -> Section.CvIssue(r.id, r.name)
            else -> Section.CvPerson(r.id, r.name)
        },
    )
}
