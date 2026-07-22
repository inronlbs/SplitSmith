package com.splitsmith.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.splitsmith.app.theme.LocalThemeController

@Composable
fun GroupIconView(
    iconName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val isDark = LocalThemeController.current.isDark
    val bgColor = if (isDark) Color.White else Color.Black
    val iconColor = if (isDark) Color.Black else Color.White

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

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconName,
            tint = iconColor,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
