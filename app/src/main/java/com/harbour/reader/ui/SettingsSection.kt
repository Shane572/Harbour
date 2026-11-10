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
                                "${vm.comicCountIn(root)} comics",
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
            SettingsCard("Updates") {
                UpdateSettingsRow(vm)
            }
        }
    }
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
