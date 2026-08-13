package com.splitsmith.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Draws a high-end micro-dot grid background pattern with a calm, non-distracting
 * floating wave animation and a smooth, gentle fade towards the bottom of the viewport.
 */
@Composable
fun Modifier.dotGridBackground(
    dotColor: Color,
    dotRadiusDp: Dp = 1.5.dp,
    spacingDp: Dp = 28.dp
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "dotGridWaveAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseAnimation"
    )

    val density = LocalDensity.current
    return this.drawBehind {
        val radiusPx = with(density) { dotRadiusDp.toPx() }
        val spacingPx = with(density) { spacingDp.toPx() }
        val waveAmplitudePx = with(density) { 2.5.dp.toPx() }
        val startOffset = spacingPx / 2f

        var x = startOffset
        var colIndex = 0
        while (x < size.width + spacingPx) {
            var y = startOffset
            var rowIndex = 0
            while (y < size.height + spacingPx) {
                val progressY = (y / size.height).coerceIn(0f, 1f)
                // Quadratic ease-out opacity dropoff from top to bottom
                val fadeFactor = (1f - progressY * 0.75f) * (1f - progressY * 0.75f)
                val baseAlpha = fadeFactor * 0.38f

                // Ultra-slow, calm sine wave displacement (vertical float)
                val waveOffset = sin(phase + colIndex * 0.25f + rowIndex * 0.15f) * waveAmplitudePx
                val animAlpha = (baseAlpha * (0.8f + 0.2f * sin(phase + colIndex * 0.3f))).coerceIn(0.01f, 0.5f)

                val finalAlpha = dotColor.alpha * animAlpha

                if (finalAlpha > 0.01f) {
                    drawCircle(
                        color = dotColor.copy(alpha = finalAlpha),
                        radius = radiusPx,
                        center = Offset(x, y + waveOffset)
                    )
                }
                y += spacingPx
                rowIndex++
            }
            x += spacingPx
            colIndex++
        }
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
