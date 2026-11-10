package com.folio.reader.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.folio.reader.Comic
import com.folio.reader.ComicArchive
import com.folio.reader.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Pages are decoded to at most this many pixels (~24 MB each) so a few can sit in memory at once. */
private const val PAGE_PIXELS = 6_000_000L

private enum class TapZone { Left, Center, Right }

@Composable
fun ReaderScreen(vm: MainViewModel, comic: Comic) {
    val context = LocalContext.current
    var archive by remember(comic.key) { mutableStateOf<ComicArchive?>(null) }
    var error by remember(comic.key) { mutableStateOf<String?>(null) }

    LaunchedEffect(comic.key) {
        var opened: ComicArchive? = null
        try {
            opened = withContext(Dispatchers.IO) { ComicArchive.open(context, Uri.parse(comic.uri)) }
            if (opened.pageCount == 0) {
                error = "No pages found in this file."
            } else {
                archive = opened
            }
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't open this comic. ${e.message ?: ""}".trim()
        } finally {
            archive = null
            opened?.close()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val a = archive
        when {
            error != null -> ReaderError(error ?: "", onBack = vm::back)
            a == null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                color = FolioColors.Accent,
                strokeWidth = 2.dp,
            )
            else -> ReaderContent(vm, comic, a)
        }
    }
}

@Composable
private fun ReaderContent(vm: MainViewModel, comic: Comic, archive: ComicArchive) {
    val count = archive.pageCount
    val pager = rememberPagerState(initialPage = vm.startPage(comic, count)) { count }
    val scope = rememberCoroutineScope()
    var rtl by remember(comic.seriesPath) { mutableStateOf(vm.isRtl(comic.seriesPath)) }
    var showUi by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    val next = remember(comic.key) { vm.nextComic(comic) }

    LaunchedEffect(pager.settledPage) { vm.saveProgress(comic, pager.settledPage, count) }
    LaunchedEffect(Unit) {
        delay(2200)
        showUi = false
    }

    fun turn(forward: Boolean) {
        val target = pager.currentPage + if (forward) 1 else -1
        when {
            target in 0 until count -> scope.launch { pager.animateScrollToPage(target) }
            forward -> showUi = true // at the end: show controls (and the next-issue button)
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = rtl,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { it },
        ) { index ->
            ZoomablePage(
                archive = archive,
                index = index,
                isCurrent = index == pager.currentPage,
                onZoomChange = { zoomed = it },
                onTap = { zone ->
                    when (zone) {
                        TapZone.Left -> turn(forward = rtl)
                        TapZone.Right -> turn(forward = !rtl)
                        TapZone.Center -> showUi = !showUi
                    }
                },
            )
        }

        // Top bar
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
            ) {
                IconButton(onClick = vm::back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        comic.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        comic.seriesName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = {
                    rtl = !rtl
                    vm.setRtl(comic.seriesPath, rtl)
                }) {
                    Text(
                        if (rtl) "Right to left" else "Left to right",
                        color = FolioColors.Accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // Bottom bar
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var sliderValue by remember { mutableStateOf<Float?>(null) }
            val shown = sliderValue ?: pager.currentPage.toFloat()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 16.dp),
            ) {
                if (next != null && pager.currentPage == count - 1) {
                    Button(
                        onClick = { vm.switchToComic(next) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FolioColors.Accent,
                            contentColor = FolioColors.OnAccent,
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                        modifier = Modifier.widthIn(max = 420.dp),
                    ) {
                        Text("Next: ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.widthIn(max = 760.dp),
                ) {
                    Text(
                        "${shown.roundToInt() + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp),
                    )
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        Slider(
                            value = shown,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                sliderValue?.let { v -> scope.launch { pager.scrollToPage(v.roundToInt()) } }
                                sliderValue = null
                            },
                            valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                            enabled = count > 1,
                            colors = SliderDefaults.colors(
                                thumbColor = FolioColors.Accent,
                                activeTrackColor = FolioColors.Accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "$count",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    archive: ComicArchive,
    index: Int,
    isCurrent: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onTap: (TapZone) -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, archive, index) {
        value = withContext(Dispatchers.IO) {
            runCatching { archive.decodePage(index, PAGE_PIXELS)?.asImageBitmap() }.getOrNull()
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
        // Keep the tapped point under the finger as we zoom in.
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
                var lastTapPos = Offset.Zero

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
                            if (!moved && (primary.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                        }

                        // Pinch always zooms; one-finger drags only pan when zoomed in,
                        // otherwise they fall through to the pager for page swipes.
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

                    val isTap = !moved && !multiTouch &&
                        upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                    if (!isTap) return@awaitEachGesture

                    val p = down.position
                    val zone = when {
                        p.x < boxSize.width * 0.3f -> TapZone.Left
                        p.x > boxSize.width * 0.7f -> TapZone.Right
                        else -> TapZone.Center
                    }
                    val isDoubleTap = pendingTap?.isActive == true &&
                        down.uptimeMillis - lastTapTime < viewConfiguration.doubleTapTimeoutMillis &&
                        (p - lastTapPos).getDistance() < viewConfiguration.touchSlop * 4

                    when {
                        isDoubleTap -> {
                            pendingTap?.cancel()
                            pendingTap = null
                            animateZoomTo(if (scale > 1.01f) 1f else 2.5f, p)
                        }
                        // Edge taps turn the page instantly when not zoomed.
                        zone != TapZone.Center && scale <= 1.01f -> {
                            pendingTap?.cancel()
                            currentOnTap(zone)
                        }
                        // Centre taps wait briefly in case a second tap (zoom) follows.
                        else -> {
                            lastTapTime = down.uptimeMillis
                            lastTapPos = p
                            pendingTap = scope.launch {
                                delay(viewConfiguration.doubleTapTimeoutMillis)
                                currentOnTap(TapZone.Center)
                            }
                        }
                    }
                }
            },
    ) {
        val bmp = bitmap
        if (bmp == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(26.dp),
                color = Color.White.copy(alpha = 0.35f),
                strokeWidth = 2.dp,
            )
        } else {
            Image(
                bitmap = bmp,
                contentDescription = "Page ${index + 1}",
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

@Composable
private fun ReaderError(message: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onBack) {
            Text("Back to library", color = FolioColors.Accent)
        }
    }
}
