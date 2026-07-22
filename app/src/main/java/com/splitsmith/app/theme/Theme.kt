package com.splitsmith.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────
// Color schemes
// ─────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF9F9F7),
    onPrimary = Color(0xFF121212),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF9F9F7),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF9F9F7)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF121212),
    onPrimary = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    background = Color(0xFFF9F9F7),
    onBackground = Color(0xFF121212)
)

// ─────────────────────────────────────────────────────────────
// Base M3 Typography — uses Outfit family as the system font.
// Actual per-role sizes come from LocalDimens at the call site;
// these M3 defaults are a safety fallback for any component
// that reads MaterialTheme.typography directly.
// ─────────────────────────────────────────────────────────────
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = OutfitFamily, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 14.sp
    ),
)

// ─────────────────────────────────────────────────────────────
// SplitSmithTheme — wraps the whole app
//
// Usage in any composable:
//   val d = LocalDimens.current
//   Text(fontSize = d.textBodyLarge, fontFamily = OutfitFamily)
//   Modifier.height(d.buttonHeight)
// ─────────────────────────────────────────────────────────────
@Composable
fun SplitSmithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,          // disabled — we use our own palette
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Compute adaptive dimens for this device's screen width
    val dimens = rememberDimens()

    val splitColors = if (darkTheme) DarkSplitColors else LightSplitColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalDimens provides dimens,
        LocalSplitColors provides splitColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AppTypography,
            content     = content,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Dynamic Theme Switching Controller
// ─────────────────────────────────────────────────────────────
data class ThemeController(
    val isDark: Boolean = false,
    val toggleTheme: () -> Unit = {}
)

val LocalThemeController = compositionLocalOf { ThemeController() }
