package com.harbour.reader.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.harbour.reader.Book
import com.harbour.reader.BookFont
import com.harbour.reader.BookMargins
import com.harbour.reader.BookSpacing
import com.harbour.reader.BookStyle
import com.harbour.reader.BookTheme
import com.harbour.reader.EpubBook
import com.harbour.reader.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Chapters are served to the WebView from inside the EPUB under this made-up address. */
private const val HOST = "book.harbour"
private const val SCRIPT_PATH = "__harbour.js"

@Composable
fun BookReaderScreen(vm: MainViewModel, book: Book) {
    if (book.isPdf) {
        ReaderScreen(vm, book.asComic(), book)
        return
    }
    var epub by remember(book.key) { mutableStateOf<EpubBook?>(null) }
    var error by remember(book.key) { mutableStateOf<String?>(null) }

    LaunchedEffect(book.key) {
        var opened: EpubBook? = null
        try {
            opened = withContext(Dispatchers.IO) { EpubBook.open(book.file) }
            epub = opened
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't open this book. ${e.message ?: ""}".trim()
        } finally {
            epub = null
            opened?.close()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(vm.bookStyle.theme.background)),
    ) {
        val e = epub
        when {
            error != null -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: "", color = Color(vm.bookStyle.theme.text), textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = vm::back) { Text("Back to library", color = HarbourColors.Accent) }
            }
            e == null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(32.dp),
                color = HarbourColors.Accent,
                strokeWidth = 2.dp,
            )
            else -> BookReaderContent(vm, book, e)
        }
    }
}

/** Receives page counts from the chapter's script (on a background thread) and hands them to the UI thread. */
private class BookBridge(private val onLayout: (count: Int, page: Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun layout(count: Int, page: Int) {
        main.post { onLayout(count, page) }
    }
}

/** Serves the book's files to the WebView and slips Harbour's style and paging script into each chapter. */
private class BookClient(private val epub: EpubBook) : WebViewClient() {
    @Volatile
    var css: String = ""

    /** Chapters that aren't valid XHTML get a second try as plain HTML. */
    private val htmlFallback = mutableSetOf<String>()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return notFound() // Books never reach the internet.
        val path = url.path?.removePrefix("/") ?: return notFound()
        if (path == SCRIPT_PATH) return response("text/javascript", PAGING_SCRIPT.toByteArray())

        val bytes = runCatching { epub.read(path) }.getOrNull() ?: return notFound()
        var type = epub.mediaType(path)
        val isPage = type == "application/xhtml+xml" || type == "text/html" || path in epub.spine
        if (!isPage) return response(type, bytes)

        if (path in htmlFallback) type = "text/html"
        if (type != "text/html") type = "application/xhtml+xml"
        val extra = "<style id=\"harbour-style\">$css</style><script src=\"https://$HOST/$SCRIPT_PATH\"></script>"
        return response(type, inject(bytes, extra.toByteArray()))
    }

    override fun onPageFinished(view: WebView, url: String) {
        val path = Uri.parse(url).path?.removePrefix("/") ?: return
        if (path in htmlFallback) return
        view.evaluateJavascript("document.getElementsByTagName('parsererror').length > 0") { broken ->
            if (broken == "true") {
                htmlFallback += path
                view.reload()
            }
        }
    }

    private fun response(type: String, bytes: ByteArray): WebResourceResponse {
        val text = type.startsWith("text/") || type.endsWith("xml") || type == "application/javascript"
        return WebResourceResponse(type, if (text) "utf-8" else null, ByteArrayInputStream(bytes))
    }

    private fun notFound() =
        WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    /** Inserts [extra] before </head> (or </body>, or </html>), matching tags case-insensitively. */
    private fun inject(page: ByteArray, extra: ByteArray): ByteArray {
        val at = listOf("</head", "</body", "</html").firstNotNullOfOrNull { indexOfTag(page, it) } ?: return page
        return page.copyOfRange(0, at) + extra + page.copyOfRange(at, page.size)
    }

    private fun indexOfTag(bytes: ByteArray, tag: String): Int? {
        val t = tag.toByteArray()
        outer@ for (i in 0..bytes.size - t.size) {
            for (j in t.indices) {
                val b = bytes[i + j].toInt().toChar().lowercaseChar()
                if (b != t[j].toInt().toChar()) continue@outer
            }
            return i
        }
        return null
    }
}

