package com.splitsmith.app.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ─── HIGH-END MONOCHROME & TEXTURED COLOR SYSTEM ──────────────
data class SplitColors(
    val canvasChalk: Color,
    val inkPrimary: Color,
    val inkMuted: Color,
    val borderWhisper: Color,
    val surfaceCard: Color,
    val dotColor: Color,
    val positiveGreen: Color,
    val alertRed: Color
)

val LightSplitColors = SplitColors(
    canvasChalk = Color(0xFFF9F9F7),
    inkPrimary = Color(0xFF121212),
    inkMuted = Color(0xFF6A6A66),
    borderWhisper = Color(0xFFE5E5E2),
    surfaceCard = Color(0xFFFFFFFF),
    dotColor = Color(0xFFCFCFCC),  // slightly darker than border for visible dots
    positiveGreen = Color(0xFF16A34A),
    alertRed = Color(0xFFDC2626)
)

val DarkSplitColors = SplitColors(
    canvasChalk = Color(0xFF121212),
    inkPrimary = Color(0xFFF9F9F7),
    inkMuted = Color(0xFF9E9E96),
    borderWhisper = Color(0xFF2C2C2E),
    surfaceCard = Color(0xFF1C1C1E),
    dotColor = Color(0xFF3D3D5C),  // indigo-tinted so dots show on dark bg
    positiveGreen = Color(0xFF4ADE80),
    alertRed = Color(0xFFF87171)
)

val LocalSplitColors = staticCompositionLocalOf { LightSplitColors }
