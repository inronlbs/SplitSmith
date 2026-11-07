package com.splitsmith.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
// Design Baseline
// Reference device: Standard Android Compact Baseline (360 dp)
// Screen width: 360 dp | Material Design 3 and Android A11y Standards
// ─────────────────────────────────────────────────────────────
private const val BASELINE_WIDTH_DP = 360f

/**
 * All size tokens for the SplitSmith design system.
 *
 * Calculated once per composition root via [rememberDimens] and provided
 * through [LocalDimens]. Access anywhere with:
 *
 *     val d = LocalDimens.current
 *     Text(fontSize = d.textBodyLarge)
 *     Modifier.height(d.buttonHeight)
 */
data class Dimens(

    // ── Typography (sp) ──────────────────────────────────────
    /** Balance hero number  e.g. "₹2,400"  */
    val textDisplayLarge: TextUnit,
    /** Screen title  e.g. "Overview"  */
    val textHeadlineLarge: TextUnit,
    /** Sub-screen / dialog title  e.g. "Goa Trip 2024"  */
    val textHeadlineMedium: TextUnit,
    /** Card title, large label  */
    val textTitleLarge: TextUnit,
    /** Row primary label  e.g. group name  */
    val textTitleMedium: TextUnit,
    /** Expense description, content body  */
    val textBodyLarge: TextUnit,
    /** Secondary info, sub-labels  */
    val textBodyMedium: TextUnit,
    /** Button / tab label text  */
    val textLabelLarge: TextUnit,
    /** Captions, metadata, timestamps  */
    val textLabelMedium: TextUnit,
    /** UPPERCASE section headers, badge text  */
    val textLabelSmall: TextUnit,
    /** JetBrains Mono — large amount display  e.g. "₹ 0.00" input  */
    val textMonoDisplay: TextUnit,
    /** JetBrains Mono — balance amounts in rows  */
    val textMonoLarge: TextUnit,
    /** JetBrains Mono — timestamps, invite codes  */
    val textMonoSmall: TextUnit,

    // ── Spacing (dp) ─────────────────────────────────────────
    val space2: Dp,
    val space4: Dp,
    val space8: Dp,
    val space12: Dp,
    /** Standard horizontal screen margin  */
    val space16: Dp,
    val space20: Dp,
    val space24: Dp,
    val space32: Dp,
    val space48: Dp,

    // ── Corner Radius (dp) ───────────────────────────────────
    val radiusXS: Dp,   // 6  — badges, chips
    val radiusSM: Dp,   // 10 — inputs
    val radiusMD: Dp,   // 14 — buttons
    val radiusLG: Dp,   // 16 — cards
    val radiusXL: Dp,   // 24 — dock, bottom sheets
    val radiusFull: Dp, // 999 — circles, pills

    // ── Component Heights (dp) ───────────────────────────────
    /** Primary full-width CTA button  */
    val buttonHeight: Dp,
    /** Secondary / ghost button  */
    val buttonHeightSm: Dp,
    /** Standard 2-line list row  */
    val rowHeightLg: Dp,
    /** Compact 1-line settings / balance row  */
    val rowHeightSm: Dp,
    /** Input field (label rendered above, not inside)  */
    val inputHeight: Dp,

    // ── Navigation (dp) ──────────────────────────────────────
    /** Outer floating capsule dock total height  */
    val navDockHeight: Dp,
    /** Inner content height inside the dock  */
    val navBarHeight: Dp,
    /** Each icon inside the nav  */
    val navIconSize: Dp,
    /** Active icon background pill  */
    val navPillSize: Dp,
    /** Center quick-add FAB circle  */
    val navCenterFab: Dp,
    /** Horizontal margin of the floating dock from screen edge  */
    val navDockMarginH: Dp,
    /** Vertical margin of the floating dock from navigation bar  */
    val navDockMarginV: Dp,

    // ── Avatar sizes (dp) ────────────────────────────────────
    /** Profile screen hero avatar  */
    val avatarLg: Dp,
    /** Group detail member list rows  */
    val avatarMd: Dp,
    /** Expense list rows  */
    val avatarSm: Dp,
    /** Micro — inline representation  */
    val avatarXs: Dp,
    /** Group list card initial circle  */
    val groupIconSize: Dp,

    // ── Card (dp) ────────────────────────────────────────────
    val cardElevation: Dp,
    val cardPaddingH: Dp,
    val cardPaddingV: Dp,

    // ── Icon (dp) ────────────────────────────────────────────
    val iconSizeLg: Dp,   // 28 — primary hero icons
    val iconSizeMd: Dp,   // 24 — standard icon
    val iconSizeSm: Dp,   // 20 — secondary / inline icon
    val iconSizeXs: Dp,   // 16 — caption-level icon / chevron
)