/** Lays a chapter out in screen-wide columns and moves between them. Reports pages back to Kotlin. */
private const val PAGING_SCRIPT = """
(function () {
  var H = window.harbour = { page: 0 };
  function width() { return window.innerWidth || document.documentElement.clientWidth || 1; }
  H.count = function () { return Math.max(1, Math.round(document.documentElement.scrollWidth / width())); };
  H.go = function (p) {
    var c = H.count();
    H.page = Math.max(0, Math.min(p, c - 1));
    window.scrollTo(H.page * width(), 0);
    if (window.HarbourAndroid) HarbourAndroid.layout(c, H.page);
  };
  H.fraction = function () { var c = H.count(); return c <= 1 ? 0 : H.page / (c - 1); };
  H.goFraction = function (f) { H.go(Math.round(f * (H.count() - 1))); };
  H.setStyle = function (css) {
    var f = H.fraction();
    var s = document.getElementById('harbour-style');
    if (s) s.textContent = css;
    setTimeout(function () { H.goFraction(f); }, 80);
  };
  function start() {
    var m = /[?&]start=([^&]+)/.exec(location.search);
    var s = m ? decodeURIComponent(m[1]) : '0';
    if (s === 'end') H.go(1e9); else H.goFraction(parseFloat(s) || 0);
  }
  window.addEventListener('load', function () {
    start();
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(function () { H.go(H.page); });
    }
  });
  window.addEventListener('resize', function () {
    var f = H.fraction();
    setTimeout(function () { H.goFraction(f); }, 80);
  });
})();
"""

private fun Long.cssColor(): String = "#%06X".format(this and 0xFFFFFF)

