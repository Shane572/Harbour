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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harbour.reader.Comic
import com.harbour.reader.MainViewModel
import com.harbour.reader.Series

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: MainViewModel, onAddFolder: () -> Unit) {
    var showFolders by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(HarbourColors.Bg),
    ) {
        if (vm.roots.isEmpty()) {
            EmptyLibrary(onAddFolder)
        } else {
            LibraryGrid(vm, onOpenFolders = { showFolders = true })
        }
    }

    if (showFolders) {
        ModalBottomSheet(
            onDismissRequest = { showFolders = false },
            containerColor = HarbourColors.Surface,
        ) {
            FoldersSheet(vm, onAddFolder = {
                showFolders = false
                onAddFolder()
            })
        }
    }
}

@Composable
private fun LibraryGrid(vm: MainViewModel, onOpenFolders: () -> Unit) {
    val series = vm.series
    val continueReading = vm.continueReading
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = insets.calculateTopPadding() + 20.dp,
            bottom = insets.calculateBottomPadding() + 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Header(
                comicCount = vm.comics.size,
                seriesCount = series.size,
                scanning = vm.scanning,
                onRefresh = vm::rescan,
                onFolders = onOpenFolders,
            )
        }

        if (continueReading.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                Column {
                    SectionLabel("Continue reading")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(continueReading, key = { it.key }) { comic ->
                            ContinueCard(comic, vm.progress[comic.key]?.let { it.page to it.total }) {
                                vm.openComic(comic)
                            }
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "series-label") {
            SectionLabel(if (series.isEmpty() && !vm.scanning) "No comics found yet" else "Series")
        }

        if (series.isEmpty() && !vm.scanning) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "nothing") {
                Text(
                    "Harbour looks for .cbz files in your folders and their subfolders. " +
                        "Check the folder in settings, or tap refresh after adding files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarbourColors.Muted,
                )
            }
        }

        items(series, key = { it.path }) { s ->
            val read = s.comics.count { vm.progress[it.key]?.finished == true }
            SeriesCard(s, read) { vm.openSeries(s) }
        }
    }
}

@Composable
private fun Header(
    comicCount: Int,
    seriesCount: Int,
    scanning: Boolean,
    onRefresh: () -> Unit,
    onFolders: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("HARBOUR", style = MaterialTheme.typography.labelMedium, color = HarbourColors.Accent)
            Spacer(Modifier.height(6.dp))
            Text("Library", style = MaterialTheme.typography.displaySmall, color = HarbourColors.Text)
            Spacer(Modifier.height(4.dp))
            Text(
                if (scanning && comicCount == 0) "Looking for comics…"
                else "$comicCount comics · $seriesCount series",
                style = MaterialTheme.typography.bodyMedium,
                color = HarbourColors.Muted,
            )
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = HarbourColors.Accent,
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Rescan folders", tint = HarbourColors.Muted)
                }
            }
        }
        IconButton(onClick = onFolders) {
            Icon(Icons.Filled.Settings, contentDescription = "Folders", tint = HarbourColors.Muted)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = HarbourColors.Muted)
}

@Composable
private fun ContinueCard(comic: Comic, pages: Pair<Int, Int>?, onClick: () -> Unit) {
    Row(
        Modifier
            .width(320.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HarbourColors.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        CoverImage(comic, Modifier.fillMaxHeight().aspectRatio(2f / 3f), corner = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp)) {
            Text(
                comic.seriesName,
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
            Spacer(Modifier.weight(1f))
            if (pages != null) {
                val (page, total) = pages
                ProgressLine(if (total <= 1) 0f else page.toFloat() / (total - 1))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Page ${page + 1} of $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = HarbourColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun SeriesCard(series: Series, readCount: Int, onClick: () -> Unit) {
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
            if (series.comics.size > 1) {
                // A second "issue" peeking out behind the cover, so folders read as stacks.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HarbourColors.SurfaceHighest),
                )
            }
            CoverImage(
                series.comics.first(),
                Modifier
                    .fillMaxSize()
                    .padding(top = if (series.comics.size > 1) 7.dp else 0.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            series.name,
            style = MaterialTheme.typography.titleSmall,
            color = HarbourColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                append(if (series.comics.size == 1) "1 issue" else "${series.comics.size} issues")
                if (readCount > 0) append(" · $readCount read")
            },
            style = MaterialTheme.typography.labelSmall,
            color = HarbourColors.Muted,
        )
    }
}

@Composable
private fun EmptyLibrary(onAddFolder: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
            "Choose the folder where your comics live. Harbour finds every .cbz inside it, " +
                "and each subfolder becomes a series.",
            style = MaterialTheme.typography.bodyLarge,
            color = HarbourColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAddFolder,
            colors = ButtonDefaults.buttonColors(containerColor = HarbourColors.Accent, contentColor = HarbourColors.OnAccent),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("Choose folder", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun FoldersSheet(vm: MainViewModel, onAddFolder: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text("Folders", style = MaterialTheme.typography.titleLarge, color = HarbourColors.Text)
        Spacer(Modifier.height(4.dp))
        Text(
            "Harbour scans these folders and everything inside them.",
            style = MaterialTheme.typography.bodyMedium,
            color = HarbourColors.Muted,
        )
        Spacer(Modifier.height(16.dp))
        vm.roots.forEach { root ->
            val count = vm.comics.count { it.seriesPath == root.name || it.seriesPath.startsWith(root.name + "/") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(root.name, style = MaterialTheme.typography.titleMedium, color = HarbourColors.Text)
                    Text("$count comics", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
                }
                IconButton(onClick = { vm.removeRoot(root) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${root.name}", tint = HarbourColors.Muted)
                }
            }
            HorizontalDivider(color = HarbourColors.Outline)
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onAddFolder) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add folder")
        }
    }
}
