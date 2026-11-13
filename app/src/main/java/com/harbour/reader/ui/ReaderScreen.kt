package com.harbour.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.gestures.scrollBy
import kotlin.math.abs
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.res.painterResource
import com.harbour.reader.R
import com.harbour.reader.ShownPages
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.harbour.reader.Book
import com.harbour.reader.Comic
import com.harbour.reader.ComicArchive
import com.harbour.reader.MainViewModel
import com.harbour.reader.PageLayout
import com.harbour.reader.ReadingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Pages are decoded at full size up to this many pixels (~48 MB each, three in memory at once).
 * Standard digital pages (1988x3056, about 6 MP) stay sharp; only huge scans get halved.
 * Two-page spreads use half this per page, since each page gets half the screen.
 */
private const val PAGE_PIXELS = 12_000_000L

private enum class TapZone { Left, Center, Right }

/** Reads a comic, or a PDF [book] (passed as [comic] via Book.asComic, with progress kept as a book). */
@Composable
fun ReaderScreen(vm: MainViewModel, comic: Comic, book: Book? = null) {
    val context = LocalContext.current
    var archive by remember(comic.key) { mutableStateOf<ComicArchive?>(null) }
    var error by remember(comic.key) { mutableStateOf<String?>(null) }

    LaunchedEffect(comic.key) {
        var opened: ComicArchive? = null
        try {
            opened = withContext(Dispatchers.IO) { ComicArchive.open(comic.file, context.cacheDir) }
            if (opened.pageCount == 0) {
                error = "No pages found in this file."
            } else {
                archive = opened
            }
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't open this file. ${e.message ?: ""}".trim()
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
            a == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(32.dp), color = HarbourColors.Accent, strokeWidth = 2.dp)
                if (comic.name.endsWith(".cbr", ignoreCase = true)) {
                    Spacer(Modifier.height(14.dp))
                    Text("Unpacking…", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
                }
            }
            else -> ReaderContent(vm, comic, a, book)
        }
    }
}

/**
 * Groups pages into what's shown at once: one page, or (two-page view) the cover alone then pairs.
 * A page that's already a double spread ([alone]) is shown by itself rather than squeezed beside
 * another, and pairing carries on after it, so the pages either side stay facing correctly.
 */
private fun buildSpreads(count: Int, double: Boolean, alone: (Int) -> Boolean): List<IntArray> =
    if (!double) {
        List(count) { intArrayOf(it) }
    } else {
        buildList {
            add(intArrayOf(0))
            var i = 1
            while (i < count) {
                if (alone(i) || i + 1 >= count || alone(i + 1)) {
                    add(intArrayOf(i))
                    i += 1
                } else {
                    add(intArrayOf(i, i + 1))
                    i += 2
                }
            }
        }
    }

@Composable
private fun ReaderContent(vm: MainViewModel, comic: Comic, archive: ComicArchive, book: Book?) {
    val sourceCount = archive.pageCount
    var mode by remember(comic.key) { mutableStateOf(vm.readingMode(comic)) }
    // Where you are in the file itself. Shown pages (margins trimmed, spreads split in two) are
    // worked out from it, so progress and saved pages never depend on those toggles.
    var sourcePage by remember(comic.key) {
        mutableIntStateOf(
            vm.takePendingStart(comic)?.coerceIn(0, sourceCount - 1)
                ?: if (book != null) vm.pdfStartPage(book, sourceCount) else vm.startPage(comic, sourceCount),
        )
    }
    val rtl = mode == ReadingMode.RightToLeft
    val trim = vm.trimMargins
    val split = vm.splitSpreads
    val shown by produceState<ShownPages?>(null, archive, trim, split, rtl) {
        value = withContext(Dispatchers.IO) {
            val wide = if (split) runCatching { vm.spreadsOf(comic, archive) }.getOrNull() else null
            ShownPages.build(archive, trim, wide, rtl)
        }
    }
    val pages = shown
    if (pages == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(32.dp), color = HarbourColors.Accent, strokeWidth = 2.dp)
        }
    } else {
        ReaderPages(
            vm = vm,
            comic = comic,
            archive = archive,
            pages = pages,
            book = book,
            mode = mode,
            onMode = {
                mode = it
                vm.setReadingMode(comic, it)
            },
            sourcePage = sourcePage,
            onSourcePage = { sourcePage = it },
        )
    }
}

