package com.harbour.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harbour.reader.Comic
import com.harbour.reader.CoverSize
import com.harbour.reader.Group
import com.harbour.reader.MainViewModel
import com.harbour.reader.Progress
import com.harbour.reader.Section
import com.harbour.reader.stateKey

/** The library: the sidebar on the left and the chosen section beside it. */
@Composable
fun MainScreen(vm: MainViewModel) {
    Row(
        Modifier
            .fillMaxSize()
            .background(HarbourColors.Bg),
    ) {
        Sidebar(vm)
        VerticalDivider(color = HarbourColors.Outline)
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
        ) {
            SearchBar(vm)
            val holder = rememberSaveableStateHolder()
            val section = vm.section
            if (vm.searchQuery.isNotBlank()) {
                holder.SaveableStateProvider("search") { SearchResults(vm) }
            } else holder.SaveableStateProvider(section.stateKey) {
                when (section) {
                    Section.Home -> HomeSection(vm)
                    Section.AllComics -> ComicsSection(vm, "All comics", vm.comics, group = null)
                    Section.Ungrouped -> ComicsSection(vm, "Ungrouped", vm.ungrouped, group = null)
                    is Section.InGroup -> {
                        val group = vm.groupById(section.id)
                        if (group == null) {
                            LaunchedEffect(section.id) { vm.show(Section.Home) }
                        } else {
                            val comics = remember(group, vm.comics) { vm.comicsIn(group) }
                            ComicsSection(vm, group.name, comics, group)
                        }
                    }
                    Section.Books -> BooksSection(vm)
                    Section.Settings -> SettingsSection(vm)
                }
            }
        }
    }
}

/** Top and bottom padding that keeps content clear of the status and navigation bars. */
@Composable
fun sectionPadding(top: Dp = 12.dp, bottom: Dp = 32.dp): PaddingValues {
    // The search bar above already keeps clear of the status bar.
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()
    return PaddingValues(
        start = 28.dp,
        end = 28.dp,
        top = top,
        bottom = insets.calculateBottomPadding() + bottom,
    )
}

fun CoverSize.minCell(): Dp = when (this) {
    CoverSize.Small -> 112.dp
    CoverSize.Medium -> 148.dp
    CoverSize.Large -> 196.dp
}

