package com.splitsmith.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.splitsmith.app.R

@Composable
fun UserAvatar(
    avatarUrl: String,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.startsWith("http")) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else if (avatarUrl.startsWith("data:image")) {
            val bitmap = remember(avatarUrl) {
                try {
                    val base64Data = avatarUrl.substringAfter("base64,")
                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "User Custom Avatar",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                FallbackDefaultAvatar(displayName)
            }
        } else if (avatarUrl.startsWith("avatar_")) {
            val idx = avatarUrl.removePrefix("avatar_").toIntOrNull() ?: 1
            val resId = when (idx) {
                1 -> R.drawable.avatar_1
                2 -> R.drawable.avatar_2
                3 -> R.drawable.avatar_3
                4 -> R.drawable.avatar_4
                5 -> R.drawable.avatar_5
                6 -> R.drawable.avatar_6
                7 -> R.drawable.avatar_7
                else -> R.drawable.avatar_8
            }
            Image(
                painter = painterResource(id = resId),
                contentDescription = "Superhero Avatar",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            FallbackDefaultAvatar(displayName)
        }
    }
}

@Composable
private fun FallbackDefaultAvatar(displayName: String) {
    val avatarIdx = Math.abs(displayName.hashCode()) % 8 + 1
    val resId = when (avatarIdx) {
        1 -> R.drawable.avatar_1
        2 -> R.drawable.avatar_2
        3 -> R.drawable.avatar_3
        4 -> R.drawable.avatar_4
        5 -> R.drawable.avatar_5
        6 -> R.drawable.avatar_6
        7 -> R.drawable.avatar_7
        else -> R.drawable.avatar_8
    }
    Image(
        painter = painterResource(id = resId),
        contentDescription = "Default Adventurer Avatar",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
