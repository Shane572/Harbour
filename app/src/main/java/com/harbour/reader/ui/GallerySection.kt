package com.harbour.reader.ui

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.harbour.reader.Comic
import com.harbour.reader.GalleryStore
import com.harbour.reader.MainViewModel
import com.harbour.reader.NaturalOrder
import com.harbour.reader.SavedPage
import com.harbour.reader.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Small copies of saved pages for the gallery grid, kept while Harbour is open. */
private object GalleryThumbs {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(file: File): ImageBitmap? = cache.get(file.path)

    fun load(file: File, maxWidth: Int): ImageBitmap? {
        cache.get(file.path)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return bmp.asImageBitmap().also { cache.put(file.path, it) }
    }
}

/** Saved pages as a wall of art, newest first, with a filter by group. Tap one to see it full screen. */
@Composable
fun GallerySection(vm: MainViewModel) {
    val all = vm.gallery
    val groupOfPage = remember(all, vm.groups) {
        all.associate { p -> p.file to (vm.groupOfKey[p.comicKey]?.name ?: p.group) }
    }
    val groupNames = remember(groupOfPage) { groupOfPage.values.filterNotNull().distinct().sortedWith(NaturalOrder) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(all, filter, groupOfPage) { if (filter == null) all else all.filter { groupOfPage[it.file] == filter } }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(vm.coverSize.minCell() * 1.4f),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 18.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = StaggeredGridItemSpan.FullLine, key = "header") {
            Column {
                Text("Gallery", style = MaterialTheme.typography.displaySmall, color = HarbourColors.Text)
                Spacer(Modifier.height(4.dp))
                val comics = all.map { it.comicKey }.distinct().size
                Text(
                    if (all.isEmpty()) "Pages you save while reading" else "${all.size} pages from $comics comics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarbourColors.Muted,
                )
                if (groupNames.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Choice("All", filter == null) { filter = null }
                        groupNames.forEach { name -> Choice(name, filter == name) { filter = name } }
                    }
                }
            }
        }
        if (all.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                Hint(
                    "No saved pages yet",
                    "While reading, tap the heart at the top to save the page you're on. Saved pages are kept in " +
                        "Harbour/Gallery on the tablet.",
                )
            }
        }
        items(shown, key = { it.file }) { p ->
            GalleryTile(p, groupOfPage[p.file]) { vm.openGallery(shown, shown.indexOf(p)) }
        }
    }
}

