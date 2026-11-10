package com.harbour.reader

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harbour.reader.ui.AccentChoice
import com.harbour.reader.ui.HarbourColors
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Full-screen places. The library itself is one screen with a sidebar of [Section]s. */
sealed interface Screen {
    data object Main : Screen
    data class Reader(val comic: Comic) : Screen
    data class BookReader(val book: Book) : Screen
    data object Browse : Screen
}

val Screen.stateKey: String
    get() = when (this) {
        Screen.Main -> "main"
        Screen.Browse -> "browse"
        is Screen.Reader -> "reader:${comic.key}"
        is Screen.BookReader -> "book-reader:${book.key}"
    }

/** What the library shows next to the sidebar. */
sealed interface Section {
    data object Home : Section
    data object AllComics : Section
    data object Ungrouped : Section
    data class InGroup(val id: String) : Section
    data object Books : Section
    data object Settings : Section
}

val Section.stateKey: String
    get() = when (this) {
        Section.Home -> "home"
        Section.AllComics -> "all"
        Section.Ungrouped -> "ungrouped"
        is Section.InGroup -> "group:$id"
        Section.Books -> "books"
        Section.Settings -> "settings"
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

    val sortedGroups: List<Group> by derivedStateOf { groups.sortedWith(compareBy(NaturalOrder) { it.name }) }

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

    // Updates

    private val updater = Updater(app)
    val currentBuild: Int get() = updater.currentBuild
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    init {
        if (roots.isNotEmpty()) rescan()
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

    fun createGroup(name: String, keys: Collection<String> = emptyList()): Group {
        val g = Group(UUID.randomUUID().toString(), name.trim().ifEmpty { "New group" }, emptyList())
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
    }

    /** Moves comics into a group. A comic is only ever in one group. */
    fun addToGroup(id: String, keys: Collection<String>) {
        val moving = keys.toSet()
        groups = groups.map { g ->
            val kept = g.keys.filterNot { it in moving }
            if (g.id == id) g.copy(keys = kept + moving) else g.copy(keys = kept)
        }
        groupStore.save(groups)
    }

    fun removeFromGroups(keys: Collection<String>) {
        val leaving = keys.toSet()
        groups = groups.map { g -> g.copy(keys = g.keys.filterNot { it in leaving }) }
        groupStore.save(groups)
    }

    // Navigation

    fun show(s: Section) {
        section = s
    }

    fun back() {
        when {
            backStack.size > 1 -> backStack.removeAt(backStack.lastIndex)
            section != Section.Home -> section = Section.Home
        }
    }

    val canGoBack: Boolean get() = backStack.size > 1 || section != Section.Home

    fun openComic(c: Comic) {
        backStack.add(Screen.Reader(c))
    }

    fun switchToComic(c: Comic) {
        backStack[backStack.lastIndex] = Screen.Reader(c)
    }

    /** The next issue: next in the comic's group, or in its folder until it's been grouped. */
    fun nextComic(c: Comic): Comic? {
        val list = groupOfKey[c.key]?.let(::comicsIn)
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

private inline fun <reified T : Enum<T>> enumValueOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
