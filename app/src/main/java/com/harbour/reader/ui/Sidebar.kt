package com.harbour.reader.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harbour.reader.Group
import com.harbour.reader.MainViewModel
import com.harbour.reader.R
import com.harbour.reader.Section

private val ExpandedWidth = 272.dp
private val CollapsedWidth = 76.dp

/**
 * The collapsible panel on the left. It's always laid out at full width and clipped, so when
 * collapsed only the icon column (and group covers) shows.
 */
@Composable
fun Sidebar(vm: MainViewModel) {
    val expanded = vm.sidebarExpanded
    val width by animateDpAsState(if (expanded) ExpandedWidth else CollapsedWidth, tween(220), label = "sidebar")
    val labels by animateFloatAsState(if (expanded) 1f else 0f, tween(180), label = "labels")
    var newGroup by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxHeight()
            .width(width)
            .clipToBounds()
            .background(HarbourColors.Surface),
    ) {
        Column(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(ExpandedWidth)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .padding(vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, bottom = 10.dp)) {
                IconButton(onClick = vm::toggleSidebar) {
                    Icon(Icons.Filled.Menu, contentDescription = "Show or hide the panel", tint = HarbourColors.Muted)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "HARBOUR",
                    style = MaterialTheme.typography.labelMedium,
                    color = HarbourColors.Accent,
                    modifier = Modifier.alpha(labels),
                )
            }

            val section = vm.section
            NavItem(rememberVectorPainter(Icons.Filled.Home), "Home", section == Section.Home, width, labels) {
                vm.show(Section.Home)
            }
            NavItem(painterResource(R.drawable.ic_all_comics), "All comics", section == Section.AllComics, width, labels) {
                vm.show(Section.AllComics)
            }
            NavItem(painterResource(R.drawable.ic_will_read), "Will Read", section == Section.WillRead, width, labels) {
                vm.show(Section.WillRead)
            }
            NavItem(painterResource(R.drawable.ic_ungrouped), "Ungrouped", section == Section.Ungrouped, width, labels) {
                vm.show(Section.Ungrouped)
            }
            NavItem(painterResource(R.drawable.ic_gallery), "Gallery", section == Section.Gallery, width, labels) {
                vm.show(Section.Gallery)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 26.dp, end = 12.dp, top = 18.dp, bottom = 2.dp)
                    .alpha(labels),
            ) {
                // The heading opens a page of every group.
                Text(
                    "GROUPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (section == Section.AllGroups) HarbourColors.Accent else HarbourColors.Muted,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = expanded) { vm.show(Section.AllGroups) }
                        .padding(vertical = 10.dp),
                )
                // Folds the list of groups away (remembered).
                IconButton(onClick = vm::toggleGroupsCollapsed, enabled = expanded) {
                    Icon(
                        if (vm.groupsCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (vm.groupsCollapsed) "Show groups" else "Hide groups",
                        tint = HarbourColors.Muted,
                    )
                }
                IconButton(onClick = { newGroup = true }, enabled = expanded) {
                    Icon(Icons.Filled.Add, contentDescription = "New group", tint = HarbourColors.Muted)
                }
            }
            if (vm.groupsCollapsed) {
                Spacer(Modifier.weight(1f))
            } else {
                ReorderableGroups(vm, section, width, labels, Modifier.weight(1f))
            }

            NavItem(painterResource(R.drawable.ic_book), "Books", section == Section.Books || section is Section.InBookGroup, width, labels) {
                vm.show(Section.Books)
            }
            NavItem(rememberVectorPainter(Icons.Filled.Settings), "Settings", section == Section.Settings, width, labels) {
                vm.show(Section.Settings)
            }
        }
    }

    if (newGroup) {
        NameDialog(
            title = "New group",
            confirmLabel = "Create",
            onDismiss = { newGroup = false },
            onConfirm = { name ->
                newGroup = false
                vm.show(Section.InGroup(vm.createGroup(name).id))
            },
        )
    }
}

@Composable
private fun NavItem(
    icon: Painter,
    label: String,
    selected: Boolean,
    panelWidth: Dp,
    labelAlpha: Float,
    onClick: () -> Unit,
) {
    NavRow(selected, panelWidth, onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) HarbourColors.Accent else HarbourColors.Muted,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) HarbourColors.Text else HarbourColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.alpha(labelAlpha),
        )
    }
}

/**
 * The group list. Press and hold a group, then drag it up or down to move it; the new order is
 * saved when you let go.
 */
@Composable
private fun ReorderableGroups(vm: MainViewModel, section: Section, width: Dp, labels: Float, modifier: Modifier) {
    var order by remember { mutableStateOf(vm.orderedGroups) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { 54.dp.toPx() } // 50dp row + 2dp padding above and below

    // Follow changes from elsewhere (new, renamed or deleted groups) except mid-drag.
    LaunchedEffect(vm.orderedGroups) {
        if (dragging == null) order = vm.orderedGroups
    }

    fun finish() {
        if (dragging != null) vm.reorderGroups(order.map { it.id })
        dragging = null
        offset = 0f
    }

    LazyColumn(modifier) {
        items(order, key = { it.id }) { g ->
            val isDragging = g.id == dragging
            GroupNavItem(
                vm = vm,
                group = g,
                selected = section == Section.InGroup(g.id),
                panelWidth = width,
                labelAlpha = labels,
                modifier = Modifier
                    // Same modifiers throughout the drag (so the gesture isn't restarted); only the
                    // dragged row skips the slide animation, since it follows your finger instead.
                    .zIndex(if (isDragging) 1f else 0f)
                    .animateItem(placementSpec = if (isDragging) null else spring<IntOffset>(stiffness = Spring.StiffnessMediumLow))
                    .graphicsLayer {
                        translationY = if (isDragging) offset else 0f
                        shadowElevation = if (isDragging) 12f else 0f
                    }
                    .pointerInput(g.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = g.id
                                offset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                offset += amount.y
                                val i = order.indexOfFirst { it.id == dragging }
                                if (i >= 0 && offset > rowPx / 2 && i < order.lastIndex) {
                                    order = order.toMutableList().apply { add(i + 1, removeAt(i)) }
                                    offset -= rowPx
                                } else if (i > 0 && offset < -rowPx / 2) {
                                    order = order.toMutableList().apply { add(i - 1, removeAt(i)) }
                                    offset += rowPx
                                }
                            },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                        )
                    },
            ) {
                vm.show(Section.InGroup(g.id))
            }
        }
    }
}

@Composable
private fun GroupNavItem(
    vm: MainViewModel,
    group: Group,
    selected: Boolean,
    panelWidth: Dp,
    labelAlpha: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val comics = remember(group, vm.comics) { vm.comicsIn(group) }
    val first = remember(group, vm.comics) { vm.coverOf(group) }
    val count = comics.size
    NavRow(selected, panelWidth, onClick, modifier) {
        if (first != null) {
            CoverImage(first, Modifier.size(width = 24.dp, height = 34.dp), corner = 4.dp)
        } else {
            Icon(
                painterResource(R.drawable.ic_group),
                contentDescription = null,
                tint = HarbourColors.Muted,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.alpha(labelAlpha)) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) HarbourColors.Text else HarbourColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (count == 1) "1 comic" else "$count comics",
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/** One row of the panel. Its highlight shrinks with the panel so it stays a neat pill. */
@Composable
private fun NavRow(
    selected: Boolean,
    panelWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .width(panelWidth - 24.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) HarbourColors.SurfaceHighest else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp),
    ) {
        content()
    }
}