@Composable
private fun ReaderPages(
    vm: MainViewModel,
    comic: Comic,
    archive: ComicArchive,
    pages: ShownPages,
    book: Book?,
    mode: ReadingMode,
    onMode: (ReadingMode) -> Unit,
    sourcePage: Int,
    onSourcePage: (Int) -> Unit,
) {
    val count = pages.pageCount
    val sourceCount = archive.pageCount
    val scope = rememberCoroutineScope()
    var page by remember(pages) { mutableIntStateOf(pages.indexOf(sourcePage)) }
    var showUi by remember { mutableStateOf(true) }
    var showOptions by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val next = remember(comic.key) { if (book != null) null else vm.nextComic(comic) }

    LaunchedEffect(Unit) {
        delay(2200)
        if (!showOptions) showUi = false
    }
    BackHandler(enabled = showOptions) { showOptions = false }

    // A finished comic stays at 100% while you flick back through it, until you reset it.
    var keepFinished by remember(comic.key) {
        mutableStateOf(if (book != null) vm.bookProgress[book.key]?.finished == true else vm.progress[comic.key]?.finished == true)
    }

    fun onPageShown(p: Int) {
        page = p
        val source = pages.sourceOf(p)
        onSourcePage(source)
        if (keepFinished) return
        if (book != null) vm.savePdfProgress(book, source, sourceCount) else vm.saveProgress(comic, source, sourceCount)
    }

    fun resetProgress() {
        if (book != null) vm.setBookRead(listOf(book.key), false) else vm.setRead(listOf(comic.key), false)
        keepFinished = false
        onPageShown(page)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val double = !mode.continuous &&
            (vm.pageLayout == PageLayout.Double || (vm.pageLayout == PageLayout.Auto && landscape))
        // Which pages are already double spreads: read from every page's size once per comic, in the
        // background, and only when two pages are shown at once.
        val wide by produceState<BooleanArray?>(null, archive, double) {
            if (double) value = withContext(Dispatchers.IO) { runCatching { vm.spreadsOf(comic, archive) }.getOrNull() }
        }
        val spreads = remember(count, double, wide, pages) { buildSpreads(count, double) { pages.isWholeSpread(it, wide) } }
        fun spreadOf(p: Int): Int = spreads.indexOfFirst { p in it }.coerceAtLeast(0)

        // Recreated when the layout changes, starting from the page you were on.
        val pager: PagerState = key(spreads) { rememberPagerState(initialPage = spreadOf(page)) { spreads.size } }
        val list: LazyListState = key(mode, pages) { rememberLazyListState(initialFirstVisibleItemIndex = page) }
        val atEnd = if (mode.continuous) {
            !list.canScrollForward && list.layoutInfo.totalItemsCount > 0
        } else {
            pager.currentPage == spreads.lastIndex
        }

        if (mode.continuous) {
            LaunchedEffect(list, count) {
                snapshotFlow { if (!list.canScrollForward) count - 1 else list.firstVisibleItemIndex.coerceAtMost(count - 1) }
                    .collect { onPageShown(it) }
            }
            val vertical = mode == ReadingMode.Vertical
            // A screenful (less a little overlap) along the strip, for the side taps.
            val step = (if (vertical) constraints.maxHeight else constraints.maxWidth) * 0.8f
            LaunchedEffect(list, step) { vm.pageTurns.collect { list.animateScrollBy(it * step) } }
            key(mode, pages) {
                ContinuousReader(
                    archive = pages,
                    state = list,
                    vertical = vertical,
                    next = next,
                    onNext = { n -> vm.switchToComic(n) },
                    onTap = { zone ->
                        when (zone) {
                            TapZone.Center -> showUi = !showUi
                            TapZone.Left -> scope.launch { list.animateScrollBy(-step) }
                            TapZone.Right -> scope.launch { list.animateScrollBy(step) }
                        }
                    },
                )
            }
        } else {
            val rtl = mode == ReadingMode.RightToLeft
            LaunchedEffect(pager, spreads) {
                snapshotFlow { pager.settledPage }.collect { i ->
                    val s = spreads[i.coerceIn(0, spreads.lastIndex)]
                    onPageShown(if (s.last() == count - 1) count - 1 else s.first())
                }
            }

            fun turn(forward: Boolean) {
                val target = pager.currentPage + if (forward) 1 else -1
                when {
                    target in spreads.indices -> scope.launch { pager.animateScrollToPage(target) }
                    forward -> showUi = true // at the end: show controls (and the next-issue button)
                }
            }

            // Volume buttons, if turned on: down for the next page, up for the one before.
            LaunchedEffect(pager, spreads) { vm.pageTurns.collect { turn(forward = it > 0) } }

            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = rtl,
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
                key = { it },
            ) { index ->
                ZoomableSpread(
                    archive = pages,
                    pages = spreads[index],
                    rtl = rtl,
                    fitWidth = vm.fitWidth && spreads[index].size == 1,
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
        }

        TopBar(
            visible = showUi,
            comic = comic,
            subtitle = if (book != null) book.author else vm.subtitleFor(comic),
            onBack = vm::back,
            onOptions = { showOptions = !showOptions },
            rotationLocked = vm.rotationLocked,
            onToggleRotation = vm::toggleRotationLock,
        ) {
            if (book == null) {
                // The file's pages on screen now: one, or two side by side.
                val visible = if (mode.continuous) {
                    listOf(pages.sourceOf(page))
                } else {
                    spreads[pager.currentPage.coerceIn(0, spreads.lastIndex)].map { pages.sourceOf(it) }.distinct().sorted()
                }
                SavePageButton(vm, comic.key, visible) { p ->
                    val saved = vm.savedPage(comic.key, p)
                    if (saved != null) vm.removeSaved(saved) else vm.savePage(comic, archive, p, sourceCount)
                }
                IconButton(onClick = { showAbout = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = "About this issue", tint = Color.White)
                }
            }
            ToggleIcon(R.drawable.ic_trim, "Trim margins", vm.trimMargins, vm::toggleTrimMargins)
            ToggleIcon(R.drawable.ic_split, "Split two-page spreads", vm.splitSpreads, vm::toggleSplitSpreads)
        }

        BottomBar(
            visible = showUi,
            modifier = Modifier.align(Alignment.BottomCenter),
            page = page,
            spreadLabel = if (mode.continuous) {
                null
            } else {
                spreads[pager.currentPage.coerceIn(0, spreads.lastIndex)]
                    .takeIf { it.size == 2 }
                    ?.let { "${it[0] + 1}–${it[1] + 1}" }
            },
            count = count,
            rtl = mode == ReadingMode.RightToLeft,
            next = if (atEnd && !mode.continuous) next else null,
            onNext = { n -> vm.switchToComic(n) },
            onSeek = { target ->
                scope.launch {
                    if (mode.continuous) list.scrollToItem(target) else pager.scrollToPage(spreadOf(target))
                }
            },
        )

        if (showOptions && showUi) {
            ReadingOptions(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(top = 64.dp, end = 16.dp),
                mode = mode,
                layout = vm.pageLayout,
                fitWidth = vm.fitWidth,
                onMode = onMode,
                onLayout = vm::changePageLayout,
                onFitWidth = vm::changeFitWidth,
                volumeKeys = vm.volumeKeysTurnPages,
                onVolumeKeys = vm::changeVolumeKeys,
                finished = keepFinished,
                onReset = ::resetProgress,
            )
        }
        if (showAbout) ComicInfoDialog(vm, comic, onDismiss = { showAbout = false })
    }
}

@Composable
private fun TopBar(
    visible: Boolean,
    comic: Comic,
    subtitle: String,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    rotationLocked: Boolean,
    onToggleRotation: () -> Unit,
    actions: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
        ) {
            IconButton(onClick = onBack) {
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
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions()
            RotationLockButton(rotationLocked, onToggleRotation)
            IconButton(onClick = onOptions) {
                Icon(Icons.Filled.Settings, contentDescription = "Reading options", tint = Color.White)
            }
        }
    }
}

