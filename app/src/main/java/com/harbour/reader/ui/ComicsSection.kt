package com.harbour.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    var about by remember { mutableStateOf<Comic?>(null) }
    val selecting = selected.isNotEmpty()
    val shown = remember(comics, vm.sort, presorted) { if (presorted) comics else vm.sorted(comics) }
    // Dragging on a group page: hold an issue, then drag it within, into or out of the read order.
    val gridState = rememberLazyGridState()
    var dragKey by remember { mutableStateOf<String?>(null) }
    // Letting go after a hold must not also count as a tap that opens the comic.
    var suppressClick by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    // Where the dragged issue would land: a slot in the read order, or out of it.
    var dropIndex by remember { mutableStateOf<Int?>(null) }
    var dropRemove by remember { mutableStateOf(false) }
    val orderKeys = group?.readOrder ?: emptyList()
    val readOrder = orderKeys.mapNotNull { vm.comicsByKey[it] }
    val inOrder = orderKeys.toSet()
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
        selected.singleOrNull()?.let { vm.comicsByKey[it] }?.let { c ->
            add("About this issue" to {
                about = c
                done()
            })
        }
        if (group != null) {
            if (addsToOrder) add("Add to group" to { picking = true })
            if (selected.any { it in inOrder }) {
                add("Remove from read order" to {
                    vm.removeFromReadOrder(group.id, selected)
                    done()
                })
            }
        }
        if (selected.any { !vm.isWillRead(it) }) {
            add("Add to Will Read" to {
                vm.addToWillRead(selected)
                done()
            })
        }
        if (selected.any { vm.isWillRead(it) }) {
            add("Remove from Will Read" to {
                vm.removeFromWillRead(selected)
                done()
            })
        }
        val single = selected.singleOrNull()
        if (single != null && single in vm.groupOfKey) {
            add("Set as group cover" to {
                vm.setGroupCover(single)
                done()
            })
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

    about?.let { c -> ComicInfoDialog(vm, c, onDismiss = { about = null }) }

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

        // The gesture handler lives across recompositions, so it reads these through live references.
        val currentShown by rememberUpdatedState(shown)
        val currentGroup by rememberUpdatedState(group)
        val haptics = LocalHapticFeedback.current
        val dragModifier = if (group != null) {
            Modifier.readOrderDrag(
                gridState = gridState,
                groupId = group.id,
                comicFor = { itemKey -> currentShown.firstOrNull { it.uri == itemKey.removePrefix("order:") } },
                orderKeys = { currentGroup?.readOrder ?: emptyList() },
                // Holding selects straight away (like everywhere else); if you then drag, that's undone.
                onHold = { key ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    suppressClick = true
                    vm.comicsByKey[key]?.let(::toggle)
                },
                onStart = { key, p ->
                    vm.comicsByKey[key]?.let(::toggle)
                    dragKey = key
                    dragPos = p
                },
                onMove = { p -> dragPos = p },
                onTarget = { index, remove ->
                    dropIndex = index
                    dropRemove = remove
                },
                onDrop = { key, index, remove ->
                    val g = currentGroup
                    if (g != null && (remove || index != null)) {
                        val order = g.readOrder.toMutableList()
                        val from = order.indexOf(key)
                        if (remove) {
                            order.remove(key)
                        } else if (index != null) {
                            var to = index
                            if (from >= 0) {
                                order.removeAt(from)
                                if (from < to) to--
                            }
                            order.add(to.coerceIn(0, order.size), key)
                        }
                        vm.setReadOrder(g.id, order)
                    }
                },
                onFinish = { moved ->
                    dragKey = null
                    dropIndex = null
                    dropRemove = false
                    // After a plain hold the comic's own tap clears this; after a drag there is no tap.
                    if (moved) suppressClick = false
                },
            )
        } else {
            Modifier
        }

        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = vm.coverSize.minCell()),
            contentPadding = PaddingValues(
                start = 28.dp,
                end = 28.dp,
                top = 12.dp,
                bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize().then(dragModifier),
        ) {
            if (group != null && readOrder.isEmpty() && dragKey != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "order-label") { SectionLabel("Read order") }
                item(key = "order-drop") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                if (dropIndex == 0) 3.dp else 2.dp,
                                if (dropIndex == 0) HarbourColors.Accent else HarbourColors.Accent.copy(alpha = 0.5f),
                                RoundedCornerShape(10.dp),
                            ),
                    ) {
                        Text("Drop here", color = HarbourColors.Accent, style = MaterialTheme.typography.labelLarge)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "rest-label") { RestLabel(false) }
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
                    val marker = when {
                        dropIndex == i -> -1
                        dropIndex == readOrder.size && i == readOrder.lastIndex -> 1
                        else -> 0
                    }
                    ComicCard(
                        modifier = Modifier
                            .dropMarker(marker, HarbourColors.Accent)
                            .alpha(if (comic.key == dragKey) 0.35f else 1f),
                        comic = comic,
                        progress = vm.progress[comic.key],
                        subtitle = null,
                        selected = comic.key in selected,
                        selecting = selecting,
                        badge = i + 1,
                        badgeHighlighted = comic.key == upNext?.key,
                        onClick = {
                        when {
                            suppressClick -> suppressClick = false
                            selecting -> toggle(comic)
                            else -> vm.openComic(comic)
                        }
                    },
                        onLongClick = null, // held and dragged (or just held) by the grid instead
                    )
                }
                if (rest.isNotEmpty() || dropRemove) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "rest-label") { RestLabel(dropRemove) }
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
                    modifier = Modifier.alpha(if (comic.key == dragKey) 0.35f else 1f),
                    comic = comic,
                    progress = vm.progress[comic.key],
                    subtitle = if (group == null) vm.groupOfKey[comic.key]?.name else null,
                    selected = comic.key in selected,
                    selecting = selecting,
                    onClick = {
                        when {
                            suppressClick -> suppressClick = false
                            selecting -> toggle(comic)
                            else -> vm.openComic(comic)
                        }
                    },
                    onLongClick = if (group != null) null else ({ toggle(comic) }),
                )
            }
        }

        // The issue being dragged follows your finger.
        val dragging = dragKey?.let { vm.comicsByKey[it] }
        if (dragging != null) {
            val density = LocalDensity.current
            val w = with(density) { 96.dp.toPx() }
            val h = w * 1.5f
            CoverImage(
                dragging,
                Modifier
                    .offset { IntOffset((dragPos.x - w / 2).roundToInt(), (dragPos.y - h / 2).roundToInt()) }
                    .size(96.dp, 144.dp)
                    .graphicsLayer {
                        shadowElevation = 24f
                        alpha = 0.95f
                    },
            )
        }
        }
    }

    if (picking) {
        AddToGroupDialog(
            count = selected.size,
            groups = vm.orderedGroups,
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
        if (group != null) {
            IconButton(onClick = vm::back, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HarbourColors.Text)
            }
        }
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
    modifier: Modifier = Modifier,
    comic: Comic,
    progress: Progress?,
    subtitle: String?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    badge: Int? = null,
    badgeHighlighted: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
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
            if (!selecting && progress?.finished == true) {
                ReadBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            } else if (selecting) {
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
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            CoverProgress(if (progress.finished) 1f else progress.fraction)
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

/** The "All issues" heading; while you drag a read-order issue over it, it says it'll be removed. */
@Composable
private fun RestLabel(dropping: Boolean) {
    if (dropping) {
        Text(
            "DROP TO REMOVE FROM READ ORDER",
            style = MaterialTheme.typography.labelMedium,
            color = HarbourColors.Accent,
        )
    } else {
        SectionLabel("All issues")
    }
}

/** An accent bar just outside a card's left (-1) or right (1) edge, marking where a drop will land. */
private fun Modifier.dropMarker(side: Int, color: Color): Modifier = if (side == 0) this else drawWithContent {
    drawContent()
    val w = 4.dp.toPx()
    val gap = 9.dp.toPx() // half the 18dp gap between covers
    val x = if (side < 0) -gap - w / 2 else size.width + gap - w / 2
    drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(w, size.width * 1.5f), cornerRadius = CornerRadius(w / 2))
}

/**
 * Hold-and-drag on a group page. Holding selects the issue at once (as elsewhere). Once you move,
 * the grid stays put and [onTarget] reports where the issue would land: a slot in the read order
 * (left or right of the issue under your finger), or out of it (over All issues). [onDrop] applies
 * it when you let go. The grid scrolls when you drag near its top or bottom edge.
 */
private fun Modifier.readOrderDrag(
    gridState: LazyGridState,
    groupId: String,
    comicFor: (itemKey: String) -> Comic?,
    orderKeys: () -> List<String>,
    onHold: (key: String) -> Unit,
    onStart: (key: String, at: Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onTarget: (index: Int?, remove: Boolean) -> Unit,
    onDrop: (key: String, index: Int?, remove: Boolean) -> Unit,
    onFinish: (moved: Boolean) -> Unit,
): Modifier = pointerInput(groupId) {
    coroutineScope {
        var candidate: String? = null
        var pos = Offset.Zero
        var start = Offset.Zero
        var moved = false
        var index: Int? = null
        var remove = false
        var scroller: Job? = null

        fun itemAt(p: Offset) = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            p.x >= it.offset.x && p.x < it.offset.x + it.size.width &&
                p.y >= it.offset.y && p.y < it.offset.y + it.size.height
        }

        // In the gaps between covers nothing is hit, so the last target simply stays.
        fun updateTarget() {
            val k = candidate ?: return
            val hit = itemAt(pos) ?: return
            val key = hit.key as? String ?: return
            val order = orderKeys()
            val from = order.indexOf(k)
            var newIndex: Int? = null
            var newRemove = false
            when {
                key.startsWith("order:") -> {
                    val j = order.indexOf(comicFor(key)?.key ?: return)
                    if (j < 0) return
                    newIndex = if (pos.x > hit.offset.x + hit.size.width / 2f) j + 1 else j
                }
                key == "order-label" || key == "order-drop" -> newIndex = 0
                key == "rest-label" || comicFor(key) != null -> newRemove = from >= 0
                else -> return
            }
            // Dropping an issue right where it already is changes nothing, so show nothing.
            if (newIndex != null && from >= 0 && (newIndex == from || newIndex == from + 1)) newIndex = null
            if (newIndex != index || newRemove != remove) {
                index = newIndex
                remove = newRemove
                onTarget(index, remove)
            }
        }

        detectDragGesturesAfterLongPress(
            onDragStart = { p ->
                val key = itemAt(p)?.key as? String
                candidate = key?.let(comicFor)?.key
                if (candidate == null) return@detectDragGesturesAfterLongPress
                pos = p
                start = p
                moved = false
                index = null
                remove = false
                onHold(candidate!!)
            },
            onDrag = { change, amount ->
                val k = candidate ?: return@detectDragGesturesAfterLongPress
                change.consume()
                pos += amount
                // A deliberate move (not a wobble while holding) turns the hold into a drag.
                if (!moved && (pos - start).getDistance() > 24.dp.toPx()) {
                    moved = true
                    onStart(k, pos)
                    scroller = launch {
                        val edge = 96.dp.toPx()
                        while (isActive) {
                            val h = size.height
                            val speed = when {
                                pos.y < edge -> -(edge - pos.y) / 3f
                                pos.y > h - edge -> (pos.y - (h - edge)) / 3f
                                else -> 0f
                            }
                            if (speed != 0f) {
                                gridState.scrollBy(speed)
                                updateTarget()
                            }
                            delay(16)
                        }
                    }
                }
                if (moved) {
                    onMove(pos)
                    updateTarget()
                }
            },
            onDragEnd = {
                scroller?.cancel()
                val k = candidate
                if (k != null) {
                    if (moved) onDrop(k, index, remove)
                    onFinish(moved)
                }
                candidate = null
            },
            onDragCancel = {
                scroller?.cancel()
                if (candidate != null) onFinish(moved)
                candidate = null
            },
        )
    }
}
