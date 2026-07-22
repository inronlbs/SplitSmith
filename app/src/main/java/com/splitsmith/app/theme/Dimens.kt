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
// Reference device: Samsung Galaxy S24 / Google Pixel 8
// Screen width: 412 dp  |  xxhdpi (1 dp ≈ 3 physical pixels)
// ─────────────────────────────────────────────────────────────
private const val BASELINE_WIDTH_DP = 412f

/**
 * All size tokens for the SplitSmith design system.
 *
 * Calculated once per composition root via [rememberDimens] and provided
 * through [LocalDimens]. Access anywhere with:
 *
 *     val d = LocalDimens.current
 *     Text(fontSize = d.textBody)
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
 * Layout scale is clamped to [0.80, 1.20] of the baseline so the
 * UI stays proportional across small budget phones and large flagships.
 * Text scale is clamped tighter ([0.85, 1.15]) because reading comfort
 * is more sensitive than layout spacing.
 */
fun buildDimens(screenWidthDp: Int): Dimens {
    val raw = screenWidthDp / BASELINE_WIDTH_DP

    // Two separate clamps: layout can flex more than text
    val ls = raw.coerceIn(0.80f, 1.20f)  // layout scale
    val ts = raw.coerceIn(0.85f, 1.15f)  // text scale

    fun Dp(base: Float) = (base * ls).dp
    fun Sp(base: Float) = (base * ts).sp

    return Dimens(
        // ── Typography ───────────────────────────────────────
        textDisplayLarge   = Sp(32f),
        textHeadlineLarge  = Sp(24f),
        textHeadlineMedium = Sp(20f),
        textTitleLarge     = Sp(18f),
        textTitleMedium    = Sp(16f),
        textBodyLarge      = Sp(15f),
        textBodyMedium     = Sp(14f),
        textLabelLarge     = Sp(13f),
        textLabelMedium    = Sp(12f),
        textLabelSmall     = Sp(11f),
        textMonoDisplay    = Sp(28f),
        textMonoLarge      = Sp(16f),
        textMonoSmall      = Sp(13f),

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
        buttonHeightSm = Dp(40f),
        rowHeightLg    = Dp(64f),
        rowHeightSm    = Dp(52f),
        inputHeight    = Dp(52f),

        // ── Navigation ───────────────────────────────────────
        navDockHeight  = Dp(68f),
        navBarHeight   = Dp(60f),
        navIconSize    = Dp(22f),
        navPillSize    = Dp(40f),
        navCenterFab   = Dp(44f),
        navDockMarginH = Dp(20f),
        navDockMarginV = Dp(10f),

        // ── Avatars ──────────────────────────────────────────
        avatarLg    = Dp(56f),
        avatarMd    = Dp(40f),
        avatarSm    = Dp(32f),
        avatarXs    = Dp(24f),
        groupIconSize = Dp(44f),

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

/** Fallback: baseline 412 dp device — replaced at composition root. */
val LocalDimens = compositionLocalOf { buildDimens(412) }

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
