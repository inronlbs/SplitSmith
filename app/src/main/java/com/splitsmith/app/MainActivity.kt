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

class MainActivity : ComponentActivity() {
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
                        MainNavigation()
                    }
                }
            }
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
                       (data.host == "invronteach.web.app" || data.host == "invronteach.firebaseapp.com") &&
                       data.path == "/join") {
                code = data.getQueryParameter("code")
            }
            if (!code.isNullOrEmpty()) {
                com.splitsmith.app.data.FirebaseManager.pendingGroupJoinCode = code
            }
        } else if (intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (uri != null) {
                com.splitsmith.app.data.FirebaseManager.sharedImageUri = uri
            }
        }
    }
}
