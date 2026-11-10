package com.harbour.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harbour.reader.Comic
import com.harbour.reader.Group
import com.harbour.reader.MainViewModel
import com.harbour.reader.Progress
import com.harbour.reader.SortOrder

/**
 * A grid of comics: All comics, Ungrouped, or one group. Long-press starts selecting, which is
 * how comics get sorted into groups.
 */
@Composable
fun ComicsSection(vm: MainViewModel, title: String, comics: List<Comic>, group: Group?) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val shown = remember(comics, vm.sort) { vm.sorted(comics) }

    BackHandler(enabled = selecting) { selected = emptySet() }

    fun toggle(c: Comic) {
        selected = if (c.key in selected) selected - c.key else selected + c.key
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        ) {
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    anyGrouped = selected.any { it in vm.groupOfKey },
                    onClose = { selected = emptySet() },
                    onSelectAll = { selected = shown.map { it.key }.toSet() },
                    onAddToGroup = { picking = true },
                    onRemoveFromGroup = {
                        vm.removeFromGroups(selected)
                        selected = emptySet()
                    },
                    onMarkRead = { read ->
                        vm.setRead(selected, read)
                        selected = emptySet()
                    },
                )
            } else {
                SectionHeader(vm, title, comics.size, group)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = vm.coverSize.minCell()),
            contentPadding = PaddingValues(
                start = 28.dp,
                end = 28.dp,
                top = 12.dp,
                bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (group != null && comics.isNotEmpty() && !selecting) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "resume") {
                    val next = comics.firstOrNull { vm.progress[it.key]?.finished != true } ?: comics.first()
                    val started = vm.progress[next.key] != null
                    Row {
                        Button(
                            onClick = { vm.openComic(next) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HarbourColors.Accent,
                                contentColor = HarbourColors.OnAccent,
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Text(
                                (if (started) "Continue · " else "Start · ") + next.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }

            if (comics.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Hint(
                        if (group != null) "This group is empty" else "Nothing here",
                        when {
                            group != null -> "Go to All comics or Ungrouped, long-press comics to select them, then tap Add to group."
                            title == "Ungrouped" -> "Every comic is in a group."
                            else -> "Add a folder in Settings, or tap refresh on Home after adding files."
                        },
                    )
                }
            } else if (!selecting && group == null && vm.groups.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "tip") {
                    Text(
                        "Tip: long-press a comic to start selecting, then add your picks to a group.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HarbourColors.Muted,
                    )
                }
            }

            items(shown, key = { it.uri }) { comic ->
                ComicCard(
                    comic = comic,
                    progress = vm.progress[comic.key],
                    subtitle = if (group == null) vm.groupOfKey[comic.key]?.name else null,
                    selected = comic.key in selected,
                    selecting = selecting,
                    onClick = { if (selecting) toggle(comic) else vm.openComic(comic) },
                    onLongClick = { toggle(comic) },
                )
            }
        }
    }

    if (picking) {
        AddToGroupDialog(
            count = selected.size,
            groups = vm.sortedGroups,
            onDismiss = { picking = false },
            onPick = { g ->
                vm.addToGroup(g.id, selected)
                picking = false
                selected = emptySet()
                vm.message = "Added to ${g.name}"
            },
            onCreate = { name ->
                val g = vm.createGroup(name, selected)
                picking = false
                selected = emptySet()
                vm.message = "Made the group ${g.name}"
            },
        )
    }
}

@Composable
private fun SectionHeader(vm: MainViewModel, title: String, count: Int, group: Group?) {
    var sortMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = HarbourColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (count == 1) "1 comic" else "$count comics",
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted,
            )
        }
        Box {
            TextButton(onClick = { sortMenu = true }) {
                Text(vm.sort.label, color = HarbourColors.Muted)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Sort", tint = HarbourColors.Muted)
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SortOrder.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label, color = if (s == vm.sort) HarbourColors.Accent else HarbourColors.Text) },
                        onClick = {
                            vm.changeSort(s)
                            sortMenu = false
                        },
                    )
                }
            }
        }
        if (group != null) {
            Box {
                IconButton(onClick = { groupMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Group options", tint = HarbourColors.Muted)
                }
                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename group") }, onClick = {
                        groupMenu = false
                        renaming = true
                    })
                    DropdownMenuItem(text = { Text("Delete group") }, onClick = {
                        groupMenu = false
                        deleting = true
                    })
                }
            }
        }
    }

    if (group != null && renaming) {
        NameDialog(
            title = "Rename group",
            confirmLabel = "Rename",
            initial = group.name,
            onDismiss = { renaming = false },
            onConfirm = {
                vm.renameGroup(group.id, it)
                renaming = false
            },
        )
    }
    if (group != null && deleting) {
        ConfirmDialog(
            title = "Delete ${group.name}?",
            text = "The comics stay in your library and go back to Ungrouped.",
            confirmLabel = "Delete",
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                vm.deleteGroup(group.id)
            },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    anyGrouped: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToGroup: () -> Unit,
    onRemoveFromGroup: () -> Unit,
    onMarkRead: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HarbourColors.SurfaceHigh)
            .padding(horizontal = 6.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Stop selecting", tint = HarbourColors.Text)
        }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = HarbourColors.Text,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onAddToGroup,
            colors = ButtonDefaults.buttonColors(containerColor = HarbourColors.Accent, contentColor = HarbourColors.OnAccent),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text("Add to group")
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = HarbourColors.Text)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Select all") }, onClick = {
                    menu = false
                    onSelectAll()
                })
                if (anyGrouped) {
                    DropdownMenuItem(text = { Text("Remove from group") }, onClick = {
                        menu = false
                        onRemoveFromGroup()
                    })
                }
                DropdownMenuItem(text = { Text("Mark as read") }, onClick = {
                    menu = false
                    onMarkRead(true)
                })
                DropdownMenuItem(text = { Text("Mark as unread") }, onClick = {
                    menu = false
                    onMarkRead(false)
                })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicCard(
    comic: Comic,
    progress: Progress?,
    subtitle: String?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
            CoverImage(
                comic,
                Modifier
                    .fillMaxSize()
                    .then(
                        if (selected) Modifier.border(3.dp, HarbourColors.Accent, RoundedCornerShape(10.dp)) else Modifier,
                    ),
            )
            if (progress?.finished == true || selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = if (selected) 0.25f else 0.45f)),
                )
            }
            if (selecting || progress?.finished == true) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                selected -> HarbourColors.Accent
                                selecting -> Color.Black.copy(alpha = 0.55f)
                                else -> HarbourColors.Accent
                            },
                        )
                        .then(if (selecting && !selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected || (!selecting && progress?.finished == true)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = if (selected) "Selected" else "Read",
                            tint = HarbourColors.OnAccent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
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
            color = if (progress?.finished == true) HarbourColors.Muted else HarbourColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
