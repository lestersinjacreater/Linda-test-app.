package com.linda.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.linda.app.core.ui.theme.LindaTheme
import com.linda.app.core.util.Prefs
import com.linda.app.features.alerts.AlertNotifier
import com.linda.app.features.onboarding.OnboardingScreen

class MainActivity : ComponentActivity() {
    // Set from the launching intent: a tapped warning, or text shared in from another app.
    private var openDetectionId by mutableStateOf<Long?>(null)
    private var sharedText by mutableStateOf<String?>(null)

    /** Wrap the context so every screen uses the language chosen in Settings (English or Kiswahili). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Prefs.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)
        // Draw behind the status and navigation bars, with light icons on our dark background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LindaTheme {
                var onboarded by remember { mutableStateOf(Prefs.onboarded(this)) }
                if (!onboarded) {
                    OnboardingScreen(onDone = { Prefs.setOnboarded(this); onboarded = true })
                } else {
                    LindaNavHost(openDetectionId = openDetectionId, sharedText = sharedText)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        openDetectionId = intent?.getLongExtra(AlertNotifier.EXTRA_DETECTION_ID, -1L)?.takeIf { it >= 0 }
        sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
    }
}
