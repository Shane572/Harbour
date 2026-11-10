package com.harbour.reader

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Library : Screen
    data class SeriesView(val path: String) : Screen
    data class Reader(val comic: Comic) : Screen
}

val Screen.stateKey: String
    get() = when (this) {
        Screen.Library -> "library"
        is Screen.SeriesView -> "series:$path"
        is Screen.Reader -> "reader:${comic.key}"
    }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LibraryRepository(app)
    private val progressStore = ProgressStore(app)
    private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val covers = CoverCache(app)

    var roots by mutableStateOf(repo.roots())
        private set
    var comics by mutableStateOf(repo.loadCached())
        private set
    var scanning by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    val progress = mutableStateMapOf<String, Progress>().apply { putAll(progressStore.all()) }
    val backStack = mutableStateListOf<Screen>(Screen.Library)
    val screen: Screen get() = backStack.last()

    val series: List<Series> by derivedStateOf {
        comics.groupBy { it.seriesPath }
            .map { (path, list) -> Series(path, list.first().seriesName, list) }
            .sortedWith(compareBy(NaturalOrder) { it.name })
    }

    val continueReading: List<Comic> by derivedStateOf {
        comics.mapNotNull { c -> progress[c.key]?.takeIf { !it.finished }?.let { c to it.time } }
            .sortedByDescending { it.second }
            .take(20)
            .map { it.first }
    }

    private val updater = Updater(app)
    val currentBuild: Int get() = updater.currentBuild
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    init {
        if (roots.isNotEmpty()) rescan()
        checkForUpdate(quiet = true)
    }

    // Updates

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

    fun addRoot(uri: Uri) {
        try {
            roots = repo.addRoot(uri)
            rescan()
        } catch (e: Exception) {
            message = "Couldn't get access to that folder"
        }
    }

    fun removeRoot(root: LibraryRoot) {
        roots = repo.removeRoot(root)
        rescan()
    }

    fun rescan() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            try {
                val result = repo.scan(roots)
                comics = result.comics
                if (result.failed.isNotEmpty()) {
                    message = "Lost access to ${result.failed.joinToString { it.name }}. Remove it and add it again."
                }
            } finally {
                scanning = false
            }
        }
    }

    // Navigation

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openSeries(s: Series) {
        if (s.comics.size == 1) openComic(s.comics.first()) else backStack.add(Screen.SeriesView(s.path))
    }

    fun openComic(c: Comic) {
        backStack.add(Screen.Reader(c))
    }

    fun switchToComic(c: Comic) {
        backStack[backStack.lastIndex] = Screen.Reader(c)
    }

    fun seriesByPath(path: String): Series? = series.firstOrNull { it.path == path }

    fun nextComic(c: Comic): Comic? {
        val list = comics.filter { it.seriesPath == c.seriesPath }
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

    fun toggleRead(c: Comic) {
        val p = progress[c.key]
        if (p?.finished == true) {
            progress.remove(c.key)
            progressStore.clear(c.key)
        } else {
            val total = (p?.total ?: 1).coerceAtLeast(1)
            saveProgress(c, total - 1, total)
        }
    }

    // Per-series reading direction (handy for manga)

    fun isRtl(seriesPath: String): Boolean = settings.getBoolean("rtl:$seriesPath", false)

    fun setRtl(seriesPath: String, rtl: Boolean) {
        settings.edit().putBoolean("rtl:$seriesPath", rtl).apply()
    }
}
