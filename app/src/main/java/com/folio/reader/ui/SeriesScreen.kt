package com.folio.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.reader.Comic
import com.folio.reader.MainViewModel
import com.folio.reader.Progress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(vm: MainViewModel, path: String) {
    val series = vm.seriesByPath(path)
    if (series == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val next = series.comics.firstOrNull { vm.progress[it.key]?.finished != true } ?: series.comics.first()
    val nextStarted = vm.progress[next.key] != null

    Scaffold(
        containerColor = FolioColors.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(series.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${series.comics.size} issues",
                            style = MaterialTheme.typography.labelSmall,
                            color = FolioColors.Muted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FolioColors.Bg,
                    scrolledContainerColor = FolioColors.Surface,
                    titleContentColor = FolioColors.Text,
                    navigationIconContentColor = FolioColors.Text,
                ),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "resume") {
                Column {
                    Button(
                        onClick = { vm.openComic(next) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FolioColors.Accent,
                            contentColor = FolioColors.OnAccent,
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text(
                            (if (nextStarted) "Continue · " else "Start · ") + next.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Long-press an issue to mark it read or unread.",
                        style = MaterialTheme.typography.labelSmall,
                        color = FolioColors.Muted,
                    )
                }
            }
            items(series.comics, key = { it.key }) { comic ->
                IssueCard(
                    comic = comic,
                    progress = vm.progress[comic.key],
                    onClick = { vm.openComic(comic) },
                    onLongClick = { vm.toggleRead(comic) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IssueCard(comic: Comic, progress: Progress?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            CoverImage(comic, Modifier.fillMaxSize())
            if (progress?.finished == true) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(FolioColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Read",
                        tint = FolioColors.OnAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else if (progress != null) {
                ProgressLine(
                    progress.fraction,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                    track = Color.Black.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            comic.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (progress?.finished == true) FolioColors.Muted else FolioColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
