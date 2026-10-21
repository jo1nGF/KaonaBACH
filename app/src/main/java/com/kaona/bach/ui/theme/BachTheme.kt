package com.kaona.bach.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class BachColorScheme(
    val background: Color = Color(0xFF0A0A0A),
    val surface: Color = Color(0xFF1A1A1A),
    val surfaceVariant: Color = Color(0xFF252525),
    val panel: Color = Color(0xFF151515),
    val accent: Color = Color(0xFFD4AF37),
    val accentDim: Color = Color(0xFF8B7422),
    val knobSurface: Color = Color(0xFF2A2A2A),
    val knobHighlight: Color = Color(0xFF3A3A3A),
    val textPrimary: Color = Color(0xFFE0E0E0),
    val textSecondary: Color = Color(0xFF888888),
    val textLabel: Color = Color(0xFFAAAAAA),
    val displayBg: Color = Color(0xFF0D1B2A),
    val displayText: Color = Color(0xFF88CCFF),
    val displayAccent: Color = Color(0xFF4488FF),
    val ledRed: Color = Color(0xFFFF3333),
    val ledGreen: Color = Color(0xFF33FF33),
    val ledBlue: Color = Color(0xFF3366FF),
    val ledYellow: Color = Color(0xFFFFCC00),
    val ledWhite: Color = Color(0xFFEEEEEE),
    val ledPink: Color = Color(0xFFFF88AA),
    val gate: Color = Color(0xFF44FF44),
    val mod: Color = Color(0xFF4488FF),
    val pitch: Color = Color(0xFFFF8844),
    val buttonPlay: Color = Color(0xFFFFCCDD),
    val buttonBreak: Color = Color(0xFFFFDD88),
    val transport: Color = Color(0xFFEEEEEE)
)

val LocalBachColors = staticCompositionLocalOf { BachColorScheme() }

object BachTheme {
    val colors: BachColorScheme
        @Composable
        get() = LocalBachColors.current
}

@Composable
fun BachTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBachColors provides BachColorScheme()
    ) {
        content()
    }
}
