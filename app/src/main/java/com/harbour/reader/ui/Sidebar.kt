package com.harbour.reader.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
            NavItem(painterResource(R.drawable.ic_ungrouped), "Ungrouped", section == Section.Ungrouped, width, labels) {
                vm.show(Section.Ungrouped)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 26.dp, end = 12.dp, top = 18.dp, bottom = 2.dp)
                    .alpha(labels),
            ) {
                Text(
                    "GROUPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = HarbourColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { newGroup = true }, enabled = expanded) {
                    Icon(Icons.Filled.Add, contentDescription = "New group", tint = HarbourColors.Muted)
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(vm.sortedGroups, key = { it.id }) { g ->
                    GroupNavItem(vm, g, section == Section.InGroup(g.id), width, labels) {
                        vm.show(Section.InGroup(g.id))
                    }
                }
            }

            NavItem(painterResource(R.drawable.ic_book), "Books", section == Section.Books, width, labels) {
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

@Composable
private fun GroupNavItem(
    vm: MainViewModel,
    group: Group,
    selected: Boolean,
    panelWidth: Dp,
    labelAlpha: Float,
    onClick: () -> Unit,
) {
    val comics = remember(group, vm.comics) { vm.comicsIn(group) }
    val first = comics.firstOrNull()
    val count = comics.size
    NavRow(selected, panelWidth, onClick) {
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
private fun NavRow(selected: Boolean, panelWidth: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
