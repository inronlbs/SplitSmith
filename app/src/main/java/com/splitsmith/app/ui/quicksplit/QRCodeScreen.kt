package com.splitsmith.app.ui.quicksplit

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScreen(
    onBack: () -> Unit
) {
    val d = LocalDimens.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val uid = FirebaseManager.currentUserId ?: ""
    val userProfileState = FirebaseManager.observeUserProfile().collectAsState(initial = null)
    val userProfile = userProfileState.value

    val colors = LocalSplitColors.current
    val canvasChalk = colors.canvasChalk
    val surfaceCard = colors.surfaceCard
    val inkPrimary = colors.inkPrimary
    val inkMuted = colors.inkMuted
    val borderWhisper = colors.borderWhisper

    val displayUserCode = remember(userProfile, uid) {
        when {
            !userProfile?.shortCode.isNullOrEmpty() -> userProfile.shortCode.uppercase()
            uid.isNotEmpty() -> uid.take(6).uppercase()
            else -> "------"
        }
    }

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
                title = { Text("My QR Code", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleLarge, color = inkPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = inkPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, "Add me on SplitSmith! My User Code is: $displayUserCode")
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
                colors = CardDefaults.cardColors(containerColor = surfaceCard),
                border = BorderStroke(1.dp, borderWhisper),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                        color = inkPrimary,
                        textAlign = TextAlign.Center
                    )
                    if (!userProfile?.email.isNullOrEmpty()) {
                        Text(
                            text = userProfile?.email ?: "",
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
                            color = inkMuted,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(d.space20))

                    // QR Code Image Container with high contrast white background
                    Surface(
                        modifier = Modifier.padding(d.space4),
                        shape = RoundedCornerShape(d.radiusMD),
                        color = Color.White,
                        border = BorderStroke(1.dp, borderWhisper)
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "My QR Code",
                                modifier = Modifier
                                    .size(240.dp)
                                    .padding(d.space16)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(d.space20))

                    Text(
                        text = "USER CODE",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textLabelSmall,
                        color = inkMuted,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(d.space4))
                    
                    // Interactive User Code Chip with Copy
                    Surface(
                        modifier = Modifier
                            .clickable {
                                if (displayUserCode != "------") {
                                    clipboardManager.setText(AnnotatedString(displayUserCode))
                                    Toast.makeText(context, "User code '$displayUserCode' copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(d.radiusSM),
                        color = canvasChalk,
                        border = BorderStroke(1.dp, borderWhisper)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = d.space16, vertical = d.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(d.space8)
                        ) {
                            Text(
                                text = displayUserCode,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = inkPrimary,
                                letterSpacing = 2.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy User Code",
                                tint = inkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(d.space12))
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