/** Saves the page on screen to the gallery (or takes it out). With two pages showing, asks which. */
@Composable
private fun SavePageButton(vm: MainViewModel, comicKey: String, visible: List<Int>, onToggle: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val anySaved = visible.any { vm.savedPage(comicKey, it) != null }
    Box {
        IconButton(onClick = { if (visible.size == 1) onToggle(visible[0]) else menu = true }) {
            Icon(
                if (anySaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Save page to gallery",
                tint = if (anySaved) HarbourColors.Accent else Color.White,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            visible.forEach { p ->
                val saved = vm.savedPage(comicKey, p) != null
                DropdownMenuItem(
                    text = { Text(if (saved) "Remove page ${p + 1} from gallery" else "Save page ${p + 1} to gallery") },
                    onClick = {
                        menu = false
                        onToggle(p)
                    },
                )
            }
        }
    }
}

/** An on/off button in the reader's top bar, lit in the accent colour when on (like rotation lock). */
@Composable
private fun ToggleIcon(icon: Int, label: String, on: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(painterResource(icon), contentDescription = label, tint = if (on) HarbourColors.Accent else Color.White)
    }
}

@Composable
private fun BottomBar(
    visible: Boolean,
    modifier: Modifier,
    page: Int,
    spreadLabel: String?,
    count: Int,
    rtl: Boolean,
    next: Comic?,
    onNext: (Comic) -> Unit,
    onSeek: (Int) -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(180)), modifier = modifier) {
        var sliderValue by remember { mutableStateOf<Float?>(null) }
        val shown = sliderValue ?: page.toFloat()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 16.dp),
        ) {
            if (next != null) {
                NextButton(next, onNext)
                Spacer(Modifier.height(14.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 760.dp),
            ) {
                val p = shown.roundToInt()
                Text(
                    if (sliderValue == null && spreadLabel != null) spreadLabel else "${p + 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(56.dp),
                )
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Slider(
                        value = shown,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            sliderValue?.let { onSeek(it.roundToInt()) }
                            sliderValue = null
                        },
                        valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                        enabled = count > 1,
                        colors = SliderDefaults.colors(
                            thumbColor = HarbourColors.Accent,
                            activeTrackColor = HarbourColors.Accent,
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

@Composable
private fun NextButton(next: Comic, onNext: (Comic) -> Unit) {
    Button(
        onClick = { onNext(next) },
        colors = ButtonDefaults.buttonColors(containerColor = HarbourColors.Accent, contentColor = HarbourColors.OnAccent),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        modifier = Modifier.widthIn(max = 420.dp),
    ) {
        Text("Next: ${next.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReadingOptions(
    modifier: Modifier,
    mode: ReadingMode,
    layout: PageLayout,
    fitWidth: Boolean,
    onMode: (ReadingMode) -> Unit,
    onLayout: (PageLayout) -> Unit,
    onFitWidth: (Boolean) -> Unit,
    volumeKeys: Boolean,
    onVolumeKeys: (Boolean) -> Unit,
    finished: Boolean,
    onReset: () -> Unit,
) {
    Column(
        modifier
            .width(380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HarbourColors.Surface)
            // Swallow taps so they don't reach the page underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(20.dp),
    ) {
        Text("Reading options", style = MaterialTheme.typography.titleMedium, color = HarbourColors.Text)
        Spacer(Modifier.height(14.dp))
        OptionLabel("Direction", "Remembered for this group")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Left to right", mode == ReadingMode.LeftToRight) { onMode(ReadingMode.LeftToRight) }
            Choice("Right to left", mode == ReadingMode.RightToLeft) { onMode(ReadingMode.RightToLeft) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Vertical scroll", mode == ReadingMode.Vertical) { onMode(ReadingMode.Vertical) }
            Choice("Horizontal scroll", mode == ReadingMode.Horizontal) { onMode(ReadingMode.Horizontal) }
        }
        if (mode.continuous) {
            Text(
                "Pinch or double-tap to zoom",
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted,
            )
        }

        if (!mode.continuous) {
            Spacer(Modifier.height(14.dp))
            OptionLabel("Pages", "Auto shows two pages side by side in landscape")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageLayout.entries.forEach { l -> Choice(l.label, layout == l) { onLayout(l) } }
            }
            Spacer(Modifier.height(14.dp))
            OptionLabel("Fit", "Fit width lets you scroll down tall pages")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Whole page", !fitWidth) { onFitWidth(false) }
                Choice("Fit width", fitWidth) { onFitWidth(true) }
            }
        }
        Spacer(Modifier.height(14.dp))
        OptionLabel("Volume buttons", "Down for the next page, up for the one before")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Change volume", !volumeKeys) { onVolumeKeys(false) }
            Choice("Turn pages", volumeKeys) { onVolumeKeys(true) }
        }
        if (finished) ResetRow(onReset)
    }
}

/** Shown in reader options for something already finished: it stays at 100% until reset here. */
@Composable
fun ResetRow(onReset: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Finished", style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
            Text("Stays at 100% until you reset it", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
        }
        TextButton(onClick = onReset) { Text("Reset progress", color = HarbourColors.Accent) }
    }
}

@Composable
private fun OptionLabel(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
    Text(detail, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
    Spacer(Modifier.height(6.dp))
}

/** One page's image, decoded in the background. */
@Composable
private fun rememberPage(archive: ComicArchive, index: Int, maxPixels: Long): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, archive, index, maxPixels) {
        value = withContext(Dispatchers.IO) {
            runCatching { archive.decodePage(index, maxPixels)?.asImageBitmap() }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun PageSpinner(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = Color.White.copy(alpha = 0.35f),
            strokeWidth = 2.dp,
        )
    }
}

/**
 * Every page in one continuous strip: stacked and fit to width (vertical, for webtoons) or side by
 * side and fit to height (horizontal). Pinch or double-tap to zoom. A committed zoom lays the pages
 * out bigger (rather than just magnifying them) so scrolling still follows your finger 1:1; while
 * pinching, the strip is magnified live around your fingers. When zoomed, one finger pans across
 * the strip while the list scrolls along it.
 */
@Composable
private fun ContinuousReader(
    archive: ComicArchive,
    state: LazyListState,
    vertical: Boolean,
    next: Comic?,
    onNext: (Comic) -> Unit,
    onTap: (TapZone) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTap)
    var zoom by remember { mutableFloatStateOf(1f) } // committed: pages are laid out this much bigger
    var pinch by remember { mutableFloatStateOf(1f) } // live, visual only, while fingers are down
    var focal by remember { mutableStateOf(Offset.Zero) }
    var pan by remember { mutableFloatStateOf(0f) } // across the strip, in px of the zoomed layout
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun across(o: Offset) = if (vertical) o.x else o.y
    fun along(o: Offset) = if (vertical) o.y else o.x
    fun clampPan(p: Float, z: Float): Float {
        val crossSize = if (vertical) viewport.width else viewport.height
        return p.coerceIn(0f, (crossSize * (z - 1f)).coerceAtLeast(0f))
    }

    // Applies a zoom around a point on screen so whatever is under it stays put.
    fun commitZoom(target: Float, at: Offset) {
        val z = target.coerceIn(1f, 4f)
        val r = z / zoom
        pinch = 1f
        if (abs(r - 1f) < 0.01f) return
        val index = state.firstVisibleItemIndex
        val offset = state.firstVisibleItemScrollOffset
        pan = clampPan((pan + across(at)) * r - across(at), z)
        zoom = z
        scope.launch {
            state.scrollToItem(index, 0)
            state.scrollBy((offset + along(at)) * r - along(at))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            // Pinch is caught before the list sees it, so two fingers zoom instead of scrolling.
            .pointerInput(vertical) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinching = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            if (!pinching) {
                                pinching = true
                                focal = event.calculateCentroid(useCurrent = true)
                            }
                            pinch = (pinch * event.calculateZoom()).coerceIn(1f / zoom, 4f / zoom)
                            event.changes.forEach { it.consume() }
                        } else if (!pinching && zoom > 1.01f) {
                            event.changes.firstOrNull { it.pressed }?.let { change ->
                                pan = clampPan(pan - across(change.positionChangeIgnoreConsumed()), zoom)
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinching) commitZoom(zoom * pinch, focal)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { p -> commitZoom(if (zoom > 1.01f) 1f else 2.5f, p) },
                    onTap = { p ->
                        currentOnTap(
                            when {
                                p.x < size.width * 0.3f -> TapZone.Left
                                p.x > size.width * 0.7f -> TapZone.Right
                                else -> TapZone.Center
                            },
                        )
                    },
                )
            }
            // Gestures above read plain screen positions; the live pinch magnifies only what's drawn.
            .clipToBounds()
            .graphicsLayer {
                scaleX = pinch
                scaleY = pinch
                if (size.width > 0f && size.height > 0f) {
                    transformOrigin = TransformOrigin(focal.x / size.width, focal.y / size.height)
                }
            },
    ) {
        // The strip is laid out [zoom] times the screen across, and shifted by the pan.
        val strip = Modifier.layout { measurable, c ->
            if (vertical) {
                val w = (c.maxWidth * zoom).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(w, c.maxHeight))
                layout(c.maxWidth, c.maxHeight) { placeable.place(-pan.roundToInt(), 0) }
            } else {
                val h = (c.maxHeight * zoom).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(c.maxWidth, h))
                layout(c.maxWidth, c.maxHeight) { placeable.place(0, -pan.roundToInt()) }
            }
        }
        if (vertical) {
            LazyColumn(state = state, modifier = strip) {
                items(archive.pageCount, key = { it }) { index ->
                    StripPage(archive, index, vertical = true)
                }
                if (next != null) {
                    item(key = "next") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            NextButton(next, onNext)
                        }
                    }
                }
            }
        } else {
            LazyRow(state = state, modifier = strip) {
                items(archive.pageCount, key = { it }) { index ->
                    StripPage(archive, index, vertical = false)
                }
                if (next != null) {
                    item(key = "next") {
                        Box(Modifier.fillMaxHeight().padding(horizontal = 48.dp), contentAlignment = Alignment.Center) {
                            NextButton(next, onNext)
                        }
                    }
                }
            }
        }
    }
}

