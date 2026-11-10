package com.harbour.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harbour.reader.ui.FolderBrowserScreen
import com.harbour.reader.ui.HarbourTheme
import com.harbour.reader.ui.LibraryScreen
import com.harbour.reader.ui.LocalCovers
import com.harbour.reader.ui.ReaderScreen
import com.harbour.reader.ui.SeriesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HarbourTheme {
                HarbourApp()
            }
        }
    }
}

@Composable
fun HarbourApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addRoot(uri)
    }
    // Some tablets (e.g. Amazon Fire) have no system folder picker; use Harbour's own instead.
    val addFolder = {
        try {
            pickFolder.launch(null)
        } catch (e: ActivityNotFoundException) {
            vm.openBrowser()
        }
    }

    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            vm.message = null
        }
    }

    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
    ImmersiveMode(enabled = vm.screen is Screen.Reader)

    val holder = rememberSaveableStateHolder()
    CompositionLocalProvider(LocalCovers provides vm.covers) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "screen",
        ) { screen ->
            holder.SaveableStateProvider(screen.stateKey) {
                when (screen) {
                    Screen.Library -> LibraryScreen(vm, onAddFolder = addFolder)
                    is Screen.SeriesView -> SeriesScreen(vm, screen.path)
                    is Screen.Reader -> ReaderScreen(vm, screen.comic)
                    Screen.Browse -> FolderBrowserScreen(vm)
                }
            }
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
