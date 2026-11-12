package com.harbour.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harbour.reader.Guide
import com.harbour.reader.GuideBlock
import com.harbour.reader.GuideMatch
import com.harbour.reader.Guides
import com.harbour.reader.MainViewModel
import com.harbour.reader.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Guides page: Shane's saved reading guides, each with how far through it he is. */
@Composable
fun GuidesSection(vm: MainViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column {
                Text("Guides", style = MaterialTheme.typography.displaySmall, color = HarbourColors.Text)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Saved guides", selected = true) {}
                }
            }
        }
        if (vm.guides.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Hint("No guides yet", "Guides come with Harbour's updates.")
            }
        }
        items(vm.guides, key = { it.id }) { g -> GuideCard(vm, g) { vm.show(Section.InGuide(g.id)) } }
    }
}

@Composable
private fun GuideCard(vm: MainViewModel, guide: Guide, onClick: () -> Unit) {
    val (done, total) = vm.guideProgress(guide)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HarbourColors.Surface)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Text(guide.title, style = MaterialTheme.typography.titleLarge, color = HarbourColors.Text)
        Spacer(Modifier.height(4.dp))
        Text(
            guide.about,
            style = MaterialTheme.typography.bodyMedium,
            color = HarbourColors.Muted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        CoverProgress(if (total == 0) 0f else done.toFloat() / total)
        Spacer(Modifier.height(4.dp))
        Text("$done of $total steps read", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
    }
}

/** One guide, word for word: headings, notes and numbered steps to tick off as you read them. */
@Composable
fun GuideSection(vm: MainViewModel, guide: Guide) {
    val index = vm.guideIndex
    // Which of each step's issues are in the library (worked out in the background).
    val matches by produceState(emptyMap<Int, GuideMatch>(), guide, index) {
        value = withContext(Dispatchers.Default) { guide.entries.associateWith { Guides.match(guide.blocks[it], index) } }
    }
    var open by remember(guide.id) { mutableStateOf<Int?>(null) }
    val upNext = guide.entries.firstOrNull { !vm.isGuideStepDone(guide, it) }
    val (done, total) = vm.guideProgress(guide)

    LazyColumn(
        contentPadding = sectionPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Column(Modifier.widthIn(max = 860.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::back, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HarbourColors.Text)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(guide.title, style = MaterialTheme.typography.headlineSmall, color = HarbourColors.Text)
                        Text("$done of $total steps read", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
                    }
                }
                if (guide.about.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(guide.about, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap the circle to tick a step off. Tap a step to see which of its issues you have; hold it for more.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HarbourColors.Muted,
                )
            }
        }
        items(guide.blocks.indices.toList(), key = { it }) { i ->
            val b = guide.blocks[i]
            Box(Modifier.widthIn(max = 860.dp)) {
                when {
                    b.isHeading -> Text(
                        b.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = HarbourColors.Accent,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                    b.isEntry -> GuideStep(
                        vm = vm,
                        guide = guide,
                        index = i,
                        block = b,
                        done = vm.isGuideStepDone(guide, i),
                        next = i == upNext,
                        match = matches[i],
                        expanded = open == i,
                        onExpand = { open = if (open == i) null else i },
                    )
                    else -> Text(
                        b.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarbourColors.Muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HarbourColors.Surface.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideStep(
    vm: MainViewModel,
    guide: Guide,
    index: Int,
    block: GuideBlock,
    done: Boolean,
    next: Boolean,
    match: GuideMatch?,
    expanded: Boolean,
    onExpand: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HarbourColors.Surface)
                .then(if (next) Modifier.border(2.dp, HarbourColors.Accent, RoundedCornerShape(14.dp)) else Modifier)
                .combinedClickable(onClick = onExpand, onLongClick = { menu = true })
                .padding(16.dp),
        ) {
            // The tick: its own target, so ticking never opens the step.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (done) HarbourColors.Accent else Color.Transparent)
                    .border(2.dp, if (done) HarbourColors.Accent else HarbourColors.Muted, CircleShape)
                    .clickable { vm.setGuideStepDone(guide, index, !done) },
            ) {
                if (done) Icon(Icons.Filled.Check, contentDescription = "Read", tint = HarbourColors.OnAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${block.n}.",
                        style = MaterialTheme.typography.labelLarge,
                        color = HarbourColors.Muted,
                        modifier = Modifier.width(34.dp),
                    )
                    Text(
                        block.read,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (done) HarbourColors.Muted else HarbourColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (block.label.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        StepLabel(block.label)
                    }
                }
                if (block.why.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        block.why,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarbourColors.Muted,
                        modifier = Modifier.padding(start = 34.dp),
                    )
                }
                if (block.extra.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Collected in ${block.extra}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HarbourColors.Muted,
                        modifier = Modifier.padding(start = 34.dp),
                    )
                }
                val line = when {
                    match == null || match.wanted == 0 -> null
                    match.found == 0 -> "Not in your library"
                    match.found >= match.wanted -> if (match.wanted == 1) "In your library" else "All ${match.wanted} in your library"
                    else -> "${match.found} of ${match.wanted} in your library"
                }
                if (next || line != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.padding(start = 34.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (next) Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = HarbourColors.Accent)
                        if (line != null) {
                            Text(
                                line,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (match!!.found > 0) HarbourColors.Text else HarbourColors.Muted,
                            )
                        }
                    }
                }
                if (expanded && match != null && match.comics.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 34.dp)) {
                        items(match.comics, key = { it.key }) { c ->
                            Column(
                                Modifier
                                    .width(84.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { vm.openComic(c) },
                            ) {
                                CoverImage(c, Modifier.fillMaxWidth().aspectRatio(2f / 3f), corner = 8.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    c.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HarbourColors.Muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else if (expanded && match != null && match.wanted > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "None of these issues turned up in your library.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HarbourColors.Muted,
                        modifier = Modifier.padding(start = 34.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (done) "Mark as not read" else "Mark as read") },
                onClick = {
                    menu = false
                    vm.setGuideStepDone(guide, index, !done)
                },
            )
            DropdownMenuItem(
                text = { Text("Mark everything before this as read") },
                onClick = {
                    menu = false
                    vm.markGuideReadBefore(guide, index)
                },
            )
        }
    }
}

/** CORE, IMPORTANT, OPTIONAL… as a small tag; star ratings shown as they are. */
@Composable
private fun StepLabel(label: String) {
    val stars = label.all { it == '⭐' || it.isSurrogate() || it.isWhitespace() }
    if (stars) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        return
    }
    val core = label.startsWith("CORE") || label.startsWith("YOU ARE")
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (core) HarbourColors.OnAccent else HarbourColors.Text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (core) HarbourColors.Accent else HarbourColors.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
