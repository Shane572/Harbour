package com.harbour.reader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AccentChoice(val name: String, val color: Color, val onColor: Color)

val AccentChoices = listOf(
    AccentChoice("Amber", Color(0xFFE8B04B), Color(0xFF1C1405)),
    AccentChoice("Coral", Color(0xFFFF8A70), Color(0xFF2A0D06)),
    AccentChoice("Rose", Color(0xFFF27BA0), Color(0xFF2E0716)),
    AccentChoice("Violet", Color(0xFFB39BFF), Color(0xFF1A1033)),
    AccentChoice("Sky", Color(0xFF6FB2FF), Color(0xFF06182E)),
    AccentChoice("Teal", Color(0xFF4FC9B8), Color(0xFF032420)),
    AccentChoice("Lime", Color(0xFFB2DB6E), Color(0xFF16220A)),
    // Dark enough that text on it needs to be light rather than dark.
    AccentChoice("Crimson", Color(0xFFC4313B), Color(0xFFFFF1F0)),
)

/**
 * Harbour's colours. [accent] and [black] are Compose state, so anything drawn with these colours
 * updates as soon as they change in Settings.
 */
object HarbourColors {
    var accent by mutableStateOf(AccentChoices.first())
    var black by mutableStateOf(false)

    val Bg: Color get() = if (black) Color.Black else Color(0xFF0C0C0E)
    val Surface: Color get() = if (black) Color(0xFF0D0D0F) else Color(0xFF151518)
    val SurfaceHigh: Color get() = if (black) Color(0xFF17171B) else Color(0xFF1E1E23)
    val SurfaceHighest: Color get() = if (black) Color(0xFF222228) else Color(0xFF28282E)
    val Accent: Color get() = accent.color
    val OnAccent: Color get() = accent.onColor
    val Text = Color(0xFFEDEBE6)
    val Muted = Color(0xFF8E8C93)
    val Outline: Color get() = if (black) Color(0xFF1F1F24) else Color(0xFF2E2E34)
}

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
fun HarbourTheme(content: @Composable () -> Unit) {
    val accent = HarbourColors.accent
    val black = HarbourColors.black
    val scheme = remember(accent, black) {
        darkColorScheme(
            primary = HarbourColors.Accent,
            onPrimary = HarbourColors.OnAccent,
            secondary = HarbourColors.Accent,
            onSecondary = HarbourColors.OnAccent,
            background = HarbourColors.Bg,
            onBackground = HarbourColors.Text,
            surface = HarbourColors.Bg,
            onSurface = HarbourColors.Text,
            surfaceVariant = HarbourColors.SurfaceHigh,
            onSurfaceVariant = HarbourColors.Muted,
            surfaceContainerLowest = HarbourColors.Bg,
            surfaceContainerLow = HarbourColors.Surface,
            surfaceContainer = HarbourColors.Surface,
            surfaceContainerHigh = HarbourColors.SurfaceHigh,
            surfaceContainerHighest = HarbourColors.SurfaceHighest,
            outline = HarbourColors.Outline,
            outlineVariant = HarbourColors.Outline,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
