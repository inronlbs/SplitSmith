package com.splitsmith.app.ui.onboarding

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.R
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import kotlinx.coroutines.launch

fun Modifier.dotGridBackground(dotColor: Color): Modifier = this.drawBehind {
    val dotRadius = 1.dp.toPx()
    val spacing = 20.dp.toPx()
    
    var x = spacing / 2
    while (x < size.width) {
        var y = spacing / 2
        while (y < size.height) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
            y += spacing
        }
        x += spacing
    }
}

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit
) {
    val d = LocalDimens.current
    var upiId by remember { mutableStateOf("") }
    var monthlyBudgetLimit by remember { mutableStateOf("15000") }
    var budgetThreshold by remember { mutableStateOf(80f) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val colors = LocalSplitColors.current
    val canvasChalk = colors.canvasChalk
    val inkPrimary = colors.inkPrimary
    val inkMuted = colors.inkMuted
    val borderWhisper = colors.borderWhisper

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasChalk)
            .dotGridBackground(inkMuted.copy(alpha = 0.05f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = d.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(d.space32))

        // Top App Icon
        Image(
            painter = painterResource(id = R.drawable.app_logo_brand),
            contentDescription = "SplitSmith App Logo",
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(d.space16))

        // Headline & Description
        Text(
            text = "Setup your account",
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.Bold,
            fontSize = d.textHeadlineMedium,
            color = inkPrimary,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(d.space4))

        Text(
            text = "Add your payment details to start sharing expense ledgers.",
            fontFamily = OutfitFamily,
            fontSize = d.textBodyMedium,
            color = inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = d.space16)
        )

        Spacer(modifier = Modifier.height(d.space24))

        // Form Card (Bento Style)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(d.radiusLG),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, borderWhisper),
            shadowElevation = d.cardElevation
        ) {
            Column(
                modifier = Modifier.padding(d.space20),
                verticalArrangement = Arrangement.spacedBy(d.space16)
            ) {
                // UPI ID
                Column {
                    Text(
                        text = "UPI ID or Phone number",
                        fontFamily = OutfitFamily,
                        fontSize = d.textLabelMedium,
                        color = inkMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                        shape = RoundedCornerShape(d.radiusSM),
                        placeholder = {
                            Text("name@okhdfcbank", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = inkMuted)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = inkPrimary,
                            unfocusedBorderColor = borderWhisper,
                            focusedTextColor = inkPrimary,
                            unfocusedTextColor = inkPrimary,
                            focusedContainerColor = canvasChalk,
                            unfocusedContainerColor = canvasChalk
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = inkPrimary)
                    )
                }

                // Monthly Limit
                Column {
                    Text(
                        text = "Monthly limit (₹ INR)",
                        fontFamily = OutfitFamily,
                        fontSize = d.textLabelMedium,
                        color = inkMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = monthlyBudgetLimit,
                        onValueChange = { monthlyBudgetLimit = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                        shape = RoundedCornerShape(d.radiusSM),
                        placeholder = {
                            Text("15000", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = inkMuted)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = inkPrimary,
                            unfocusedBorderColor = borderWhisper,
                            focusedTextColor = inkPrimary,
                            unfocusedTextColor = inkPrimary,
                            focusedContainerColor = canvasChalk,
                            unfocusedContainerColor = canvasChalk
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = inkPrimary)
                    )
                }

                // Threshold Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alert threshold",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = inkMuted
                        )
                        Text(
                            text = "${budgetThreshold.toInt()}%",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textLabelMedium,
                            color = inkPrimary
                        )
                    }
                    Slider(
                        value = budgetThreshold,
                        onValueChange = { budgetThreshold = it },
                        valueRange = 50f..95f,
                        colors = SliderDefaults.colors(
                            thumbColor = inkPrimary,
                            activeTrackColor = inkPrimary,
                            inactiveTrackColor = borderWhisper
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Actions Container
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(d.space12)
        ) {
            Button(
                onClick = {
                    if (upiId.trim().isEmpty()) {
                        Toast.makeText(context, "UPI ID is required to receive splits", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            FirebaseManager.updateUpiId(upiId.trim())
                            Toast.makeText(context, "Setup completed!", Toast.LENGTH_SHORT).show()
                            onOnboardingComplete()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                shape = RoundedCornerShape(d.radiusMD),
                colors = ButtonDefaults.buttonColors(containerColor = inkPrimary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = canvasChalk, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "Continue",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textLabelLarge,
                        color = canvasChalk
                    )
                }
            }

            TextButton(
                onClick = onOnboardingComplete,
                modifier = Modifier.align(Alignment.CenterHorizontally).height(d.buttonHeightSm),
                enabled = !isLoading
            ) {
                Text(
                    text = "Skip for now",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelLarge,
                    color = inkMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(d.space24))
    }
}
