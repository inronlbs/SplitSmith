package com.splitsmith.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.splitsmith.app.theme.LocalThemeController
import com.splitsmith.app.theme.SplitSmithTheme
import com.splitsmith.app.theme.ThemeController

import com.splitsmith.app.ui.components.BiometricLockOverlay

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var isAppLocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Parse incoming intent if launching
        handleIncomingIntent(intent)

        // Initialize edge-to-edge system transparently
        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            val prefs = remember { getSharedPreferences("splitsmith_prefs", MODE_PRIVATE) }
            var isDark by remember { mutableStateOf(prefs.getBoolean("dark_theme", systemDark)) }

            val themeController = remember(isDark) {
                ThemeController(
                    isDark = isDark,
                    toggleTheme = {
                        isDark = !isDark
                        prefs.edit().putBoolean("dark_theme", isDark).apply()
                    }
                )
            }

            CompositionLocalProvider(LocalThemeController provides themeController) {
                SplitSmithTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (isAppLocked) {
                            BiometricLockOverlay(
                                onUnlockClick = { triggerBiometricPrompt() }
                            )
                        } else {
                            MainNavigation()
                        }
                    }
                }
            }
        }
    }

    companion object {
        var isSystemPickerActive = false
        var lastBackgroundTimestamp: Long = 0L
        var isSessionUnlocked = false
    }

    override fun onPause() {
        super.onPause()
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("splitsmith_prefs", MODE_PRIVATE)
        val isBiometricEnabled = prefs.getBoolean("key_biometric_enabled", false)
        val elapsed = System.currentTimeMillis() - lastBackgroundTimestamp

        if (isBiometricEnabled && !isSystemPickerActive && (!isSessionUnlocked || elapsed >= 30_000L)) {
            isAppLocked = true
            triggerBiometricPrompt()
        } else {
            isAppLocked = false
        }
        isSystemPickerActive = false
    }

    fun triggerBiometricPrompt() {
        try {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
            val biometricPrompt = androidx.biometric.BiometricPrompt(
                this,
                executor,
                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        isAppLocked = false
                        isSessionUnlocked = true
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                    }
                }
            )

            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("SplitSmith Locked")
                .setSubtitle("Authenticate to access your expenses")
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            isAppLocked = false
            isSessionUnlocked = true
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data ?: return
            var code: String? = null
            if (data.scheme == "splitsmith" && data.host == "join") {
                code = data.getQueryParameter("code")
            } else if ((data.scheme == "http" || data.scheme == "https") && 
                       (data.host == "splitsmith.web.app" || data.host == "splitsmith.firebaseapp.com" || data.host == "invronteach.web.app" || data.host == "invronteach.firebaseapp.com") &&
                       data.path == "/join") {
                code = data.getQueryParameter("code")
            }
            if (!code.isNullOrEmpty()) {
                com.splitsmith.app.data.FirebaseManager.pendingGroupJoinCode = code
            }
            intent.action = null
        } else if (intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (uri != null) {
                com.splitsmith.app.data.FirebaseManager.sharedImageUri = uri
            }
            intent.action = null // Clear to prevent re-processing on activity recreate
        }
    }
}
