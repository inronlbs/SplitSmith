package com.splitsmith.app.ui.auth

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    val webClientId = remember(context) {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            context.getString(resId)
        } else {
            "545492492856-hi7e0d7su8duvi27s8g0ob61a5pbq94p.apps.googleusercontent.com"
        }
    }

    val googleSignInClient = remember(webClientId) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply {
                if (webClientId.isNotEmpty()) {
                    requestIdToken(webClientId)
                }
            }
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
                                try {
                                    val profile = FirebaseManager.getUserProfile(uid)
                                    hasUpi = !profile?.upiId.isNullOrEmpty()
                                } catch (e: Exception) {
                                    hasUpi = false
                                }
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
                    .clip(RoundedCornerShape(20.dp))
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
            shape = RoundedCornerShape(topStart = d.radiusXL, topEnd = d.radiusXL),
            color = colors.canvasChalk,
            shadowElevation = d.cardElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(d.space24)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
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
                    text = "Smart, effortless expense splitting & personal ledger tracking.",
                    fontFamily = OutfitFamily,
                    fontSize = d.textBodyMedium,
                    color = inkMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(d.space32))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d.buttonHeight)
                        .clickable(enabled = !isLoading) {
                            isLoading = true
                            googleSignInClient.signOut().addOnCompleteListener {
                                try {
                                    launcher.launch(googleSignInClient.signInIntent)
                                } catch (e: Exception) {
                                    isLoading = false
                                    Toast.makeText(context, "Error launching sign-in: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    shape = RoundedCornerShape(d.radiusMD),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, borderWhisper),
                    shadowElevation = d.cardElevation
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = d.space16),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = inkPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(d.space12))
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

                Spacer(modifier = Modifier.height(d.space24))

                var showTermsDialog by remember { mutableStateOf(false) }

                Text(
                    text = "By continuing, you agree to SplitSmith's\nTerms & Conditions and Data Sharing Agreement",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelSmall,
                    color = inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = d.space16)
                        .clickable { showTermsDialog = true }
                )

                if (showTermsDialog) {
                    AlertDialog(
                        onDismissRequest = { showTermsDialog = false },
                        containerColor = colors.surfaceCard,
                        shape = RoundedCornerShape(d.radiusLG),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🛡️ Terms & Data Privacy", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary)
                            }
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(d.space12),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(shape = RoundedCornerShape(d.radiusSM), color = colors.canvasChalk, border = BorderStroke(0.5.dp, borderWhisper)) {
                                    Column(modifier = Modifier.padding(d.space12)) {
                                        Text("1. Terms of Service", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                                        Text("By signing in, you agree to store group expense records securely. Google Auth provides safe passwordless authentication.", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(d.radiusSM), color = colors.canvasChalk, border = BorderStroke(0.5.dp, borderWhisper)) {
                                    Column(modifier = Modifier.padding(d.space12)) {
                                        Text("2. Group Data Sharing", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                                        Text("Shared group expenses, amounts, and display names are visible exclusively to approved members of that group.", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(d.radiusSM), color = colors.canvasChalk, border = BorderStroke(0.5.dp, borderWhisper)) {
                                    Column(modifier = Modifier.padding(d.space12)) {
                                        Text("3. Private Ledger Security", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                                        Text("Your personal budgets and private expense logs are end-to-end isolated and accessible only to your account.", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showTermsDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                                shape = RoundedCornerShape(d.radiusMD)
                            ) {
                                Text("I Understand & Agree", fontFamily = OutfitFamily, color = colors.canvasChalk)
                            }
                        }
                    )
                }
            }
        }
    }
}
