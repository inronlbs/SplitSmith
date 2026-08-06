package com.splitsmith.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val ExpoEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Wraps list item content with a staggered entrance animation using exponential ease-out curve.
 */
@Composable
fun AnimatedListItem(
    index: Int,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val delay = (index * 35).coerceAtMost(350)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = delay)) +
                slideInVertically(
                    initialOffsetY = { it / 5 },
                    animationSpec = tween(durationMillis = 350, delayMillis = delay, easing = ExpoEaseOut)
                )
    ) {
        content()
    }
}
