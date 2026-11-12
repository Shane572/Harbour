package com.harbour.reader

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harbour.reader.ui.AccentChoice
import com.harbour.reader.ui.HarbourColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/** Full-screen places. The library itself is one screen with a sidebar of [Section]s. */
sealed interface Screen {
    data object Main : Screen
    data class Reader(val comic: Comic) : Screen
    data class BookReader(val book: Book) : Screen
    data object Browse : Screen

    /** Saved pages full screen, starting at [start]; [ids] picks which (and in what order). */
    data class GalleryView(val ids: List<String>, val start: Int) : Screen
}

val Screen.stateKey: String
    get() = when (this) {
        Screen.Main -> "main"
        Screen.Browse -> "browse"
        is Screen.GalleryView -> "gallery-view"
        is Screen.Reader -> "reader:${comic.key}"
        is Screen.BookReader -> "book-reader:${book.key}"
    }

/** What the library shows next to the sidebar. */
sealed interface Section {
    data object Home : Section
    data object AllComics : Section
    data object Ungrouped : Section
    data object AllGroups : Section
    data object Gallery : Section
    data object Guides : Section
    data class InGuide(val id: String) : Section

    // Browsing Comic Vine (search, then a character, series, issue or creator page).
    data object ComicVine : Section
    data class CvCharacter(val id: Int, val name: String) : Section
    data class CvSeries(val id: Int, val name: String) : Section
    data class CvIssue(val id: Int, val name: String) : Section
    data class CvPerson(val id: Int, val name: String) : Section
    data object WillRead : Section
    data class InGroup(val id: String) : Section
    data object Books : Section
    data class InBookGroup(val id: String) : Section
    data object Settings : Section
}

val Section.stateKey: String
    get() = when (this) {
        Section.Home -> "home"
        Section.AllComics -> "all"
        Section.Ungrouped -> "ungrouped"
        Section.AllGroups -> "groups"
        Section.Gallery -> "gallery"
        Section.Guides -> "guides"
        is Section.InGuide -> "guide:$id"
        Section.ComicVine -> "cv"
        is Section.CvCharacter -> "cv-character:$id"
        is Section.CvSeries -> "cv-series:$id"
        is Section.CvIssue -> "cv-issue:$id"
        is Section.CvPerson -> "cv-person:$id"
        Section.WillRead -> "will-read"
        is Section.InGroup -> "group:$id"
        Section.Books -> "books"
        is Section.InBookGroup -> "book-group:$id"
        Section.Settings -> "settings"
    }

