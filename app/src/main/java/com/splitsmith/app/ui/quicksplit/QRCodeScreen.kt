package com.splitsmith.app.ui.quicksplit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.theme.LocalSplitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScreen(
    onBack: () -> Unit
) {
    val d = LocalDimens.current
    val context = LocalContext.current
    val uid = FirebaseManager.currentUserId ?: ""
    val userProfileState = FirebaseManager.observeUserProfile().collectAsState(initial = null)
    val userProfile = userProfileState.value

    val colors = LocalSplitColors.current
    val canvasChalk = colors.canvasChalk
    val inkPrimary = colors.inkPrimary
    val inkMuted = colors.inkMuted
    val borderWhisper = colors.borderWhisper

    // Generate QR bitmap when uid is loaded
    val qrBitmap = remember(uid) {
        if (uid.isNotEmpty()) {
            generateQRCodeBitmap(uid, 512)
        } else {
            null
        }
    }

    Scaffold(
        containerColor = canvasChalk,
        topBar = {
            TopAppBar(
                title = { Text("My QR Code", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = inkPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "Add me on SplitSmith! My User Code is: ${uid.take(6).uppercase()}")
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share User Code"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = inkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = canvasChalk)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = d.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(d.radiusLG),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(d.space24),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "QR Logo",
                        tint = inkPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(d.space8))
                    Text(
                        text = userProfile?.displayName ?: "SplitSmith User",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textTitleLarge,
                        color = inkPrimary
                    )
                    Text(
                        text = userProfile?.email ?: "",
                        fontFamily = OutfitFamily,
                        fontSize = d.textBodyMedium,
                        color = inkMuted
                    )

                    Spacer(modifier = Modifier.height(d.space24))

                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "My QR Code",
                            modifier = Modifier
                                .size(300.dp)
                                .background(Color.White)
                                .padding(d.space12)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(300.dp)
                                .background(borderWhisper),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = inkPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(d.space24))

                    Text(
                        text = "User Code",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = d.textLabelSmall,
                        color = inkMuted,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(d.space4))
                    Text(
                        text = uid.take(6).uppercase(),
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textTitleMedium,
                        color = inkPrimary,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(d.space8))
                    Text(
                        text = "Scan this QR code in Quick Split to instantly split bills with each other.",
                        fontFamily = OutfitFamily,
                        fontSize = d.textLabelMedium,
                        color = inkMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = d.textLabelMedium * 1.4f
                    )
                }
            }
        }
    }
}

private fun generateQRCodeBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
