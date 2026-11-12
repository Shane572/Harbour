package com.harbour.reader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.harbour.reader.ComicVine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harbour.reader.Guide
import com.harbour.reader.GuideBlock
import com.harbour.reader.MainViewModel
import com.harbour.reader.R
import com.harbour.reader.Section
import com.harbour.reader.StepState

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

private fun progressText(read: Int, skipped: Int, total: Int): String =
    "$read of $total read" + if (skipped > 0) " · $skipped skipped" else ""

@Composable
private fun GuideCard(vm: MainViewModel, guide: Guide, onClick: () -> Unit) {
    val (read, skipped, total) = vm.guideProgress(guide)
    // Skipped steps count as dealt with, so a guide you've finished (minus the bits you left) is full.
    val counted = total - skipped
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
        CoverProgress(if (counted <= 0) 0f else read.toFloat() / counted)
        Spacer(Modifier.height(4.dp))
        Text(progressText(read, skipped, total), style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
    }
}

/** One guide, word for word: headings, notes and numbered steps to tick off or skip. */
@Composable
fun GuideSection(vm: MainViewModel, guide: Guide) {
    val upNext = guide.entries.firstOrNull { vm.guideStep(guide, it) == StepState.None }
    val (read, skipped, total) = vm.guideProgress(guide)

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
                        Text(progressText(read, skipped, total), style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
                    }
                }
                if (guide.about.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(guide.about, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tick the circle when you've read a step, or tap the skip icon to leave it out (the one by a " +
                        "heading leaves out that whole part). Tap a step or heading to see its cover; hold a step for more.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HarbourColors.Muted,
                )
            }
        }
        items(guide.blocks.indices.toList(), key = { it }) { i ->
            val b = guide.blocks[i]
            Box(Modifier.widthIn(max = 860.dp)) {
                when {
                    b.isHeading -> SectionHeading(vm, guide, i, b.text)
                    b.isEntry -> GuideStep(
                        vm = vm,
                        guide = guide,
                        index = i,
                        block = b,
                        state = vm.guideStep(guide, i),
                        next = i == upNext,
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

/** A part of the guide, with a skip button that leaves out (or brings back) the whole part. */
@Composable
private fun SectionHeading(vm: MainViewModel, guide: Guide, index: Int, text: String) {
    val steps = guide.sectionOf(index)
    val open = steps.filter { vm.guideStep(guide, it) != StepState.Read }
    val skipped = open.isNotEmpty() && open.all { vm.guideStep(guide, it) == StepState.Skipped }
    var showCover by remember { mutableStateOf(false) }
    if (showCover && steps.isNotEmpty()) CoverDialog(vm, guide, steps.first(), onDismiss = { showCover = false })
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (skipped) HarbourColors.Muted else HarbourColors.Accent,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = steps.isNotEmpty()) { showCover = true }
                .padding(vertical = 6.dp),
        )
        if (skipped) {
            Text("SKIPPED", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
            Spacer(Modifier.width(4.dp))
        }
        if (steps.isNotEmpty()) {
            IconButton(onClick = { vm.toggleSkipSection(guide, index) }) {
                Icon(
                    painterResource(R.drawable.ic_skip),
                    contentDescription = if (skipped) "Bring this part back" else "Skip this part",
                    tint = if (skipped) HarbourColors.Accent else HarbourColors.Muted,
                )
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
    state: StepState,
    next: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    var showCover by remember { mutableStateOf(false) }
    if (showCover) CoverDialog(vm, guide, index, onDismiss = { showCover = false })
    val read = state == StepState.Read
    val skipped = state == StepState.Skipped
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HarbourColors.Surface)
                .then(if (next) Modifier.border(2.dp, HarbourColors.Accent, RoundedCornerShape(14.dp)) else Modifier)
                .combinedClickable(onClick = { showCover = true }, onLongClick = { menu = true })
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
        ) {
            // The tick: its own target, so ticking never opens the menu.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (read) HarbourColors.Accent else Color.Transparent)
                    .border(2.dp, if (read) HarbourColors.Accent else HarbourColors.Muted, CircleShape)
                    .clickable { vm.setGuideStep(guide, index, if (read) StepState.None else StepState.Read) },
            ) {
                if (read) Icon(Icons.Filled.Check, contentDescription = "Read", tint = HarbourColors.OnAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .alpha(if (skipped) 0.45f else 1f),
            ) {
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
                        color = if (read) HarbourColors.Muted else HarbourColors.Text,
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
                if (next || skipped) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (next) "UP NEXT" else "SKIPPED",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (next) HarbourColors.Accent else HarbourColors.Muted,
                        modifier = Modifier.padding(start = 34.dp),
                    )
                }
            }
            // Skip: leave this step out (tap again to bring it back).
            IconButton(onClick = { vm.setGuideStep(guide, index, if (skipped) StepState.None else StepState.Skipped) }) {
                Icon(
                    painterResource(R.drawable.ic_skip),
                    contentDescription = if (skipped) "Don't skip" else "Skip",
                    tint = if (skipped) HarbourColors.Accent else HarbourColors.Muted.copy(alpha = 0.7f),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (read) "Mark as not read" else "Mark as read") },
                onClick = {
                    menu = false
                    vm.setGuideStep(guide, index, if (read) StepState.None else StepState.Read)
                },
            )
            DropdownMenuItem(
                text = { Text(if (skipped) "Don't skip" else "Skip") },
                onClick = {
                    menu = false
                    vm.setGuideStep(guide, index, if (skipped) StepState.None else StepState.Skipped)
                },
            )
            DropdownMenuItem(
                text = { Text("Mark everything before this as read") },
                onClick = {
                    menu = false
                    vm.markGuideReadBefore(guide, index)
                },
            )
            DropdownMenuItem(
                text = { Text("Skip or bring back this part") },
                onClick = {
                    menu = false
                    vm.toggleSkipSection(guide, index)
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

/**
 * A step's cover: the first issue it names, from Comic Vine (looked up once, then saved). Also lets
 * you tick the step off or skip it from here.
 */
@Composable
private fun CoverDialog(vm: MainViewModel, guide: Guide, index: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val block = guide.blocks[index]
    var cover by remember(index) { mutableStateOf(ComicVine.cached(context, block)) }
    var error by remember(index) { mutableStateOf<String?>(null) }
    LaunchedEffect(index, vm.comicVineKey) {
        if (cover == null) {
            error = null
            vm.stepCover(block)
                .onSuccess { cover = it }
                .onFailure { e ->
                    error = (e as? ComicVine.LookupException)?.message
                        ?: "Couldn't reach Comic Vine. Check the tablet's internet connection."
                }
        }
    }
    val picture by produceState<ImageBitmap?>(null, cover) {
        value = cover?.let { c ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(c.file.path, bounds)
                    var sample = 1
                    while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 2_500_000L) sample *= 2
                    BitmapFactory.decodeFile(c.file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val state = vm.guideStep(guide, index)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(18.dp))
                .background(HarbourColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
        ) {
            Text(
                "${block.n}. ${block.read}",
                style = MaterialTheme.typography.titleMedium,
                color = HarbourColors.Text,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HarbourColors.SurfaceHigh),
            ) {
                val p = picture
                val e = error
                when {
                    p != null -> Image(
                        bitmap = p,
                        contentDescription = cover?.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    e != null -> Text(
                        e,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarbourColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp),
                    )
                    else -> CircularProgressIndicator(Modifier.size(28.dp), color = HarbourColors.Accent, strokeWidth = 2.dp)
                }
            }
            cover?.let {
                Spacer(Modifier.height(8.dp))
                Text("${it.caption} · from Comic Vine", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    vm.setGuideStep(guide, index, if (state == StepState.Read) StepState.None else StepState.Read)
                }) {
                    Text(if (state == StepState.Read) "Mark as not read" else "Mark as read", color = HarbourColors.Accent)
                }
                TextButton(onClick = {
                    vm.setGuideStep(guide, index, if (state == StepState.Skipped) StepState.None else StepState.Skipped)
                }) {
                    Text(if (state == StepState.Skipped) "Don't skip" else "Skip", color = HarbourColors.Accent)
                }
                TextButton(onClick = onDismiss) { Text("Close", color = HarbourColors.Accent) }
            }
        }
    }
}