/** The Comic Vine pages, where the search bar searches Comic Vine instead of the library. */
val Section.isComicVine: Boolean
    get() = when (this) {
        Section.ComicVine, is Section.CvCharacter, is Section.CvSeries, is Section.CvIssue, is Section.CvPerson -> true
        else -> false
    }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LibraryRepository(app)
    private val groupStore = GroupStore(app)
    private val progressStore = ProgressStore(app)
    private val bookProgressStore = BookProgressStore(app)
    private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val covers = CoverCache(app)

    var roots by mutableStateOf(repo.roots())
        private set
    var comics by mutableStateOf(repo.loadCached())
        private set
    var books by mutableStateOf(repo.loadCachedBooks())
        private set
    var groups by mutableStateOf(groupStore.load())
        private set
    var scanning by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** Details of the last crash, shown once so they can be copied and sent. */
    var crashReport by mutableStateOf(CrashLog.read(app))
        private set

    fun dismissCrashReport() {
        CrashLog.clear(getApplication())
        crashReport = null
    }

    val progress = mutableStateMapOf<String, Progress>().apply { putAll(progressStore.all()) }
    val bookProgress = mutableStateMapOf<String, BookProgress>().apply { putAll(bookProgressStore.all()) }
    val backStack = mutableStateListOf<Screen>(Screen.Main)
    val screen: Screen get() = backStack.last()
    var section by mutableStateOf<Section>(Section.Home)
        private set

    // Library views

    val comicsByKey: Map<String, Comic> by derivedStateOf { comics.associateBy { it.key } }

    val groupOfKey: Map<String, Group> by derivedStateOf {
        buildMap { groups.forEach { g -> g.keys.forEach { put(it, g) } } }
    }

    /** Groups in Shane's own order (drag to reorder in the sidebar). New groups go at the end. */
    val orderedGroups: List<Group> by derivedStateOf { groups.filter { !it.forBooks } }

    /** Book groups, in the order they were made. */
    val bookGroups: List<Group> by derivedStateOf { groups.filter { it.forBooks } }

    val booksByKey: Map<String, Book> by derivedStateOf { books.associateBy { it.key } }

    /** A book group's books, by title. */
    fun booksIn(g: Group): List<Book> = g.keys.mapNotNull { booksByKey[it] }.sortedWith(compareBy(NaturalOrder) { it.title })

    fun bookCoverOf(g: Group): Book? =
        g.coverKey?.takeIf { it in g.keys }?.let { booksByKey[it] } ?: booksIn(g).firstOrNull()

    val ungrouped: List<Comic> by derivedStateOf { comics.filter { it.key !in groupOfKey } }

    val continueReading: List<Comic> by derivedStateOf {
        comics.mapNotNull { c -> progress[c.key]?.takeIf { !it.finished }?.let { c to it.time } }
            .sortedByDescending { it.second }
            .take(20)
            .map { it.first }
    }

    val recentlyAdded: List<Comic> by derivedStateOf { comics.sortedByDescending { it.modified }.take(20) }

    fun groupById(id: String): Group? = groups.firstOrNull { it.id == id }

    /** A group's comics in reading order (natural title order), skipping files that have gone. */
    fun comicsIn(g: Group): List<Comic> =
        g.keys.mapNotNull { comicsByKey[it] }.sortedWith(compareBy(NaturalOrder) { it.name })

    fun sorted(list: List<Comic>): List<Comic> = when (sort) {
        SortOrder.Title -> list.sortedWith(compareBy(NaturalOrder) { it.name })
        SortOrder.Added -> list.sortedByDescending { it.modified }
        SortOrder.Read -> list.sortedByDescending { progress[it.key]?.time ?: 0L }
    }

    /** The line under a comic's title: its group, or its file's folder until it's grouped. */
    fun subtitleFor(c: Comic): String = groupOfKey[c.key]?.name ?: c.folderName

    // Books

    val continueBooks: List<Book> by derivedStateOf {
        books.mapNotNull { b -> bookProgress[b.key]?.takeIf { !it.finished }?.let { b to it.time } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun sortedBooks(list: List<Book>): List<Book> = when (sort) {
        SortOrder.Title -> list.sortedWith(compareBy(NaturalOrder) { it.title })
        SortOrder.Added -> list.sortedByDescending { it.modified }
        SortOrder.Read -> list.sortedByDescending { bookProgress[it.key]?.time ?: 0L }
    }

    fun openBook(b: Book) {
        backStack.add(Screen.BookReader(b))
    }

    fun saveBookProgress(b: Book, chapter: Int, fraction: Float, chapters: Int) {
        val p = BookProgress(chapter, fraction.coerceIn(0f, 1f), chapters, System.currentTimeMillis())
        bookProgress[b.key] = p
        bookProgressStore.save(b.key, p)
    }

    /** PDF books are read by page: progress is stored as page (of pages), finished on the last one. */
    fun pdfStartPage(b: Book, count: Int): Int {
        val p = bookProgress[b.key] ?: return 0
        return if (p.finished) 0 else p.chapter.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun savePdfProgress(b: Book, page: Int, count: Int) {
        saveBookProgress(b, page, if (page >= count - 1) 1f else 0f, count)
    }

    fun setBookRead(keys: Collection<String>, read: Boolean) {
        for (key in keys) {
            if (read) {
                val chapters = (bookProgress[key]?.chapters ?: 1).coerceAtLeast(1)
                val done = BookProgress(chapters - 1, 1f, chapters, System.currentTimeMillis())
                bookProgress[key] = done
                bookProgressStore.save(key, done)
            } else {
                bookProgress.remove(key)
                bookProgressStore.clear(key)
            }
        }
    }

    var bookStyle by mutableStateOf(
        BookStyle(
            sizePercent = settings.getInt("bookSize", 110),
            font = enumValueOr(settings.getString("bookFont", null), BookFont.Original),
            spacing = enumValueOr(settings.getString("bookSpacing", null), BookSpacing.Normal),
            margins = enumValueOr(settings.getString("bookMargins", null), BookMargins.Normal),
            theme = enumValueOr(settings.getString("bookTheme", null), BookTheme.Dark),
        ),
    )
        private set

    fun changeBookStyle(style: BookStyle) {
        bookStyle = style.copy(sizePercent = style.sizePercent.coerceIn(60, 220))
        settings.edit()
            .putInt("bookSize", bookStyle.sizePercent)
            .putString("bookFont", style.font.name)
            .putString("bookSpacing", style.spacing.name)
            .putString("bookMargins", style.margins.name)
            .putString("bookTheme", style.theme.name)
            .apply()
    }

    // Look and feel

    var sidebarExpanded by mutableStateOf(settings.getBoolean("sidebarExpanded", true))
        private set
    var sort by mutableStateOf(enumValueOr(settings.getString("sort", null), SortOrder.Title))
        private set
    var coverSize by mutableStateOf(enumValueOr(settings.getString("coverSize", null), CoverSize.Medium))
        private set

    fun toggleSidebar() {
        sidebarExpanded = !sidebarExpanded
        settings.edit().putBoolean("sidebarExpanded", sidebarExpanded).apply()
    }

    fun changeSort(s: SortOrder) {
        sort = s
        settings.edit().putString("sort", s.name).apply()
    }

    fun changeCoverSize(s: CoverSize) {
        coverSize = s
        settings.edit().putString("coverSize", s.name).apply()
    }

    fun changeAccent(a: AccentChoice) {
        HarbourColors.accent = a
        settings.edit().putString("accent", a.name).apply()
    }

    fun changeBlackBackground(on: Boolean) {
        HarbourColors.black = on
        settings.edit().putBoolean("black", on).apply()
    }

    // Backup

    var lastBackup by mutableStateOf(Backup.lastSaved())
        private set

    fun refreshBackupInfo() {
        lastBackup = Backup.lastSaved()
    }

    fun backUpNow() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.write(getApplication()) }.isSuccess }
            lastBackup = Backup.lastSaved()
            message = if (ok) "Backup saved" else "Couldn't save the backup. Allow file access first (Add folder)."
        }
    }

    /**
     * Saves a backup when you leave Harbour, but only once there's something worth keeping, so a
     * fresh install can never overwrite a good backup with an empty one.
     */
    fun autoBackup() {
        val app = getApplication<Application>()
        if (!StorageAccess.hasAccess(app)) return
        if (groups.isEmpty() && progress.isEmpty() && bookProgress.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Backup.write(app) } }
            lastBackup = Backup.lastSaved()
        }
    }

    fun restoreBackup() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.restore(app) }.isSuccess }
            if (ok) Backup.restartApp(app) else message = "Couldn't read the backup file"
        }
    }

    // Reading guides: Shane's own, word for word (assets/guides.json), ticked off as he reads.

    val guides: List<Guide> by lazy { runCatching { Guides.load(getApplication()) }.getOrDefault(emptyList()) }

    fun guideById(id: String): Guide? = guides.firstOrNull { it.id == id }

    // Each step's state, by "guide:block". Build 36 stored ticks as true; those count as read.
    private val guideStore = app.getSharedPreferences("guide_progress", Context.MODE_PRIVATE)
    private val guideSteps = mutableStateMapOf<String, StepState>().apply {
        guideStore.all.forEach { (k, v) ->
            when (v) {
                true, "read" -> put(k, StepState.Read)
                "skipped" -> put(k, StepState.Skipped)
            }
        }
    }

    fun guideStep(g: Guide, index: Int): StepState = guideSteps["${g.id}:$index"] ?: StepState.None

    fun setGuideStep(g: Guide, index: Int, state: StepState) {
        val key = "${g.id}:$index"
        if (state == StepState.None) guideSteps.remove(key) else guideSteps[key] = state
        guideStore.edit().apply {
            when (state) {
                StepState.None -> remove(key)
                StepState.Read -> putString(key, "read")
                StepState.Skipped -> putString(key, "skipped")
            }
        }.apply()
    }

    /** Ticks off every step before this one that isn't already read or skipped. */
    fun markGuideReadBefore(g: Guide, index: Int) {
        g.entries.filter { it < index && guideStep(g, it) == StepState.None }.forEach { setGuideStep(g, it, StepState.Read) }
    }

    /** Skips the rest of a section (steps already read stay read), or brings it back if it's all skipped. */
    fun toggleSkipSection(g: Guide, index: Int) {
        val steps = g.sectionOf(index)
        val open = steps.filter { guideStep(g, it) != StepState.Read }
        val allSkipped = open.isNotEmpty() && open.all { guideStep(g, it) == StepState.Skipped }
        open.forEach { setGuideStep(g, it, if (allSkipped) StepState.None else StepState.Skipped) }
    }

    // Covers for guide steps, from Comic Vine with Shane's own key (ComicVine.kt).

    var comicVineKey by mutableStateOf(ComicVine.key(app))
        private set

    fun changeComicVineKey(key: String) {
        ComicVine.setKey(getApplication(), key)
        comicVineKey = key.trim()
        message = if (comicVineKey.isEmpty()) "Comic Vine key removed" else "Comic Vine key saved"
    }

    // Browsing Comic Vine: the search bar's text on those pages, and pages opened lately.

    var cvQuery by mutableStateOf("")
        private set

    /** Bumped when Search is pressed, to search straight away rather than waiting for a pause. */
    var cvSubmits by mutableIntStateOf(0)
        private set

    fun searchComicVine(q: String) {
        cvQuery = q
        if (section.isComicVine && section != Section.ComicVine) show(Section.ComicVine)
    }

    fun submitComicVineSearch() {
        cvSubmits++
    }

    private val cvPrefs = app.getSharedPreferences("comicvine", Context.MODE_PRIVATE)

    var cvRecent by mutableStateOf(loadCvRecent())
        private set

    private fun loadCvRecent(): List<CvRecent> = runCatching {
        val arr = org.json.JSONArray(cvPrefs.getString("recent", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CvRecent(o.getString("type"), o.getInt("id"), o.getString("name"), o.optString("image").ifEmpty { null }, o.optString("detail"))
        }
    }.getOrDefault(emptyList())

    suspend fun comicVineSavedBytes(): Long = withContext(Dispatchers.IO) { ComicVine.savedBytes(getApplication()) }

    /** Clears saved Comic Vine pages and pictures (not guide covers, and not the key). */
    fun clearComicVineSaved() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { ComicVine.clearSaved(app) }
        message = "Cleared saved Comic Vine pages"
    }

    fun noteCvVisit(type: String, id: Int, name: String, image: String?, detail: String) {
        val r = CvRecent(type, id, name, image, detail)
        cvRecent = (listOf(r) + cvRecent.filterNot { it.type == type && it.id == id }).take(30)
        val arr = org.json.JSONArray()
        cvRecent.forEach {
            arr.put(
                org.json.JSONObject()
                    .put("type", it.type).put("id", it.id).put("name", it.name)
                    .put("image", it.image ?: "").put("detail", it.detail),
            )
        }
        cvPrefs.edit().putString("recent", arr.toString()).apply()
    }

    suspend fun stepCover(block: GuideBlock): Result<StepCover> =
        withContext(Dispatchers.IO) { runCatching { ComicVine.lookup(getApplication(), block) } }

    /** Steps read, skipped, and in all. */
    fun guideProgress(g: Guide): Triple<Int, Int, Int> {
        val states = g.entries.map { guideStep(g, it) }
        return Triple(states.count { it == StepState.Read }, states.count { it == StepState.Skipped }, states.size)
    }

    // The sidebar's list of groups can be folded away.

    var groupsCollapsed by mutableStateOf(settings.getBoolean("groupsCollapsed", false))
        private set

    fun toggleGroupsCollapsed() {
        groupsCollapsed = !groupsCollapsed
        settings.edit().putBoolean("groupsCollapsed", groupsCollapsed).apply()
    }

    // Rotation lock while reading

    var rotationLocked by mutableStateOf(settings.getBoolean("rotationLocked", false))
        private set

    fun toggleRotationLock() {
        rotationLocked = !rotationLocked
        settings.edit().putBoolean("rotationLocked", rotationLocked).apply()
        message = if (rotationLocked) "Rotation locked" else "Rotation unlocked"
    }

    // Page tweaks while reading, toggled from the reader's top bar: trim plain margins, and
    // split two-page spreads into halves.

    var trimMargins by mutableStateOf(settings.getBoolean("trimMargins", false))
        private set
    var splitSpreads by mutableStateOf(settings.getBoolean("splitSpreads", false))
        private set

    fun toggleTrimMargins() {
        trimMargins = !trimMargins
        settings.edit().putBoolean("trimMargins", trimMargins).apply()
        message = if (trimMargins) "Trimming page margins" else "Showing pages as they are"
    }

    fun toggleSplitSpreads() {
        splitSpreads = !splitSpreads
        settings.edit().putBoolean("splitSpreads", splitSpreads).apply()
        message = if (splitSpreads) "Splitting two-page spreads" else "Keeping spreads whole"
    }

    // Which pages of a comic are spreads: worked out once per comic while Harbour is open.
    private val spreadCache = ConcurrentHashMap<String, BooleanArray>()

    fun spreadsOf(c: Comic, archive: ComicArchive): BooleanArray =
        spreadCache.getOrPut(c.key) { ShownPages.findSpreads(archive) }

    // Issue details from inside the file (ComicInfo.xml), read when first asked for.
    private val infoCache = HashMap<String, ComicInfo?>()

    suspend fun comicInfo(c: Comic): ComicInfo? {
        if (infoCache.containsKey(c.key)) return infoCache[c.key]
        val info = withContext(Dispatchers.IO) { ComicInfo.read(c.file) }
        infoCache[c.key] = info
        return info
    }

    // Gallery: pages saved from the reader (pictures in Harbour/Gallery)

    var gallery by mutableStateOf<List<SavedPage>>(emptyList())
        private set

    fun loadGallery() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            gallery = withContext(Dispatchers.IO) { runCatching { GalleryStore.load(app) }.getOrDefault(emptyList()) }
        }
    }

    // Build 31 had read aloud (since removed). Clear out the voices it downloaded and its settings.
    private fun clearOldVoices() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                File(app.filesDir, "voices").deleteRecursively()
                app.cacheDir.listFiles()?.filter { it.name.endsWith(".tar.bz2") }?.forEach { it.delete() }
                app.deleteSharedPreferences("bubble_voices")
                val old = settings.all.keys.filter { it.startsWith("voice") || it == "speechSpeed" }
                if (old.isNotEmpty()) settings.edit().apply { old.forEach(::remove) }.apply()
            }
        }
    }

    fun savedPage(key: String, page: Int): SavedPage? = gallery.firstOrNull { it.comicKey == key && it.page == page }

    /** Saves a page of the open comic at wallpaper quality (trimmed, if trimming is on). */
    fun savePage(c: Comic, archive: ComicArchive, page: Int, pages: Int) {
        if (savedPage(c.key, page) != null) return
        val group = groupOfKey[c.key]?.name
        val trim = trimMargins
        val app = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = archive.decodePage(page, 8_000_000L) ?: error("Couldn't decode the page")
                    GalleryStore.write(app, if (trim) Trim.margins(bmp) else bmp, c, group, page, pages)
                }.getOrNull()
            }
            if (saved == null) {
                message = "Couldn't save that page"
                return@launch
            }
            gallery = listOf(saved) + gallery
            val list = gallery
            withContext(Dispatchers.IO) { runCatching { GalleryStore.save(list) } }
            message = "Saved page ${page + 1} to your gallery and Pictures"
        }
    }

    /** Takes a page out of the gallery and deletes its picture from the tablet. The comic isn't touched. */
    fun removeSaved(p: SavedPage) {
        gallery = gallery.filter { it.id != p.id }
        val list = gallery
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                GalleryStore.delete(app, p)
                GalleryStore.save(list)
            }
        }
        message = "Removed from your gallery"
    }

    /** Gives a saved page your own name (blank goes back to the comic's title). */
    fun renameSaved(p: SavedPage, name: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val renamed = withContext(Dispatchers.IO) { runCatching { GalleryStore.rename(app, p, name) }.getOrNull() }
            if (renamed == null) {
                message = "Couldn't rename that picture"
                return@launch
            }
            gallery = gallery.map { if (it.id == p.id) renamed else it }
            val list = gallery
            withContext(Dispatchers.IO) { runCatching { GalleryStore.save(list) } }
        }
    }

    fun openGallery(list: List<SavedPage>, start: Int) {
        backStack.add(Screen.GalleryView(list.map { it.id }, start))
    }

    // Set by Read from here in the gallery, so the reader starts on that page.
    private var pendingStart: Pair<String, Int>? = null

    fun takePendingStart(c: Comic): Int? {
        val p = pendingStart
        pendingStart = null
        return p?.takeIf { it.first == c.key }?.second
    }

    fun readFrom(p: SavedPage) {
        val c = comicsByKey[p.comicKey]
        if (c == null) {
            message = "That comic isn't in your library any more"
            return
        }
        pendingStart = c.key to p.page
        openComic(c)
    }

    /** [which] is WallpaperManager.FLAG_SYSTEM (home screen), FLAG_LOCK, or both. */
    fun setWallpaper(p: SavedPage, which: Int) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    FileInputStream(GalleryStore.imageOf(p)).use { WallpaperManager.getInstance(app).setStream(it, null, true, which) }
                }.isSuccess
            }
            message = if (ok) "Wallpaper set" else "Couldn't set the wallpaper"
        }
    }

    // Updates

    private val updater = Updater(app)
    val currentBuild: Int get() = updater.currentBuild
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    init {
        // Groups used to be listed A–Z; keep that look the first time, then the order is Shane's.
        if (!settings.getBoolean("groupsOrdered", false)) {
            groups = groups.sortedWith(compareBy(NaturalOrder) { it.name })
            groupStore.save(groups)
            settings.edit().putBoolean("groupsOrdered", true).apply()
        }
        if (roots.isNotEmpty()) rescan()
        loadGallery()
        clearOldVoices()
        checkForUpdate(quiet = true)
    }

    /** [quiet] keeps a failed check (e.g. offline) from showing an error on launch. */
    fun checkForUpdate(quiet: Boolean = false) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        viewModelScope.launch {
            update = UpdateState.Checking
            update = try {
                updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                if (quiet) UpdateState.Idle else UpdateState.Failed("Couldn't check for updates", null)
            }
        }
    }

    fun installUpdate(info: UpdateInfo) {
        if (update is UpdateState.Downloading) return
        viewModelScope.launch {
            update = UpdateState.Downloading(info, 0f)
            update = try {
                updater.install(info) { p -> update = UpdateState.Downloading(info, p) }
                UpdateState.Waiting(info)
            } catch (e: Exception) {
                UpdateState.Failed("Download failed. Check your connection and try again.", info)
            }
        }
    }

    // Folders

    fun openBrowser() {
        if (screen !is Screen.Browse) backStack.add(Screen.Browse)
    }

    fun addRoot(dir: File) {
        roots = repo.addRoot(dir)
        backStack.removeAll { it is Screen.Browse }
        rescan()
    }

    fun removeRoot(root: LibraryRoot) {
        roots = repo.removeRoot(root)
        rescan()
    }

    fun comicCountIn(root: LibraryRoot): Int = comics.count { it.uri.startsWith(root.uri + "/") }

    fun bookCountIn(root: LibraryRoot): Int = books.count { it.uri.startsWith(root.uri + "/") }

    fun rescan() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            try {
                val result = repo.scan(roots)
                comics = result.comics
                books = result.books
                if (result.failed.isNotEmpty()) {
                    message = "Lost access to ${result.failed.joinToString { it.name }}. Remove it and add it again."
                }
            } finally {
                scanning = false
            }
        }
    }

    // Groups

    fun createGroup(name: String, keys: Collection<String> = emptyList(), forBooks: Boolean = false): Group {
        val g = Group(UUID.randomUUID().toString(), name.trim().ifEmpty { "New group" }, emptyList(), forBooks = forBooks)
        groups = groups + g
        if (keys.isEmpty()) groupStore.save(groups) else addToGroup(g.id, keys)
        return g
    }

    fun renameGroup(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        groups = groups.map { if (it.id == id) it.copy(name = clean) else it }
        groupStore.save(groups)
    }

    fun deleteGroup(id: String) {
        groups = groups.filterNot { it.id == id }
        groupStore.save(groups)
        if (section == Section.InGroup(id)) section = Section.Home
        if (section == Section.InBookGroup(id)) section = Section.Books
    }

    /** Moves comics into a group. A comic is only ever in one group. */
    fun addToGroup(id: String, keys: Collection<String>) {
        val moving = keys.toSet()
        groups = groups.map { g ->
            val kept = g.keys.filterNot { it in moving }
            if (g.id == id) g.copy(keys = kept + moving) else g.copy(keys = kept, readOrder = g.readOrder.filterNot { it in moving })
        }
        groupStore.save(groups)
    }

    fun removeFromGroups(keys: Collection<String>) {
        val leaving = keys.toSet()
        groups = groups.map { g ->
            g.copy(keys = g.keys.filterNot { it in leaving }, readOrder = g.readOrder.filterNot { it in leaving })
        }
        groupStore.save(groups)
    }

    // Read order: a hand-picked reading sequence at the top of a group

    fun readOrderOf(g: Group): List<Comic> = g.readOrder.mapNotNull { comicsByKey[it] }

    /** Adds comics to the end of the group's read order, in natural order. */
    fun addToReadOrder(groupId: String, keys: Collection<String>) {
        updateGroup(groupId) { g ->
            val adding = keys.filter { it in g.keys && it !in g.readOrder }
                .sortedWith(compareBy(NaturalOrder) { comicsByKey[it]?.name ?: it })
            g.copy(readOrder = g.readOrder + adding)
        }
    }

    /** Replaces a group's read order (from dragging on the group page). */
    fun setReadOrder(groupId: String, keys: List<String>) {
        updateGroup(groupId) { g -> g.copy(readOrder = keys.filter { it in g.keys }.distinct()) }
    }

    fun removeFromReadOrder(groupId: String, keys: Collection<String>) {
        val leaving = keys.toSet()
        updateGroup(groupId) { g -> g.copy(readOrder = g.readOrder.filterNot { it in leaving }) }
    }

    /** Moves one comic earlier (-1) or later (+1) in the read order. */
    fun moveInReadOrder(groupId: String, key: String, delta: Int) {
        updateGroup(groupId) { g ->
            val i = g.readOrder.indexOf(key)
            val j = i + delta
            if (i < 0 || j !in g.readOrder.indices) {
                g
            } else {
                val list = g.readOrder.toMutableList()
                list.removeAt(i)
                list.add(j, key)
                g.copy(readOrder = list)
            }
        }
    }

    /** The comic shown as a group's picture: the one picked with Set as group cover, else the first issue. */
    fun coverOf(g: Group): Comic? =
        g.coverKey?.takeIf { it in g.keys }?.let { comicsByKey[it] } ?: comicsIn(g).firstOrNull()

    fun setGroupCover(key: String) {
        val g = groupOfKey[key] ?: return
        updateGroup(g.id) { it.copy(coverKey = key) }
        message = "Cover set for ${g.name}"
    }

    /** Saves a new group order from the sidebar. */
    fun reorderGroups(ids: List<String>) {
        val byId = groups.associateBy { it.id }
        groups = ids.mapNotNull { byId[it] } + groups.filter { it.id !in ids }
        groupStore.save(groups)
    }

    private fun updateGroup(id: String, change: (Group) -> Group) {
        groups = groups.map { if (it.id == id) change(it) else it }
        groupStore.save(groups)
    }

    /** What to read next in a group: the first unread in its read order, then the first unread overall. */
    fun upNext(g: Group): Comic? {
        val unread = { c: Comic -> progress[c.key]?.finished != true }
        return readOrderOf(g).firstOrNull(unread)
            ?: comicsIn(g).firstOrNull(unread)
            ?: readOrderOf(g).firstOrNull()
            ?: comicsIn(g).firstOrNull()
    }

    // Recently opened groups, newest first, for Home

    private var recentGroupIds by mutableStateOf(
        settings.getString("recentGroups", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
    )

    val recentGroups: List<Group> by derivedStateOf {
        recentGroupIds.mapNotNull { id -> groups.firstOrNull { it.id == id } }.take(3)
    }

    private fun noteOpened(c: Comic) {
        val id = groupOfKey[c.key]?.id ?: return
        recentGroupIds = (listOf(id) + recentGroupIds.filter { it != id }).take(6)
        settings.edit().putString("recentGroups", recentGroupIds.joinToString(",")).apply()
    }

    // Will Read: comics lined up to read next, in the order they were added

    private var willReadKeys by mutableStateOf(
        settings.getString("willRead", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
    )

    val willRead: List<Comic> by derivedStateOf { willReadKeys.mapNotNull { comicsByKey[it] } }

    fun isWillRead(key: String): Boolean = key in willReadKeys

    fun addToWillRead(keys: Collection<String>) {
        val adding = keys.filter { it !in willReadKeys }
            .sortedWith(compareBy(NaturalOrder) { comicsByKey[it]?.name ?: it })
        saveWillRead(willReadKeys + adding)
        message = if (adding.size == 1) "Added to Will Read" else "Added ${adding.size} to Will Read"
    }

    fun removeFromWillRead(keys: Collection<String>) {
        val leaving = keys.toSet()
        saveWillRead(willReadKeys.filterNot { it in leaving })
    }

    private fun saveWillRead(keys: List<String>) {
        willReadKeys = keys
        settings.edit().putString("willRead", keys.joinToString("\n")).apply()
    }

    // Search

    var searchQuery by mutableStateOf("")
        private set

    fun search(q: String) {
        searchQuery = q
    }

    /**
     * Every word must match. Words are matched against the group name plus the title, so
     * "green lantern 48" finds issue 48 in the Green Lantern group. Numbers match whole numbers
     * ("48" matches "#048" but not "148"); "#" is ignored.
     */
    /** Search covers the page you're on: a group, Will Read, Ungrouped, All comics or Books. Home and Settings search everything. */
    val searchScopeName: String?
        get() = when (val s = section) {
            is Section.InGroup -> groupById(s.id)?.name
            is Section.InBookGroup -> groupById(s.id)?.name
            Section.WillRead -> "Will Read"
            Section.Ungrouped -> "Ungrouped"
            Section.AllGroups -> "Groups"
            Section.Gallery -> "Gallery"
            Section.Guides, is Section.InGuide -> null
            Section.ComicVine, is Section.CvCharacter, is Section.CvSeries, is Section.CvIssue, is Section.CvPerson -> null
            Section.AllComics -> "All comics"
            Section.Books -> "Books"
            Section.Home, Section.Settings -> null
        }

    private fun comicsInScope(): List<Comic> = when (val s = section) {
        is Section.InGroup -> groupById(s.id)?.let(::comicsIn) ?: comics
        Section.WillRead -> willRead
        Section.Ungrouped -> ungrouped
        Section.AllGroups -> comics.filter { c -> groupOfKey[c.key]?.forBooks == false }
        Section.Gallery -> gallery.mapNotNull { comicsByKey[it.comicKey] }.distinct()
        Section.Guides, is Section.InGuide -> comics
        Section.ComicVine, is Section.CvCharacter, is Section.CvSeries, is Section.CvIssue, is Section.CvPerson -> comics
        Section.Books, is Section.InBookGroup -> emptyList()
        Section.Home, Section.Settings, Section.AllComics -> comics
    }

    private fun booksInScope(): List<Book> = when (val s = section) {
        Section.Books, Section.Home, Section.Settings -> books
        is Section.InBookGroup -> groupById(s.id)?.let(::booksIn) ?: books
        else -> emptyList()
    }

    fun searchComics(q: String): List<Comic> {
        val terms = searchTerms(q)
        if (terms.isEmpty()) return emptyList()
        return comicsInScope()
            .filter { c -> matches(terms, "${groupOfKey[c.key]?.name ?: ""} ${c.title} ${c.name}") }
            .sortedWith(
                compareBy<Comic> { groupOfKey[it.key] == null }
                    .thenBy(NaturalOrder) { groupOfKey[it.key]?.name ?: "" }
                    .thenBy(NaturalOrder) { it.name },
            )
    }

    fun searchBooks(q: String): List<Book> {
        val terms = searchTerms(q)
        if (terms.isEmpty()) return emptyList()
        return booksInScope().filter { b -> matches(terms, "${b.title} ${b.author} ${b.name}") }
            .sortedWith(compareBy(NaturalOrder) { it.title })
    }

    private fun searchTerms(q: String): List<String> =
        q.lowercase().replace("#", " ").split(WHITESPACE).filter { it.isNotBlank() }

    private fun matches(terms: List<String>, text: String): Boolean {
        val hay = text.lowercase()
        val numbers = DIGITS.findAll(hay).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()
        return terms.all { t ->
            if (t.all { it.isDigit() }) t.trimStart('0').ifEmpty { "0" } in numbers else t in hay
        }
    }

    // Deleting files

    /** Deletes the files themselves from the tablet, then forgets them. Can't be undone. */
    fun deleteFiles(items: Collection<LibraryItem>) {
        val list = items.toList()
        viewModelScope.launch {
            val gone = withContext(Dispatchers.IO) {
                list.filter { runCatching { it.file.delete() || !it.file.exists() }.getOrDefault(false) }
            }
            val keys = gone.map { it.key }.toSet()
            comics = comics.filterNot { it.key in keys }
            books = books.filterNot { it.key in keys }
            removeFromGroups(keys)
            removeFromWillRead(keys)
            keys.forEach {
                progress.remove(it)
                progressStore.clear(it)
                bookProgress.remove(it)
                bookProgressStore.clear(it)
            }
            val failed = list.size - gone.size
            message = when {
                failed == 0 && gone.size == 1 -> "Deleted ${gone[0].title}"
                failed == 0 -> "Deleted ${gone.size} files"
                else -> "Couldn't delete $failed of ${list.size} files"
            }
            rescan()
        }
    }

    // Navigation

    // Pages you've been to, so Back (and the back arrow) steps back one at a time.
    private val sectionHistory = mutableStateListOf<Section>()

    fun show(s: Section) {
        if (s == section) return
        sectionHistory.add(section)
        if (sectionHistory.size > 30) sectionHistory.removeAt(0)
        section = s
    }

    fun back() {
        when {
            backStack.size > 1 -> backStack.removeAt(backStack.lastIndex)
            section == Section.ComicVine && cvQuery.isNotEmpty() -> cvQuery = ""
            !section.isComicVine && searchQuery.isNotEmpty() -> searchQuery = ""
            section != Section.Home -> section = sectionHistory.removeLastOrNull() ?: Section.Home
        }
    }

    val canGoBack: Boolean get() = backStack.size > 1 || section != Section.Home || searchQuery.isNotEmpty() || cvQuery.isNotEmpty()

    fun openComic(c: Comic) {
        noteOpened(c)
        backStack.add(Screen.Reader(c))
    }

    fun switchToComic(c: Comic) {
        noteOpened(c)
        backStack[backStack.lastIndex] = Screen.Reader(c)
    }

    /**
     * The next issue: next in the group's read order if the comic is in it, otherwise next in the
     * group (or in its folder until it's been grouped).
     */
    fun nextComic(c: Comic): Comic? {
        val group = groupOfKey[c.key]
        if (group != null && c.key in group.readOrder) {
            val order = readOrderOf(group)
            return order.getOrNull(order.indexOfFirst { it.key == c.key } + 1)
        }
        val list = group?.let(::comicsIn)
            ?: comics.filter { it.folderPath == c.folderPath }.sortedWith(compareBy(NaturalOrder) { it.name })
        val i = list.indexOfFirst { it.key == c.key }
        return if (i < 0) null else list.getOrNull(i + 1)
    }

    // Progress

    fun startPage(c: Comic, total: Int): Int {
        val p = progress[c.key] ?: return 0
        return if (p.finished) 0 else p.page.coerceIn(0, (total - 1).coerceAtLeast(0))
    }

    fun saveProgress(c: Comic, page: Int, total: Int) {
        val p = Progress(page, total, System.currentTimeMillis())
        progress[c.key] = p
        progressStore.save(c.key, p)
    }

    fun setRead(keys: Collection<String>, read: Boolean) {
        for (key in keys) {
            val p = progress[key]
            if (read) {
                val total = (p?.total ?: 1).coerceAtLeast(1)
                val done = Progress(total - 1, total, System.currentTimeMillis())
                progress[key] = done
                progressStore.save(key, done)
            } else {
                progress.remove(key)
                progressStore.clear(key)
            }
        }
    }

    // Reading options. Direction is remembered per group (or per comic until it's grouped);
    // page layout and fit apply everywhere.

    private fun modeKey(c: Comic): String = groupOfKey[c.key]?.let { "mode:g:${it.id}" } ?: "mode:c:${c.key}"

    fun readingMode(c: Comic): ReadingMode = enumValueOr(settings.getString(modeKey(c), null), ReadingMode.LeftToRight)

    fun setReadingMode(c: Comic, mode: ReadingMode) {
        settings.edit().putString(modeKey(c), mode.name).apply()
    }

    var pageLayout by mutableStateOf(enumValueOr(settings.getString("pageLayout", null), PageLayout.Auto))
        private set
    var fitWidth by mutableStateOf(settings.getBoolean("fitWidth", false))
        private set

    fun changePageLayout(l: PageLayout) {
        pageLayout = l
        settings.edit().putString("pageLayout", l.name).apply()
    }

    fun changeFitWidth(on: Boolean) {
        fitWidth = on
        settings.edit().putBoolean("fitWidth", on).apply()
    }
}

private val WHITESPACE = Regex("\\s+")
private val DIGITS = Regex("\\d+")

private inline fun <reified T : Enum<T>> enumValueOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
