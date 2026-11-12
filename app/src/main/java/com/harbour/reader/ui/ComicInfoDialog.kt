package com.harbour.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.harbour.reader.Comic
import com.harbour.reader.ComicInfo
import com.harbour.reader.MainViewModel
import java.util.Locale

/**
 * About this issue: the details tagging tools leave inside the file (ComicInfo.xml), plus what
 * Harbour knows itself: group, progress, file. [onRead] adds a Read button (used by the gallery).
 */
@Composable
fun ComicInfoDialog(vm: MainViewModel, comic: Comic, onDismiss: () -> Unit, onRead: (() -> Unit)? = null) {
    var info by remember(comic.key) { mutableStateOf<ComicInfo?>(null) }
    var loaded by remember(comic.key) { mutableStateOf(false) }
    LaunchedEffect(comic.key) {
        info = vm.comicInfo(comic)
        loaded = true
    }
    val group = vm.groupOfKey[comic.key]
    val progress = vm.progress[comic.key]

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(HarbourColors.Surface)
                .padding(24.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row {
                    CoverImage(comic, Modifier.width(120.dp).aspectRatio(2f / 3f))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        val i = info
                        Text(i?.heading ?: comic.title, style = MaterialTheme.typography.titleLarge, color = HarbourColors.Text)
                        i?.get("Title")?.let {
                            Spacer(Modifier.height(2.dp))
                            Text("“$it”", style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
                        }
                        val line = listOfNotNull(i?.date, i?.get("Publisher")).joinToString(" · ")
                        if (line.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOfNotNull(
                                group?.name,
                                progress?.let { if (it.finished) "Read" else "${(it.fraction * 100).toInt()}% read" },
                            ).joinToString(" · ").ifEmpty { "Not in a group" },
                            style = MaterialTheme.typography.labelLarge,
                            color = HarbourColors.Accent,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                val i = info
                when {
                    !loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = HarbourColors.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Reading issue details…", style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Muted)
                    }
                    i == null -> Text(
                        "This file doesn't carry issue details. Comics tagged with ComicRack, ComicTagger or Mylar " +
                            "include them (a ComicInfo.xml inside the file).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarbourColors.Muted,
                    )
                    else -> {
                        i["Summary"]?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Text)
                            Spacer(Modifier.height(16.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            i.details.forEach { (label, value) -> InfoRow(label, value) }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = HarbourColors.Outline)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("File", comic.name)
                    InfoRow("Size", String.format(Locale.getDefault(), "%.1f MB", comic.size / 1_048_576.0))
                    if (comic.folderPath.isNotEmpty()) InfoRow("Folder", comic.folderPath.trimStart('/'))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onRead != null) {
                    TextButton(onClick = onRead) { Text("Read", color = HarbourColors.Accent) }
                }
                TextButton(onClick = onDismiss) { Text("Close", color = HarbourColors.Accent) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelLarge, color = HarbourColors.Muted, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HarbourColors.Text, modifier = Modifier.weight(1f))
    }
}