/** One page of the strip: full width when stacked, full height when side by side. */
@Composable
private fun StripPage(archive: ComicArchive, index: Int, vertical: Boolean) {
    val fill = if (vertical) Modifier.fillMaxWidth() else Modifier.fillMaxHeight()
    val bmp = rememberPage(archive, index, PAGE_PIXELS)
    if (bmp == null) {
        PageSpinner(fill.aspectRatio(0.66f))
    } else {
        Image(
            bitmap = bmp,
            contentDescription = "Page ${index + 1}",
            contentScale = if (vertical) ContentScale.FillWidth else ContentScale.FillHeight,
            filterQuality = FilterQuality.High,
            modifier = fill.aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1)),
        )
    }
}

/** One or two pages side by side, with pinch and double-tap zoom over the whole spread. */
@Composable
private fun ZoomableSpread(
    archive: ComicArchive,
    pages: IntArray,
    rtl: Boolean,
    fitWidth: Boolean,
    isCurrent: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onTap: (TapZone) -> Unit,
) {
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
                        // otherwise they fall through to the pager (or fit-width scrolling).
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
        val layer = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }
        when {
            pages.size == 2 -> {
                // Left page hugs the spine on its right, and vice versa. Manga reads right page first.
                val (left, right) = if (rtl) pages[1] to pages[0] else pages[0] to pages[1]
                Row(Modifier.fillMaxSize().then(layer)) {
                    SpreadPage(archive, left, Alignment.CenterEnd, Modifier.weight(1f).fillMaxHeight())
                    SpreadPage(archive, right, Alignment.CenterStart, Modifier.weight(1f).fillMaxHeight())
                }
            }
            fitWidth -> {
                val bmp = rememberPage(archive, pages[0], PAGE_PIXELS)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .then(layer),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (bmp == null) {
                        PageSpinner(Modifier.fillMaxWidth().aspectRatio(0.66f))
                    } else {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Page ${pages[0] + 1}",
                            contentScale = ContentScale.FillWidth,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1)),
                        )
                    }
                }
            }
            else -> SpreadPage(archive, pages[0], Alignment.Center, Modifier.fillMaxSize().then(layer), PAGE_PIXELS)
        }
    }
}

@Composable
private fun SpreadPage(
    archive: ComicArchive,
    index: Int,
    alignment: Alignment,
    modifier: Modifier,
    maxPixels: Long = PAGE_PIXELS / 2,
) {
    val bmp = rememberPage(archive, index, maxPixels)
    if (bmp == null) {
        PageSpinner(modifier)
    } else {
        Image(
            bitmap = bmp,
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.Fit,
            alignment = alignment,
            filterQuality = FilterQuality.High,
            modifier = modifier,
        )
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
            Text("Back to library", color = HarbourColors.Accent)
        }
    }
}
