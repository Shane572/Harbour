package com.harbour.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harbour.reader.Group
import com.harbour.reader.R

/** Asks for a name, e.g. for a new group or a rename. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HarbourColors.Surface,
        title = { Text(title, color = HarbourColors.Text) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = if (name.isNotBlank()) HarbourColors.Accent else HarbourColors.Muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = HarbourColors.Muted) }
        },
    )
}

/** Picks an existing group, or names a new one, for the selected comics. */
@Composable
fun AddToGroupDialog(
    count: Int,
    groups: List<Group>,
    onDismiss: () -> Unit,
    onPick: (Group) -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HarbourColors.Surface,
        title = { Text(if (count == 1) "Add 1 comic to a group" else "Add $count comics to a group", color = HarbourColors.Text) },
        text = {
            Column {
                if (groups.isNotEmpty()) {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        groups.forEach { g ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPick(g) }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_group),
                                    contentDescription = null,
                                    tint = HarbourColors.Muted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    g.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = HarbourColors.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text("New group", style = MaterialTheme.typography.labelLarge, color = HarbourColors.Muted)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create group", color = if (name.isNotBlank()) HarbourColors.Accent else HarbourColors.Muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = HarbourColors.Muted) }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HarbourColors.Surface,
        title = { Text(title, color = HarbourColors.Text) },
        text = { Text(text, color = HarbourColors.Muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = HarbourColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = HarbourColors.Muted) }
        },
    )
}

@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HarbourColors.Surface,
        title = { Text("Harbour closed unexpectedly", color = HarbourColors.Text) },
        text = {
            Column {
                Text(
                    "Copy these details and send them to Claude so it can fix the problem.",
                    color = HarbourColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    report,
                    color = HarbourColors.Text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HarbourColors.Bg)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(report))
                copied = true
            }) {
                Text(if (copied) "Copied" else "Copy details", color = HarbourColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = HarbourColors.Muted) }
        },
    )
}
