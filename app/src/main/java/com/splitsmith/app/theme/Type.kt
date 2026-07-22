package com.splitsmith.app.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

// ─────────────────────────────────────────────────────────────
// Google Fonts provider (uses GMS — already bundled on device)
// ─────────────────────────────────────────────────────────────
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = com.splitsmith.app.R.array.com_google_android_gms_fonts_certs
)

// ── Outfit — primary UI typeface ──────────────────────────────
val OutfitFont = GoogleFont("Outfit")
val OutfitFamily = FontFamily(
    Font(googleFont = OutfitFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = OutfitFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = OutfitFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = OutfitFont, fontProvider = provider, weight = FontWeight.Bold),
)

// ── JetBrains Mono — numeric / monetary values ───────────────
val JetBrainsMonoFont = GoogleFont("JetBrains Mono")
val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = JetBrainsMonoFont, fontProvider = provider, weight = FontWeight.Bold),
)

// ─────────────────────────────────────────────────────────────
// NOTE: Actual TextStyle sizes are intentionally NOT defined here.
// Font sizes are computed dynamically per device in Dimens.kt and
// accessed via LocalDimens.current.textXxx at the call site.
//
// MaterialTheme.typography is kept with Outfit as the font family
// but uses M3 default sizing; all SplitSmith-specific sizing comes
// from LocalDimens.
// ─────────────────────────────────────────────────────────────