@Composable
private fun HomeSection(vm: MainViewModel) {
    // Just three groups: the ones you last read from, topped up A–Z. All groups are in the sidebar.
    val recent = vm.recentGroups
    val groups = remember(vm.sortedGroups, recent) {
        val ids = recent.map { it.id }.toSet()
        (recent + vm.sortedGroups.filter { it.id !in ids }).take(3)
    }
    val continueReading = vm.continueReading

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = vm.coverSize.minCell()),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.displaySmall, color = HarbourColors.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (vm.scanning && vm.comics.isEmpty()) "Looking for comics…"
                        else "${vm.comics.size} comics · ${vm.groups.size} groups",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarbourColors.Muted,
                    )
                }
            }
        }

        if (vm.update.showsBanner) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "update") { UpdateBanner(vm) }
        }

        if (continueReading.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                Column {
                    SectionLabel("Continue reading")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(continueReading, key = { it.uri }) { comic ->
                            ContinueCard(comic, vm.subtitleFor(comic), vm.progress[comic.key]) { vm.openComic(comic) }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "groups-label") { SectionLabel("Groups") }

        if (groups.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "groups-hint") {
                Hint(
                    "Make your first group",
                    "Open All comics or Ungrouped, long-press a comic to start selecting, tap the others " +
                        "that belong together, then tap Add to group. You can also tap + next to Groups.",
                )
            }
        }

        items(groups, key = { it.id }) { g ->
            val comics = remember(g, vm.comics) { vm.comicsIn(g) }
            GroupCard(g, comics, cover = vm.coverOf(g), readCount = comics.count { vm.progress[it.key]?.finished == true }) {
                vm.show(Section.InGroup(g.id))
            }
        }

        val added = vm.recentlyAdded
        if (added.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                Column {
                    SectionLabel("Recently added")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(added, key = { it.uri }) { comic ->
                            Column(
                                Modifier
                                    .width(116.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { vm.openComic(comic) },
                            ) {
                                CoverImage(comic, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    comic.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HarbourColors.Text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (vm.comics.isEmpty() && !vm.scanning) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nothing") {
                Hint(
                    "No comics found yet",
                    "Harbour looks for .cbz files in your folders and everything inside them. " +
                        "Check your folders in Settings, or tap refresh after adding files.",
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = HarbourColors.Muted)
}

@Composable
fun Hint(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HarbourColors.Surface)
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
    }
}

@Composable
private fun ContinueCard(comic: Comic, subtitle: String, progress: Progress?, onClick: () -> Unit) {
    Row(
        Modifier
            .width(320.dp)
            .height(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HarbourColors.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(Modifier.width(68.dp).fillMaxHeight()) {
            CoverImage(comic, Modifier.fillMaxWidth().weight(1f), corner = 8.dp)
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                CoverProgress(progress.fraction)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp)) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                comic.title,
                style = MaterialTheme.typography.titleSmall,
                color = HarbourColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A group shown as a little stack of covers. */
@Composable
private fun GroupCard(group: Group, comics: List<Comic>, cover: Comic?, readCount: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.64f),
        ) {
            if (comics.size > 1) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HarbourColors.SurfaceHighest),
                )
            }
            CoverImage(
                cover,
                Modifier
                    .fillMaxSize()
                    .padding(top = if (comics.size > 1) 7.dp else 0.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            group.name,
            style = MaterialTheme.typography.titleSmall,
            color = HarbourColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                append(if (comics.size == 1) "1 comic" else "${comics.size} comics")
                if (readCount > 0) append(" · $readCount read")
            },
            style = MaterialTheme.typography.labelSmall,
            color = HarbourColors.Muted,
        )
    }
}

/** First run: no folders yet. */
@Composable
fun WelcomeScreen(vm: MainViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(HarbourColors.Bg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Updates must be reachable even before any folder is added.
        Box(Modifier.widthIn(max = 560.dp)) { UpdateBanner(vm) }
        if (vm.update.showsBanner) Spacer(Modifier.height(28.dp))
        Text("HARBOUR", style = MaterialTheme.typography.labelMedium, color = HarbourColors.Accent)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your comics, beautifully",
            style = MaterialTheme.typography.displaySmall,
            color = HarbourColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Choose the folder where your comics live. Harbour finds every comic inside it, " +
                "and you sort them into groups however you like.",
            style = MaterialTheme.typography.bodyLarge,
            color = HarbourColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = vm::openBrowser,
            colors = ButtonDefaults.buttonColors(containerColor = HarbourColors.Accent, contentColor = HarbourColors.OnAccent),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("Choose folder", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Search and refresh, at the top of every library page. */
@Composable
private fun SearchBar(vm: MainViewModel) {
    val focus = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(start = 28.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(HarbourColors.Surface)
                .padding(start = 16.dp, end = 4.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = HarbourColors.Muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = vm.searchQuery,
                onValueChange = vm::search,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = HarbourColors.Text),
                cursorBrush = SolidColor(HarbourColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (vm.searchQuery.isEmpty()) {
                            Text(
                                "Search, e.g. Green Lantern 48",
                                style = MaterialTheme.typography.bodyLarge,
                                color = HarbourColors.Muted,
                                maxLines = 1,
                            )
                        }
                        field()
                    }
                },
            )
            if (vm.searchQuery.isNotEmpty()) {
                IconButton(onClick = {
                    vm.search("")
                    focus.clearFocus()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = HarbourColors.Muted)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (vm.scanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = HarbourColors.Accent, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = vm::rescan) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Look for new and removed files", tint = HarbourColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun SearchResults(vm: MainViewModel) {
    val q = vm.searchQuery
    val comics = remember(q, vm.comics, vm.groups) { vm.searchComics(q) }
    val books = remember(q, vm.books) { vm.searchBooks(q) }
    ComicsSection(
        vm = vm,
        title = "Results",
        comics = comics,
        group = null,
        subtitle = buildString {
            append(if (comics.size == 1) "1 comic" else "${comics.size} comics")
            if (books.isNotEmpty()) append(if (books.size == 1) " · 1 book" else " · ${books.size} books")
        },
        presorted = true,
        emptyHint = if (books.isEmpty()) {
            "No matches" to "Try fewer words, or just the group name and issue number, like \"lantern 48\"."
        } else {
            null
        },
        extraItems = if (books.isEmpty()) {
            null
        } else {
            {
                item(span = { GridItemSpan(maxLineSpan) }, key = "books-label") { SectionLabel("Books") }
                items(books, key = { "book:" + it.uri }) { b ->
                    BookCard(vm, b)
                }
                if (comics.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "comics-label") { SectionLabel("Comics") }
                }
            }
        },
    )
}
