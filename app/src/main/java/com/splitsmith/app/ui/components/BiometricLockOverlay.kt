package com.splitsmith.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.R
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily

@Composable
fun BiometricLockOverlay(
    onUnlockClick: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.canvasChalk
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = d.space32, vertical = d.space48),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(d.space32))

            // App Logo & Lock Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(d.space16)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "SplitSmith Lock",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SplitSmith Locked",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "Authenticate to access your expenses",
                        fontFamily = OutfitFamily,
                        fontSize = d.textBodyMedium,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Unlock Action Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(d.space16)
            ) {
                Button(
                    onClick = onUnlockClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d.buttonHeight),
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.inkPrimary,
                        contentColor = colors.canvasChalk
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Unlock Biometrics",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Unlock with Biometrics",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textBodyLarge
                        )
                    }
                }
            }
        }
    }
}