@Composable
private fun GalleryTile(page: SavedPage, group: String?, onClick: () -> Unit) {
    val file = remember(page.file) { GalleryStore.imageOf(page) }
    val bmp by produceState(GalleryThumbs.peek(file), file) {
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { GalleryThumbs.load(file, 600) }.getOrNull() }
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        val b = bmp
        if (b == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.66f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HarbourColors.SurfaceHigh),
            )
        } else {
            Image(
                bitmap = b,
                contentDescription = "${page.title}, page ${page.page + 1}",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(b.width.toFloat() / b.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(page.title, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull("Page ${page.page + 1}", group).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = HarbourColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Saved pages full screen: swipe between them, pinch or double-tap to zoom, tap for the controls. */
@Composable
fun GalleryViewer(vm: MainViewModel, screen: Screen.GalleryView) {
    val pages = remember(vm.gallery, screen.files) {
        val byFile = vm.gallery.associateBy { it.file }
        screen.files.mapNotNull { byFile[it] }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (pages.isEmpty()) {
            LaunchedEffect(Unit) { vm.back() }
        } else {
            GalleryPages(vm, pages, screen.start)
        }
    }
}

@Composable
private fun GalleryPages(vm: MainViewModel, pages: List<SavedPage>, start: Int) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, pages.lastIndex)) { pages.size }
    var showUi by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf<Comic?>(null) }
    var removing by remember { mutableStateOf<SavedPage?>(null) }
    var wallpaperMenu by remember { mutableStateOf(false) }
    val current = pages[pager.currentPage.coerceIn(0, pages.lastIndex)]
    val comic = vm.comicsByKey[current.comicKey]

    LaunchedEffect(Unit) {
        delay(2200)
        showUi = false
    }

    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        key = { pages[it].file },
    ) { i ->
        ZoomableImage(
            file = GalleryStore.imageOf(pages[i]),
            isCurrent = i == pager.currentPage,
            onZoomChange = { zoomed = it },
            onTap = { showUi = !showUi },
        )
    }

    AnimatedVisibility(visible = showUi, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        ) {
            IconButton(onClick = vm::back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(current.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (current.pages > 0) "Page ${current.page + 1} of ${current.pages}" else "Page ${current.page + 1}",
                        vm.groupOfKey[current.comicKey]?.name ?: current.group,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${pager.currentPage + 1} / ${pages.size}", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = showUi, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(top = 36.dp, bottom = 12.dp),
            ) {
                ViewerAction("Read from here", enabled = comic != null) { vm.readFrom(current) }
                ViewerAction("About this issue", enabled = comic != null) { about = comic }
                Box {
                    ViewerAction("Set as wallpaper") { wallpaperMenu = true }
                    DropdownMenu(expanded = wallpaperMenu, onDismissRequest = { wallpaperMenu = false }) {
                        listOf(
                            "Home screen" to WallpaperManager.FLAG_SYSTEM,
                            "Lock screen" to WallpaperManager.FLAG_LOCK,
                            "Both" to (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
                        ).forEach { (label, which) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                wallpaperMenu = false
                                vm.setWallpaper(current, which)
                            })
                        }
                    }
                }
                ViewerAction("Remove") { removing = current }
            }
        }
    }

    about?.let { c ->
        ComicInfoDialog(
            vm,
            c,
            onDismiss = { about = null },
            onRead = {
                about = null
                vm.readFrom(current)
            },
        )
    }
    removing?.let { p ->
        ConfirmDialog(
            title = "Remove from gallery?",
            text = "The saved picture of page ${p.page + 1} is deleted. The comic itself isn't touched.",
            confirmLabel = "Remove",
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                vm.removeSaved(p)
            },
        )
    }
}

@Composable
private fun ViewerAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
    }
}

/** One saved picture, fit to the screen, with pinch and double-tap zoom. */
@Composable
private fun ZoomableImage(file: File, isCurrent: Boolean, onZoomChange: (Boolean) -> Unit, onTap: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 12_000_000L) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    val isZoomed = scale > 1.01f

    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(isZoomed, isCurrent) {
        if (isCurrent) onZoomChange(isZoomed)
    }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = boxSize.width * (s - 1f) / 2f
        val maxY = boxSize.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun animateZoomTo(target: Float, focus: Offset) {
        val startScale = scale
        val startOffset = offset
        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
        val targetOffset = if (target <= 1f) Offset.Zero else clamp((center - focus) * (target - 1f), target)
        scope.launch {
            animate(0f, 1f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { v, _ ->
                scale = startScale + (target - startScale) * v
                offset = startOffset + (targetOffset - startOffset) * v
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                var pendingTap: Job? = null
                var lastTapTime = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    var multiTouch = false
                    var upTime = down.uptimeMillis
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed > 1) multiTouch = true
                        val primary = event.changes.firstOrNull { it.id == down.id }
                        if (primary != null) {
                            upTime = primary.uptimeMillis
                            if (!moved && (primary.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        }
                        // Pinch always zooms; one finger only pans when zoomed, otherwise it swipes pages.
                        if (pressed > 1 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid.isSpecified) {
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                val c = centroid - Offset(boxSize.width / 2f, boxSize.height / 2f)
                                offset = clamp(c - (c - offset) * (newScale / scale) + pan, newScale)
                                scale = newScale
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    val isTap = !moved && !multiTouch && upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                    if (!isTap) return@awaitEachGesture
                    if (pendingTap?.isActive == true && down.uptimeMillis - lastTapTime < viewConfiguration.doubleTapTimeoutMillis) {
                        pendingTap?.cancel()
                        pendingTap = null
                        animateZoomTo(if (scale > 1.01f) 1f else 2.5f, down.position)
                    } else {
                        lastTapTime = down.uptimeMillis
                        pendingTap = scope.launch {
                            delay(viewConfiguration.doubleTapTimeoutMillis)
                            currentOnTap()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null) {
            CircularProgressIndicator(Modifier.size(26.dp), color = Color.White.copy(alpha = 0.35f), strokeWidth = 2.dp)
        } else {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}
