package com.splitsmith.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Draws a high-end, subtle dot grid background pattern in the upper half of the screen,
 * fading out linearly down to the vertical midpoint.
 */
fun Modifier.dotGridBackground(
    dotColor: Color,
    spacing: Float = 48f,
    radius: Float = 1.5f // slightly smaller dots for even better subtlety
): Modifier = this.drawBehind {
    val halfHeight = size.height / 2f
    if (halfHeight <= 0) return@drawBehind

    var x = 24f
    while (x < size.width) {
        var y = 24f
        while (y < halfHeight) {
            val progress = (y / halfHeight).coerceIn(0f, 1f)
            // Linearly fade out opacity: max at top, 0 at midpoint
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
