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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.unit.sp
import com.harbour.reader.Comic
import com.harbour.reader.Group
import com.harbour.reader.MainViewModel
import com.harbour.reader.Progress
import com.harbour.reader.SortOrder

/**
 * A grid of comics: All comics, Ungrouped, one group, or search results. Long-press starts
 * selecting, which is how comics get sorted into groups and read orders (or deleted).
 */
@Composable
fun ComicsSection(
    vm: MainViewModel,
    title: String,
    comics: List<Comic>,
    group: Group?,
    subtitle: String? = null,
    presorted: Boolean = false,
    emptyHint: Pair<String, String>? = null,
    extraItems: (LazyGridScope.() -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val shown = remember(comics, vm.sort, presorted) { if (presorted) comics else vm.sorted(comics) }
    val readOrder = remember(group, vm.comics) { group?.let(vm::readOrderOf) ?: emptyList() }
    val inOrder = group?.readOrder?.toSet() ?: emptySet()
    val rest = if (inOrder.isEmpty()) shown else shown.filter { it.key !in inOrder }
    val upNext = group?.let(vm::upNext)

    BackHandler(enabled = selecting) { selected = emptySet() }

    fun toggle(c: Comic) {
        selected = if (c.key in selected) selected - c.key else selected + c.key
    }

    fun done() {
        selected = emptySet()
    }

    val addsToOrder = group != null && selected.any { it !in inOrder }
    val actions = buildList<Pair<String, () -> Unit>> {
        add("Select all" to { selected = shown.map { it.key }.toSet() })
        if (group != null) {
            if (addsToOrder) add("Add to group" to { picking = true })
            if (selected.any { it in inOrder }) {
                add("Remove from read order" to {
                    vm.removeFromReadOrder(group.id, selected)
                    done()
                })
            }
            val only = selected.singleOrNull()
            if (only != null && only in inOrder) {
                add("Move earlier in read order" to { vm.moveInReadOrder(group.id, only, -1) })
                add("Move later in read order" to { vm.moveInReadOrder(group.id, only, 1) })
            }
        }
        if (selected.any { it in vm.groupOfKey }) {
            add("Remove from group" to {
                vm.removeFromGroups(selected)
                done()
            })
        }
        add("Mark as read" to {
            vm.setRead(selected, true)
            done()
        })
        add("Mark as unread" to {
            vm.setRead(selected, false)
            done()
        })
        add("Delete from device…" to { confirmDelete = true })
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        ) {
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    primaryLabel = if (addsToOrder) "Add to read order" else "Add to group",
                    onPrimary = {
                        if (addsToOrder) {
                            vm.addToReadOrder(group.id, selected)
                            done()
                        } else {
                            picking = true
                        }
                    },
                    actions = actions,
                    onClose = ::done,
                )
            } else {
                SectionHeader(
                    vm = vm,
                    title = title,
                    subtitle = subtitle ?: if (comics.size == 1) "1 comic" else "${comics.size} comics",
                    group = group,
                    showSort = !presorted,
                )
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
            if (upNext != null && !selecting) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "resume") {
                    val started = vm.progress[upNext.key] != null
                    Column {
                        Button(
                            onClick = { vm.openComic(upNext) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HarbourColors.Accent,
                                contentColor = HarbourColors.OnAccent,
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Text(
                                (if (started) "Continue · " else "Up next · ") + upNext.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (readOrder.isEmpty() && comics.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tip: long-press issues and tap Add to read order to line up what you read next.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HarbourColors.Muted,
                            )
                        }
                    }
                }
            }

            if (readOrder.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "order-label") {
                    val read = readOrder.count { vm.progress[it.key]?.finished == true }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Read order")
                        Spacer(Modifier.width(10.dp))
                        Text("$read of ${readOrder.size} read", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
                    }
                }
                itemsIndexed(readOrder, key = { _, c -> "order:" + c.uri }) { i, comic ->
                    ComicCard(
                        comic = comic,
                        progress = vm.progress[comic.key],
                        subtitle = null,
                        selected = comic.key in selected,
                        selecting = selecting,
                        badge = i + 1,
                        badgeHighlighted = comic.key == upNext?.key,
                        onClick = { if (selecting) toggle(comic) else vm.openComic(comic) },
                        onLongClick = { toggle(comic) },
                    )
                }
                if (rest.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "rest-label") { SectionLabel("All issues") }
                }
            }

            extraItems?.invoke(this)

            if (comics.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    val (hintTitle, hintBody) = emptyHint ?: when {
                        group != null -> "This group is empty" to
                            "Go to All comics or Ungrouped, long-press comics to select them, then tap Add to group."
                        title == "Ungrouped" -> "Nothing here" to "Every comic is in a group."
                        else -> "Nothing here" to "Add a folder in Settings, or tap refresh after adding files."
                    }
                    Hint(hintTitle, hintBody)
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

            items(rest, key = { it.uri }) { comic ->
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
                done()
                vm.message = "Added to ${g.name}"
            },
            onCreate = { name ->
                val g = vm.createGroup(name, selected)
                picking = false
                done()
                vm.message = "Made the group ${g.name}"
            },
        )
    }

    if (confirmDelete) {
        val n = selected.size
        ConfirmDialog(
            title = if (n == 1) "Delete this comic from the tablet?" else "Delete $n comics from the tablet?",
            text = "This permanently deletes the files from your storage, not just from Harbour. It can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                vm.deleteFiles(selected.mapNotNull { vm.comicsByKey[it] })
                confirmDelete = false
                done()
            },
        )
    }
}

@Composable
private fun SectionHeader(vm: MainViewModel, title: String, subtitle: String, group: Group?, showSort: Boolean) {
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
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted,
            )
        }
        if (showSort) Box {
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
    primaryLabel: String,
    onPrimary: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    onClose: () -> Unit,
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
            onClick = onPrimary,
            colors = ButtonDefaults.buttonColors(containerColor = HarbourColors.Accent, contentColor = HarbourColors.OnAccent),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(primaryLabel)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = HarbourColors.Text)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                actions.forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        menu = false
                        action()
                    })
                }
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
    badge: Int? = null,
    badgeHighlighted: Boolean = false,
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
            if (badge != null) {
                // Position in the read order; the next one to read is picked out in the accent colour.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (badgeHighlighted) HarbourColors.Accent else Color.Black.copy(alpha = 0.7f)),
                ) {
                    Text(
                        "$badge",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
                        color = if (badgeHighlighted) HarbourColors.OnAccent else Color.White,
                    )
                }
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
