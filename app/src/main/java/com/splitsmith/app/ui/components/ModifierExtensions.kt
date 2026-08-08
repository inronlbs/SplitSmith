package com.splitsmith.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Draws a high-end, minimal micro-dot grid background pattern,
 * restricted strictly to the top 28% header area and fading out smoothly to 0 opacity.
 */
fun Modifier.dotGridBackground(
    dotColor: Color,
    spacing: Float = 56f,
    radius: Float = 1.25f,
    maxFadeHeightFraction: Float = 0.28f
): Modifier = this.drawBehind {
    val maxDotY = size.height * maxFadeHeightFraction
    var x = 28f
    while (x < size.width) {
        var y = 28f
        while (y < maxDotY) {
            val progress = (y / maxDotY).coerceIn(0f, 1f)
            // Quadratic ease-out fade from 1.0 at top down to 0.0 at maxDotY
            val alphaFactor = (1f - progress) * (1f - progress)
            val finalAlpha = dotColor.alpha * alphaFactor * 0.35f

            if (finalAlpha > 0.01f) {
                drawCircle(
                    color = dotColor.copy(alpha = finalAlpha),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
            y += spacing
        }
        x += spacing
    }
}

/**
 * Draws an off-axis radial mesh gradient for warm subtle depth on primary dashboards.
 */
fun Modifier.meshGradientBackground(accentColor: Color): Modifier = this.drawBehind {
    val brush = Brush.radialGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.05f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.15f, size.height * 0.06f),
        radius = size.width * 0.85f
    )
    drawRect(brush)
}
