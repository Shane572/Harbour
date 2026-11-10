package com.harbour.reader.ui

import android.Manifest
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harbour.reader.MainViewModel
import com.harbour.reader.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Harbour's own folder picker, for tablets where Android's folder picker doesn't work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.hasAccess(context)) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        granted = StorageAccess.hasAccess(context)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val requestAccess: () -> Unit = {
        if (StorageAccess.usesSettingsScreen) {
            val opened = StorageAccess.settingsIntents(context).any { intent ->
                try {
                    settingsLauncher.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
            if (!opened) vm.message = "Open Settings › Apps › Harbour and allow access to all files"
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val roots = remember { StorageAccess.roots(context) }
    var path by rememberSaveable { mutableStateOf<String?>(if (roots.size == 1) roots[0].dir.path else null) }
    val current = path?.let(::File)
    val atRoot = current == null || roots.any { it.dir.path == current.path }

    // Up from a storage root goes back to the list of roots (or out, if there's only one).
    fun goUp() {
        path = if (current == null || atRoot) null else current.parentFile?.path
    }

    BackHandler(enabled = current != null && !(atRoot && roots.size == 1)) { goUp() }

    val listing by produceState<StorageAccess.Listing?>(null, path, granted) {
        value = if (current == null || !granted) null else withContext(Dispatchers.IO) { StorageAccess.list(current) }
    }

    Scaffold(
        containerColor = HarbourColors.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Choose a folder", style = MaterialTheme.typography.titleLarge)
                        Text(
                            current?.let { displayPath(it, roots) } ?: "Pick where your comics are stored",
                            style = MaterialTheme.typography.labelSmall,
                            color = HarbourColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (current == null || (atRoot && roots.size == 1)) vm.back() else goUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HarbourColors.Bg,
                    titleContentColor = HarbourColors.Text,
                    navigationIconContentColor = HarbourColors.Text,
                ),
            )
        },
        bottomBar = {
            if (granted && current != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(HarbourColors.Surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (val n = listing?.comicsHere ?: 0) {
                                0 -> "No comics directly in this folder. Subfolders are included too."
                                1 -> "1 comic here, plus any in subfolders"
                                else -> "$n comics here, plus any in subfolders"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = HarbourColors.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = { vm.addFileRoot(current) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HarbourColors.Accent,
                                contentColor = HarbourColors.OnAccent,
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Text("Use this folder", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !granted -> AccessNeeded(onAllow = requestAccess)
                current == null -> FolderList(
                    rows = roots.map { it.label to it.dir },
                    onOpen = { path = it.path },
                )
                listing == null -> Unit
                listing?.readable == false -> Message("Harbour can't read this folder.")
                listing?.folders.isNullOrEmpty() -> Message("No folders inside. Tap Use this folder if your comics are here.")
                else -> FolderList(
                    rows = listing!!.folders.map { it.name to it },
                    onOpen = { path = it.path },
                )
            }
        }
    }
}

@Composable
private fun FolderList(rows: List<Pair<String, File>>, onOpen: (File) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(rows, key = { it.second.path }) { (label, dir) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(dir) }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = HarbourColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = HarbourColors.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            HorizontalDivider(color = HarbourColors.Outline, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun AccessNeeded(onAllow: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Let Harbour see your files",
            style = MaterialTheme.typography.headlineSmall,
            color = HarbourColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (StorageAccess.usesSettingsScreen) {
                "Android will open a settings page. Switch on \"Allow access to manage all files\", then come back here."
            } else {
                "Android will ask whether Harbour can read your storage. Tap Allow."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = HarbourColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Harbour only reads files. It never changes or uploads them.",
            style = MaterialTheme.typography.bodySmall,
            color = HarbourColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAllow,
            colors = ButtonDefaults.buttonColors(containerColor = HarbourColors.Accent, contentColor = HarbourColors.OnAccent),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("Allow access", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = HarbourColors.Muted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

private fun displayPath(dir: File, roots: List<StorageAccess.Root>): String {
    val root = roots.firstOrNull { dir.path == it.dir.path || dir.path.startsWith(it.dir.path + "/") }
        ?: return dir.path
    val rest = dir.path.removePrefix(root.dir.path).trim('/')
    return if (rest.isEmpty()) root.label else "${root.label} › ${rest.replace("/", " › ")}"
}