// ─────────────────────────────────────────────────────────────
// Factory — compute Dimens from real screen width
// ─────────────────────────────────────────────────────────────

/**
 * Computes a [Dimens] instance scaled to [screenWidthDp].
 *
 * Layout scale allows subtle expansion for larger screens, but has a 0.95 floor
 * so spacing never collapses awkwardly on compact phones.
 * Per Material Design 3 and Android A11y Guidelines, typography should NEVER scale down
 * below 1.0f on compact phones; reading comfort must remain at comfortable scale.
 */
fun buildDimens(screenWidthDp: Int): Dimens {
    val raw = screenWidthDp / BASELINE_WIDTH_DP

    // Two separate clamps: layout can flex more than text
    val ls = raw.coerceIn(0.95f, 1.25f)  // layout scale
    val ts = raw.coerceIn(1.0f, 1.15f)   // text scale (strictly never below 1.0f)

    fun Dp(base: Float) = (base * ls).dp
    fun Sp(base: Float) = (base * ts).sp

    return Dimens(
        // ── Typography ───────────────────────────────────────
        textDisplayLarge   = Sp(34f),
        textHeadlineLarge  = Sp(26f),
        textHeadlineMedium = Sp(22f),
        textTitleLarge     = Sp(19f),
        textTitleMedium    = Sp(16.5f),
        textBodyLarge      = Sp(16f),
        textBodyMedium     = Sp(14f),
        textLabelLarge     = Sp(14f),
        textLabelMedium    = Sp(12.5f),
        textLabelSmall     = Sp(11.5f),
        textMonoDisplay    = Sp(30f),
        textMonoLarge      = Sp(16.5f),
        textMonoSmall      = Sp(13.5f),

        // ── Spacing ──────────────────────────────────────────
        space2  = Dp(2f),
        space4  = Dp(4f),
        space8  = Dp(8f),
        space12 = Dp(12f),
        space16 = Dp(16f),
        space20 = Dp(20f),
        space24 = Dp(24f),
        space32 = Dp(32f),
        space48 = Dp(48f),

        // ── Corner Radius ─────────────────────────────────────
        radiusXS   = Dp(6f),
        radiusSM   = Dp(10f),
        radiusMD   = Dp(14f),
        radiusLG   = Dp(16f),
        radiusXL   = Dp(24f),
        radiusFull = 999.dp,  // intentionally never scaled

        // ── Component Heights ─────────────────────────────────
        buttonHeight   = Dp(52f),
        buttonHeightSm = Dp(44f),
        rowHeightLg    = Dp(66f),
        rowHeightSm    = Dp(52f),
        inputHeight    = Dp(52f),

        // ── Navigation ───────────────────────────────────────
        navDockHeight  = Dp(70f),
        navBarHeight   = Dp(62f),
        navIconSize    = Dp(24f),
        navPillSize    = Dp(44f),
        navCenterFab   = Dp(50f),
        navDockMarginH = Dp(20f),
        navDockMarginV = Dp(10f),

        // ── Avatars ──────────────────────────────────────────
        avatarLg    = Dp(56f),
        avatarMd    = Dp(42f),
        avatarSm    = Dp(34f),
        avatarXs    = Dp(26f),
        groupIconSize = Dp(46f),

        // ── Card ─────────────────────────────────────────────
        cardElevation  = Dp(2f),
        cardPaddingH   = Dp(16f),
        cardPaddingV   = Dp(14f),

        // ── Icons ────────────────────────────────────────────
        iconSizeLg = Dp(28f),
        iconSizeMd = Dp(24f),
        iconSizeSm = Dp(20f),
        iconSizeXs = Dp(16f),
    )
}

// ─────────────────────────────────────────────────────────────
// CompositionLocal — access via LocalDimens.current
// ─────────────────────────────────────────────────────────────

/** Fallback: baseline 360 dp device — replaced at composition root. */
val LocalDimens = compositionLocalOf { buildDimens(360) }

/**
 * Reads the current screen width from [LocalConfiguration] and
 * returns a [Dimens] instance scaled to this device.
 * Call once inside [SplitSmithTheme] content wrapper.
 */
@Composable
@ReadOnlyComposable
fun rememberDimens(): Dimens {
    val config = LocalConfiguration.current
    return buildDimens(config.screenWidthDp)
}
