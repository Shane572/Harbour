package com.harbour.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.harbour.reader.ui.HarbourColors
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harbour.reader.ui.BookReaderScreen
import com.harbour.reader.ui.CrashReportDialog
import com.harbour.reader.ui.FolderBrowserScreen
import com.harbour.reader.ui.GalleryViewer
import com.harbour.reader.ui.HarbourTheme
import com.harbour.reader.ui.LocalCovers
import com.harbour.reader.ui.MainScreen
import com.harbour.reader.ui.ReaderScreen
import com.harbour.reader.ui.WelcomeScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    // Volume buttons turn pages while reading a comic, if that's turned on in Reading options.
    private fun pageKey(keyCode: Int): Int? {
        // Comics, and PDF books (read in the same page-by-page reader); not EPUBs.
        val paged = vm.screen is Screen.Reader || (vm.screen as? Screen.BookReader)?.book?.isPdf == true
        if (!vm.volumeKeysTurnPages || !paged) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> 1
            KeyEvent.KEYCODE_VOLUME_UP -> -1
            else -> null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val direction = pageKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) vm.turnPage(direction)
        return true // and don't change the volume
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (pageKey(keyCode) != null) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        vm.autoBackup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HarbourTheme {
                HarbourRoot()
            }
        }
    }
}

@Composable
fun HarbourRoot(vm: MainViewModel = viewModel()) {
    // Messages ("Rotation locked", "Saved page 12…") show in Harbour's own style rather than as
    // Android toasts, which on the tablet come with a generic Android icon.
    var shownMessage by remember { mutableStateOf("") }
    var messageCount by remember { mutableIntStateOf(0) }
    var messageVisible by remember { mutableStateOf(false) }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            shownMessage = msg
            messageCount++
            vm.message = null
        }
    }
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            messageVisible = true
            delay(2800)
            messageVisible = false
        }
    }

    BackHandler(enabled = vm.canGoBack) { vm.back() }
    val reading = vm.screen is Screen.Reader || vm.screen is Screen.BookReader || vm.screen is Screen.GalleryView
    ImmersiveMode(enabled = reading)
    RotationLock(enabled = reading && vm.rotationLocked)

    val holder = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalCovers provides vm.covers) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "screen",
        ) { screen ->
            holder.SaveableStateProvider(screen.stateKey) {
                when (screen) {
                    Screen.Main -> if (vm.roots.isEmpty()) WelcomeScreen(vm) else MainScreen(vm)
                    is Screen.Reader -> ReaderScreen(vm, screen.comic)
                    is Screen.BookReader -> BookReaderScreen(vm, screen.book)
                    Screen.Browse -> FolderBrowserScreen(vm)
                    is Screen.GalleryView -> GalleryViewer(vm, screen)
                }
            }
        }
    }

    AnimatedVisibility(
        visible = messageVisible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(240)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
    ) {
        Text(
            shownMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = HarbourColors.Text,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(HarbourColors.SurfaceHigh)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
    }

    vm.crashReport?.let { report -> CrashReportDialog(report, onDismiss = vm::dismissCrashReport) }
}

/** Holds the screen in its current orientation while reading, if you've locked it. */
@Composable
private fun RotationLock(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val activity = view.context.findActivity()
        if (enabled) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            if (enabled) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/** Hides the status and navigation bars and keeps the screen awake while reading. */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = true
        }
        onDispose {
            if (enabled) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                view.keepScreenOn = false
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
