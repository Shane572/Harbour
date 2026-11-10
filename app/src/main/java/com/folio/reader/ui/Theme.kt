package com.folio.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FolioColors {
    val Bg = Color(0xFF0C0C0E)
    val Surface = Color(0xFF151518)
    val SurfaceHigh = Color(0xFF1E1E23)
    val SurfaceHighest = Color(0xFF28282E)
    val Accent = Color(0xFFE8B04B)
    val OnAccent = Color(0xFF1C1405)
    val Text = Color(0xFFEDEBE6)
    val Muted = Color(0xFF8E8C93)
    val Outline = Color(0xFF2E2E34)
}

private val scheme = darkColorScheme(
    primary = FolioColors.Accent,
    onPrimary = FolioColors.OnAccent,
    secondary = FolioColors.Accent,
    onSecondary = FolioColors.OnAccent,
    background = FolioColors.Bg,
    onBackground = FolioColors.Text,
    surface = FolioColors.Bg,
    onSurface = FolioColors.Text,
    surfaceVariant = FolioColors.SurfaceHigh,
    onSurfaceVariant = FolioColors.Muted,
    surfaceContainerLowest = FolioColors.Bg,
    surfaceContainerLow = FolioColors.Surface,
    surfaceContainer = FolioColors.Surface,
    surfaceContainerHigh = FolioColors.SurfaceHigh,
    surfaceContainerHighest = FolioColors.SurfaceHighest,
    outline = FolioColors.Outline,
    outlineVariant = FolioColors.Outline,
)

private val typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Medium),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp),
    )
}

@Composable
fun FolioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
