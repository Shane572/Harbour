package com.harbour.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import com.harbour.reader.VoiceRole
import com.harbour.reader.VoicePack
import kotlin.math.abs
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harbour.reader.CoverSize
import com.harbour.reader.MainViewModel

@Composable
fun SettingsSection(vm: MainViewModel) {
    LazyColumn(
        contentPadding = sectionPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.displaySmall, color = HarbourColors.Text)
        }

        item {
            SettingsCard("Folders") {
                Text(
                    "Where Harbour looks for comics, including everything inside. Folders don't affect how " +
                        "your library is organised; that's what groups are for.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarbourColors.Muted,
                )
                Spacer(Modifier.height(10.dp))
                vm.roots.forEach { root ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(root.name, style = MaterialTheme.typography.titleMedium, color = HarbourColors.Text)
                            Text(
                                "${vm.comicCountIn(root)} comics · ${vm.bookCountIn(root)} books",
                                style = MaterialTheme.typography.labelSmall,
                                color = HarbourColors.Muted,
                            )
                        }
                        IconButton(onClick = { vm.removeRoot(root) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove ${root.name}", tint = HarbourColors.Muted)
                        }
                    }
                    HorizontalDivider(color = HarbourColors.Outline)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::openBrowser) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add folder")
                    }
                    OutlinedButton(onClick = vm::rescan, enabled = !vm.scanning) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (vm.scanning) "Looking…" else "Look for new comics")
                    }
                }
            }
        }

        item {
            SettingsCard("Appearance") {
                SettingLabel("Accent colour")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentChoices.forEach { choice ->
                        val chosen = choice == HarbourColors.accent
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(choice.color)
                                .then(if (chosen) Modifier.border(3.dp, HarbourColors.Text, CircleShape) else Modifier)
                                .clickable { vm.changeAccent(choice) },
                        ) {
                            if (chosen) {
                                Icon(Icons.Filled.Check, contentDescription = choice.name, tint = choice.onColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                SettingLabel("Background")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Choice("Dark", selected = !HarbourColors.black) { vm.changeBlackBackground(false) }
                    Choice("Black", selected = HarbourColors.black) { vm.changeBlackBackground(true) }
                }

                Spacer(Modifier.height(20.dp))
                SettingLabel("Cover size")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoverSize.entries.forEach { size ->
                        Choice(size.label, selected = vm.coverSize == size) { vm.changeCoverSize(size) }
                    }
                }
            }
        }

        item {
            var confirmRestore by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { vm.refreshBackupInfo() }
            SettingsCard("Backup") {
                Text(
                    "Saves your groups, read orders, covers, Will Read, reading progress and settings to " +
                        "Harbour/harbour-backup.json on the tablet. It's also saved automatically whenever you leave Harbour.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HarbourColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    vm.lastBackup?.let { "Last saved ${formatWhen(it)}" } ?: "No backup yet",
                    style = MaterialTheme.typography.labelLarge,
                    color = HarbourColors.Text,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::backUpNow) { Text("Back up now") }
                    if (vm.lastBackup != null) {
                        OutlinedButton(onClick = { confirmRestore = true }) { Text("Restore") }
                    }
                }
            }
            if (confirmRestore) {
                RestoreDialog(vm, onDismiss = { confirmRestore = false })
            }
        }

        item { ReadAloudSettings(vm) }

        item {
            SettingsCard("Updates") {
                UpdateSettingsRow(vm)
            }
        }
    }
}

/** Downloading the free voices, and picking who reads what. */
@Composable
private fun ReadAloudSettings(vm: MainViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    SettingsCard("Read aloud") {
        Text(
            "Reads comics out loud with free voices that run on the tablet, with no internet needed once " +
                "they're downloaded. Start it with the speech button at the top of the reader. Caption boxes get " +
                "the narrator and speech bubbles start with the character voice below; change any bubble's voice " +
                "from the bar while it's reading.",
            style = MaterialTheme.typography.bodyMedium,
            color = HarbourColors.Muted,
        )
        Spacer(Modifier.height(16.dp))
        SettingLabel("Voices")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VoicePack.entries.forEach { pack ->
                Choice(pack.label, vm.voicePack == pack) { if (vm.voiceDownload == null) vm.changeVoicePack(pack) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(vm.voicePack.detail, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
        Spacer(Modifier.height(14.dp))
        val progress = vm.voiceDownload
        when {
            progress != null -> {
                Text(
                    if (progress < 0f) "Unpacking the voices…" else "Downloading the voices… ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = HarbourColors.Text,
                )
                Spacer(Modifier.height(8.dp))
                if (progress < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = HarbourColors.Accent, trackColor = HarbourColors.SurfaceHigh)
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = HarbourColors.Accent,
                        trackColor = HarbourColors.SurfaceHigh,
                    )
                }
            }
            !vm.voicesReady -> OutlinedButton(onClick = vm::downloadVoices) {
                Text("Download ${vm.voicePack.label} voices (${vm.voicePack.downloadMb} MB)")
            }
            else -> {
                VoiceRole.entries.forEach { role -> VoicePicker(vm, role) }
                vm.sampleStatus?.let { status ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.sampleBusy) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = HarbourColors.Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall, color = HarbourColors.Text)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SettingLabel("Speech bubbles start as")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Choice("Male", vm.characterVoice == VoiceRole.Male) { vm.changeCharacterVoice(VoiceRole.Male) }
                    Choice("Female", vm.characterVoice == VoiceRole.Female) { vm.changeCharacterVoice(VoiceRole.Female) }
                }
                Spacer(Modifier.height(16.dp))
                SettingLabel("Speed")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Slower" to 0.85f, "Normal" to 1f, "Faster" to 1.15f).forEach { (label, speed) ->
                        Choice(label, abs(vm.speechSpeed - speed) < 0.01f) { vm.changeSpeechSpeed(speed) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmDelete = true }) { Text("Delete voices", color = HarbourColors.Muted) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete the voices?",
            text = "Deletes the ${vm.voicePack.label} voices from the tablet. You can download them again any time.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.deleteVoices()
            },
        )
    }
}

@Composable
private fun VoicePicker(vm: MainViewModel, role: VoiceRole) {
    var open by remember { mutableStateOf(false) }
    val voice = vm.voicePack.byId(vm.voiceFor(role))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(role.label, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { open = true }) {
                Text(voice.label)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                vm.voicePack.voices.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(if (v.id == voice.id) "${v.label}  ✓" else v.label) },
                        onClick = {
                            open = false
                            vm.changeVoice(role, v.id)
                            vm.playSample(v.id)
                        },
                    )
                }
            }
        }
        IconButton(onClick = { vm.playSample(voice.id) }, enabled = !vm.sampleBusy) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Hear ${role.label.lowercase()} voice", tint = HarbourColors.Accent)
        }
    }
}

fun formatWhen(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

/** Confirms replacing everything with the backup. Harbour restarts to load it. */
@Composable
fun RestoreDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Restore your backup?",
        text = "This replaces your groups, read orders, Will Read, progress and settings with the backup from " +
            "${vm.lastBackup?.let(::formatWhen) ?: "earlier"}. Harbour will restart.",
        confirmLabel = "Restore",
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            vm.restoreBackup()
        },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HarbourColors.Surface)
            .padding(22.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = HarbourColors.Text)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = HarbourColors.Muted,
            selectedContainerColor = HarbourColors.Accent,
            selectedLabelColor = HarbourColors.OnAccent,
        ),
    )
}
