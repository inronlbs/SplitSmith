package com.splitsmith.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Draws a high-end, subtle dot grid background pattern,
 * fading out linearly down over 75% of the screen height.
 */
fun Modifier.dotGridBackground(
    dotColor: Color,
    spacing: Float = 48f,
    radius: Float = 1.5f
): Modifier = this.drawBehind {
    val maxFadeHeight = size.height * 0.75f
    if (maxFadeHeight <= 0) return@drawBehind

    var x = 24f
    while (x < size.width) {
        var y = 24f
        while (y < maxFadeHeight) {
            val progress = (y / maxFadeHeight).coerceIn(0f, 1f)
            val alphaFactor = (1f - progress) * 0.65f
            val finalAlpha = dotColor.alpha * alphaFactor

            drawCircle(
                color = dotColor.copy(alpha = finalAlpha),
                radius = radius,
                center = Offset(x, y)
            )
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