private fun Color.cssColor(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** Harbour's look for a chapter: columns for paging, plus your font, size, spacing, margins and colours. */
private fun bookCss(s: BookStyle, accent: Color): String {
    val bg = s.theme.background.cssColor()
    val fg = s.theme.text.cssColor()
    val m = s.margins.px
    val v = 44
    val font = s.font.css?.let { "font-family: $it !important;" } ?: ""
    return """
        html { height: 100vh !important; overflow: hidden !important; margin: 0 !important; padding: 0 !important;
          background: $bg !important; font-size: ${s.sizePercent}% !important; -webkit-text-size-adjust: none !important; }
        body { height: 100vh !important; width: auto !important; max-width: none !important; margin: 0 !important;
          padding: ${v}px ${m}px !important; box-sizing: border-box !important;
          column-width: calc(100vw - ${2 * m}px) !important; column-gap: ${2 * m}px !important; column-fill: auto !important;
          overflow: visible !important; background: $bg !important; color: $fg !important;
          line-height: ${s.spacing.lineHeight} !important; $font }
        body * { color: inherit !important; background-color: transparent !important; max-width: 100% !important; $font }
        p, li, blockquote, dd, dt { line-height: inherit !important; }
        a, a * { color: ${accent.cssColor()} !important; text-decoration: none !important; }
        img, svg, video, image { max-width: 100% !important; max-height: calc(100vh - ${2 * v}px) !important;
          height: auto; object-fit: contain; break-inside: avoid !important; }
    """.trimIndent().replace('\n', ' ')
}

private class WebHolder {
    var view: WebView? = null
    var client: BookClient? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BookReaderContent(vm: MainViewModel, book: Book, epub: EpubBook) {
    val chapters = epub.spine.size
    val saved = remember(book.key) { vm.bookProgress[book.key] }
    // A finished book opens at the start and stays at 100% until you reset it.
    var keepFinished by remember(book.key) { mutableStateOf(saved?.finished == true) }
    var chapter by remember(book.key) {
        mutableIntStateOf(if (keepFinished) 0 else (saved?.chapter ?: 0).coerceIn(0, chapters - 1))
    }
    var start by remember(book.key) { mutableStateOf(if (keepFinished) "0" else (saved?.fraction ?: 0f).toString()) }
    var nonce by remember(book.key) { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var showUi by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<String?>(null) } // "style" or "contents"
    val holder = remember { WebHolder() }
    val style = vm.bookStyle
    val accent = HarbourColors.Accent

    fun url(): String {
        val path = epub.spine[chapter].split('/').joinToString("/") { Uri.encode(it) }
        return "https://$HOST/$path?start=${Uri.encode(start)}&n=$nonce"
    }

    val onLayout by rememberUpdatedState { count: Int, p: Int ->
        pages = count
        page = p
        val fraction = if (count <= 1) 1f else p.toFloat() / (count - 1)
        if (!keepFinished) vm.saveBookProgress(book, chapter, fraction, chapters)
    }

    fun goToChapter(ch: Int, from: String) {
        chapter = ch.coerceIn(0, chapters - 1)
        start = from
        page = 0
        pages = 1
        nonce++
    }

    fun next() {
        if (page < pages - 1) holder.view?.evaluateJavascript("window.harbour && harbour.go(harbour.page + 1)", null)
        else if (chapter < chapters - 1) goToChapter(chapter + 1, "0")
        else showUi = true
    }

    fun prev() {
        if (page > 0) holder.view?.evaluateJavascript("window.harbour && harbour.go(harbour.page - 1)", null)
        else if (chapter > 0) goToChapter(chapter - 1, "end")
    }

    LaunchedEffect(nonce) {
        if (nonce > 0) holder.view?.loadUrl(url())
    }
    LaunchedEffect(style, accent) {
        val css = bookCss(style, accent)
        holder.client?.css = css
        holder.view?.setBackgroundColor(Color(style.theme.background).toArgb())
        holder.view?.evaluateJavascript("window.harbour && harbour.setStyle(${JSONObject.quote(css)})", null)
    }
    LaunchedEffect(Unit) {
        delay(2200)
        if (panel == null) showUi = false
    }
    BackHandler(enabled = panel != null) { panel = null }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            factory = { ctx ->
                val client = BookClient(epub).also { it.css = bookCss(style, accent) }
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.textZoom = 100
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setBackgroundColor(Color(style.theme.background).toArgb())
                    addJavascriptInterface(BookBridge { count, p -> onLayout(count, p) }, "HarbourAndroid")
                    webViewClient = client
                    holder.view = this
                    holder.client = client
                    loadUrl(url())
                }
            },
            onRelease = {
                holder.view = null
                it.destroy()
            },
        )

        // All touches land here, so the page itself never scrolls or follows links.
        val currentNext by rememberUpdatedState(::next)
        val currentPrev by rememberUpdatedState(::prev)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var last = down.position
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.id == down.id }?.let { last = it.position }
                        } while (event.changes.any { it.pressed })
                        val dx = last.x - down.position.x
                        val dy = last.y - down.position.y
                        when {
                            abs(dx) > 48.dp.toPx() && abs(dx) > abs(dy) -> if (dx < 0) currentNext() else currentPrev()
                            abs(dx) < viewConfiguration.touchSlop && abs(dy) < viewConfiguration.touchSlop -> when {
                                down.position.x < size.width * 0.3f -> currentPrev()
                                down.position.x > size.width * 0.7f -> currentNext()
                                else -> {
                                    showUi = !showUi
                                    if (!showUi) panel = null
                                }
                            }
                        }
                    }
                },
        )

        BookTopBar(
            visible = showUi,
            book = book,
            onBack = vm::back,
            onContents = { panel = if (panel == "contents") null else "contents" },
            onStyle = { panel = if (panel == "style") null else "style" },
            rotationLocked = vm.rotationLocked,
            onToggleRotation = vm::toggleRotationLock,
        )

        BookBottomBar(
            visible = showUi,
            modifier = Modifier.align(Alignment.BottomCenter),
            chapterTitle = epub.chapterTitle(chapter) ?: "Chapter ${chapter + 1} of $chapters",
            page = page,
            pages = pages,
            overall = (chapter + if (pages <= 1) 0f else page.toFloat() / (pages - 1)) / chapters,
            onSeek = { v ->
                val target = (v * chapters).coerceIn(0f, chapters - 0.001f)
                val ch = floor(target).toInt()
                goToChapter(ch, (target - ch).toString())
            },
        )

        if (showUi && panel == "style") {
            StylePanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(top = 64.dp, end = 16.dp),
                style = style,
                onChange = vm::changeBookStyle,
                finished = keepFinished,
                onReset = {
                    vm.setBookRead(listOf(book.key), false)
                    keepFinished = false
                    vm.saveBookProgress(book, chapter, if (pages <= 1) 1f else page.toFloat() / (pages - 1), chapters)
                },
            )
        }
        if (showUi && panel == "contents") {
            ContentsPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(top = 64.dp, end = 16.dp),
                epub = epub,
                current = chapter,
                onPick = { ch ->
                    panel = null
                    goToChapter(ch, "0")
                },
            )
        }
    }
}

