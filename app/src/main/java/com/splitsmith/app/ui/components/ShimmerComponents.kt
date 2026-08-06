package com.splitsmith.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors

@Composable
fun ShimmerListItem(
    modifier: Modifier = Modifier
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShimmerAlpha"
    )

    val shimmerColor = colors.borderWhisper.copy(alpha = alpha)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = d.space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(d.avatarMd)
                .clip(CircleShape)
                .background(shimmerColor)
        )
        Spacer(modifier = Modifier.width(d.space12))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
        }
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerColor)
        )
    }
}
