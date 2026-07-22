package com.splitsmith.app.ui.auth

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.R
import com.splitsmith.app.theme.LocalSplitColors
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.OutfitFamily
import kotlinx.coroutines.launch

// Custom extension modifier to draw the premium dot grid pattern
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
fun AuthScreen(
    onAuthSuccess: (isNewUser: Boolean) -> Unit
) {
    val d = LocalDimens.current
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Colors ────────────────────────────────────────────────
    val colors = LocalSplitColors.current
    val accentIndigo    = Color(0xFF2C2C2E) // Top gradient starts at dark charcoal
    val deepIndigo      = Color(0xFF121212) // Top gradient ends at Pitch Black
    val inkPrimary      = colors.inkPrimary
    val inkMuted        = colors.inkMuted
    val borderWhisper   = colors.borderWhisper

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("538688889606-vg27dc4pnvu0l8uf63qidtt2nm44nlu1.apps.googleusercontent.com")
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val authResult = FirebaseManager.signInWithGoogleCredential(idToken)
                            val isNewUser = authResult.additionalUserInfo?.isNewUser == true
                            val uid = authResult.user?.uid
                            var hasUpi = false
                            if (uid != null) {
                                val profile = FirebaseManager.getUserProfile(uid)
                                hasUpi = !profile?.upiId.isNullOrEmpty()
                            }
                            onAuthSuccess(isNewUser || !hasUpi)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                            isLoading = false
                        }
                    }
                } else {
                    isLoading = false
                    Toast.makeText(context, "Google sign-in token was null", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                isLoading = false
                Toast.makeText(context, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(accentIndigo, deepIndigo)
                )
            )
            .dotGridBackground(Color.White.copy(alpha = 0.12f))
    ) {
        // Top branding section (Centered logo and app title above bottom card)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
                .statusBarsPadding()
                .padding(horizontal = d.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo_brand),
                contentDescription = "SplitSmith App Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(d.space16))
            Text(
                text = "SplitSmith",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )
        }

        // Bottom clean card section (Wraps content height naturally)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, borderWhisper),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .dotGridBackground(colors.inkMuted.copy(alpha = 0.05f))
                    .padding(horizontal = d.space24, vertical = d.space32)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Welcome to SplitSmith",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textTitleLarge,
                    color = inkPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(d.space8))

                Text(
                    text = "Settle balances and split group expenses seamlessly.",
                    fontFamily = OutfitFamily,
                    fontSize = d.textBodyMedium,
                    color = inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = d.space8)
                )

                Spacer(modifier = Modifier.height(d.space32))

                // Standard Google Sign-In button matching Google Branding Guidelines
                Surface(
                    modifier = Modifier
                        .width(268.dp)
                        .height(d.buttonHeight),
                    shape = RoundedCornerShape(d.radiusMD),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, borderWhisper),
                    shadowElevation = d.cardElevation,
                    enabled = !isLoading,
                    onClick = {
                        isLoading = true
                        googleSignInClient.signOut().addOnCompleteListener {
                            try {
                                launcher.launch(googleSignInClient.signInIntent)
                            } catch (e: Exception) {
                                isLoading = false
                                Toast.makeText(context, "Error launching sign-in: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Official Multi-Colored Google Logo Box on the left
                        Box(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(d.radiusSM))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Centered text
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = inkPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Continue with Google",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = d.textLabelLarge,
                                    color = inkPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
