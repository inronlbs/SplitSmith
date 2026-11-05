package com.splitsmith.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.splitsmith.app.theme.LocalThemeController

@Composable
fun GroupIconView(
    iconName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    imageUrl: String = ""
) {
    val isDark = LocalThemeController.current.isDark
    val bgColor = if (isDark) Color.White else Color.Black
    val iconColor = if (isDark) Color.Black else Color.White

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            if (imageUrl.startsWith("http")) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = iconName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (imageUrl.startsWith("data:image")) {
                val bitmap = remember(imageUrl) {
                    try {
                        val base64Data = imageUrl.substringAfter("base64,")
                        val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Group Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    FallbackGroupVectorIcon(iconName = iconName, iconColor = iconColor, size = size)
                }
            } else {
                FallbackGroupVectorIcon(iconName = iconName, iconColor = iconColor, size = size)
            }
        } else {
            FallbackGroupVectorIcon(iconName = iconName, iconColor = iconColor, size = size)
        }
    }
}

@Composable
private fun FallbackGroupVectorIcon(iconName: String, iconColor: Color, size: Dp) {
    val icon = when (iconName) {
        "Home" -> Icons.Default.Home
        "Trip" -> Icons.Default.Flight
        "Work" -> Icons.Default.Work
        "Event" -> Icons.Default.Event
        "Food" -> Icons.Default.LocalPizza
        "Payment" -> Icons.Default.CreditCard
        "Shopping" -> Icons.Default.ShoppingCart
        "Dining" -> Icons.Default.Restaurant
        "Drinks" -> Icons.Default.LocalBar
        "Pets" -> Icons.Default.Pets
        "Education" -> Icons.Default.Book
        "Tech" -> Icons.Default.VideogameAsset
        else -> Icons.Default.Category
    }

    Icon(
        imageVector = icon,
        contentDescription = iconName,
        tint = iconColor,
        modifier = Modifier.size(size * 0.5f)
    )
}
