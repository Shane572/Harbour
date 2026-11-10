package com.harbour.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.harbour.reader.MainViewModel
import com.harbour.reader.UpdateInfo
import com.harbour.reader.UpdateState
import kotlin.math.roundToInt

val UpdateState.showsBanner: Boolean
    get() = this is UpdateState.Available || this is UpdateState.Downloading ||
        this is UpdateState.Waiting || (this is UpdateState.Failed && info != null)

/** Shown at the top of the library only while there is an update to act on. */
@Composable
fun UpdateBanner(vm: MainViewModel) {
    val state = vm.update

    AnimatedVisibility(visible = state.showsBanner) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HarbourColors.Surface)
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                when (state) {
                    is UpdateState.Available -> {
                        BannerTitle(if (state.info.preview) "Preview build available" else "Update available")
                        BannerDetail("Build ${state.info.build}${sizeText(state.info)} · you have build ${vm.currentBuild}")
                    }
                    is UpdateState.Downloading -> {
                        BannerTitle("Downloading update… ${(state.progress * 100).roundToInt()}%")
                        Spacer(Modifier.height(8.dp))
                        ProgressLine(state.progress)
                    }
                    is UpdateState.Waiting -> {
                        BannerTitle("Almost there")
                        BannerDetail("Tap Update on Android's prompt. Harbour will reopen on the new version.")
                    }
                    is UpdateState.Failed -> {
                        BannerTitle("Update didn't finish")
                        BannerDetail(state.message)
                    }
                    else -> Unit
                }
            }
            Spacer(Modifier.width(12.dp))
            val info: UpdateInfo? = when (state) {
                is UpdateState.Available -> state.info
                is UpdateState.Waiting -> state.info
                is UpdateState.Failed -> state.info
                else -> null
            }
            if (info != null) {
                Button(
                    onClick = { vm.installUpdate(info) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HarbourColors.Accent,
                        contentColor = HarbourColors.OnAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(if (state is UpdateState.Available) "Update" else "Try again")
                }
            }
        }
    }
}

/** The "About" row in the folders sheet: current build and a manual check. */
@Composable
fun UpdateSettingsRow(vm: MainViewModel) {
    val state = vm.update
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Harbour build ${vm.currentBuild}", style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
            Text(
                when (state) {
                    UpdateState.Checking -> "Checking for updates…"
                    UpdateState.UpToDate -> "You're on the latest version"
                    is UpdateState.Available -> "Build ${state.info.build} is available"
                    is UpdateState.Downloading -> "Downloading update…"
                    is UpdateState.Waiting -> "Waiting for you to confirm the install"
                    is UpdateState.Failed -> state.message
                    UpdateState.Idle -> "Updates install straight from GitHub"
                },
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted,
            )
        }
        val available = state as? UpdateState.Available
        if (available != null) {
            TextButton(onClick = { vm.installUpdate(available.info) }) {
                Text("Update", color = HarbourColors.Accent)
            }
        } else {
            TextButton(
                onClick = { vm.checkForUpdate() },
                enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
            ) {
                Text("Check now", color = HarbourColors.Accent)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Preview builds", style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
            Text(
                "Get every new build before it's released. For testing on the PC; leave off on the tablet.",
                style = MaterialTheme.typography.labelSmall,
                color = HarbourColors.Muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = vm.previewBuilds,
            onCheckedChange = { vm.setPreviewBuilds(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = HarbourColors.OnAccent,
                checkedTrackColor = HarbourColors.Accent,
            ),
        )
    }
}

@Composable
private fun BannerTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text)
}

@Composable
private fun BannerDetail(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
}

private fun sizeText(info: UpdateInfo): String =
    if (info.sizeBytes > 0) " · %.1f MB".format(info.sizeBytes / 1_048_576.0) else ""