@Composable
private fun BookTopBar(
    visible: Boolean,
    book: Book,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onStyle: () -> Unit,
    rotationLocked: Boolean,
    onToggleRotation: () -> Unit,
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
                Text(book.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RotationLockButton(rotationLocked, onToggleRotation)
            IconButton(onClick = onContents) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents", tint = Color.White)
            }
            TextButton(onClick = onStyle) {
                Text("Aa", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BookBottomBar(
    visible: Boolean,
    modifier: Modifier,
    chapterTitle: String,
    page: Int,
    pages: Int,
    overall: Float,
    onSeek: (Float) -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(180)), modifier = modifier) {
        var sliderValue by remember { mutableStateOf<Float?>(null) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 16.dp),
        ) {
            Text(
                chapterTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 640.dp),
            )
            Text(
                "Page ${page + 1} of $pages in this chapter · ${((sliderValue ?: overall) * 100).roundToInt()}%",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
            )
            Slider(
                value = sliderValue ?: overall.coerceIn(0f, 1f),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    sliderValue?.let(onSeek)
                    sliderValue = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = HarbourColors.Accent,
                    activeTrackColor = HarbourColors.Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                ),
                modifier = Modifier.widthIn(max = 760.dp),
            )
        }
    }
}

@Composable
private fun StylePanel(modifier: Modifier, style: BookStyle, onChange: (BookStyle) -> Unit, finished: Boolean, onReset: () -> Unit) {
    Column(
        modifier
            .width(400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HarbourColors.Surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(20.dp),
    ) {
        Text("Text and page", style = MaterialTheme.typography.titleMedium, color = HarbourColors.Text)
        Spacer(Modifier.height(14.dp))

        PanelLabel("Size")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onChange(style.copy(sizePercent = style.sizePercent - 10)) }) { Text("A−") }
            Text(
                "${style.sizePercent}%",
                color = HarbourColors.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            OutlinedButton(onClick = { onChange(style.copy(sizePercent = style.sizePercent + 10)) }) { Text("A+") }
        }

        Spacer(Modifier.height(14.dp))
        PanelLabel("Font")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookFont.entries.forEach { f -> Choice(f.label, style.font == f) { onChange(style.copy(font = f)) } }
        }

        Spacer(Modifier.height(14.dp))
        PanelLabel("Line spacing")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookSpacing.entries.forEach { s -> Choice(s.label, style.spacing == s) { onChange(style.copy(spacing = s)) } }
        }

        Spacer(Modifier.height(14.dp))
        PanelLabel("Margins")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookMargins.entries.forEach { m -> Choice(m.label, style.margins == m) { onChange(style.copy(margins = m)) } }
        }

        Spacer(Modifier.height(14.dp))
        PanelLabel("Page colour")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookTheme.entries.forEach { t ->
                val chosen = style.theme == t
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(t.background))
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) HarbourColors.Accent else HarbourColors.Outline, CircleShape)
                            .clickable { onChange(style.copy(theme = t)) },
                    ) {
                        Text("Aa", color = Color(t.text), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
                }
            }
        }
        if (finished) ResetRow(onReset)
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ContentsPanel(modifier: Modifier, epub: EpubBook, current: Int, onPick: (Int) -> Unit) {
    // Use the book's contents when it has them, otherwise just number the chapters.
    val rows: List<Pair<String, Int>> = remember(epub) {
        val fromToc = epub.toc.mapNotNull { t ->
            val ch = epub.spine.indexOf(t.path)
            if (ch < 0) null else ("    ".repeat(t.depth.coerceAtMost(3)) + t.title) to ch
        }
        fromToc.ifEmpty { epub.spine.indices.map { "Chapter ${it + 1}" to it } }
    }
    Column(
        modifier
            .width(400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HarbourColors.Surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(vertical = 16.dp),
    ) {
        Text(
            "Contents",
            style = MaterialTheme.typography.titleMedium,
            color = HarbourColors.Text,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            itemsIndexed(rows) { _, (title, ch) ->
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ch == current) HarbourColors.Accent else HarbourColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(ch) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
