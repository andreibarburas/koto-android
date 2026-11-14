package com.brbrs.koto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.settings.SettingsRepository
import com.brbrs.koto.sync.WorkScheduler
import com.brbrs.koto.ui.KotoNavGraph
import com.brbrs.koto.ui.theme.DisplayPreferences
import com.brbrs.koto.ui.theme.DisplayPreferencesRepository
import com.brbrs.koto.ui.theme.KotoTheme
import com.brbrs.koto.ui.theme.ThemeRepository
import com.brbrs.koto.ui.theme.textSizeMultiplier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// FragmentActivity (not ComponentActivity) is required for BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository
    @Inject lateinit var displayPrefs: DisplayPreferencesRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var workScheduler: WorkScheduler

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* reminders just won't show until granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        lifecycleScope.launch {
            if (authRepository.currentSession() != null) {
                settingsRepository.fetchFromRemote()
                val minutes = settingsRepository.settings.first().syncIntervalMinutes
                workScheduler.scheduleSync(minutes)
                workScheduler.syncNow()
            }
        }

        val deepLinkHref = taskHrefFrom(intent)

        setContent {
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "light"  -> false
                "system" -> systemDark
                else     -> true // "dark" / "oled"
            }
            val isOled = themeMode == "oled"
            val display by displayPrefs.preferences.collectAsStateWithLifecycle(initialValue = DisplayPreferences())
            KotoTheme(
                isDark = isDark,
                isOled = isOled,
                textScale = textSizeMultiplier(display.textSize),
                useCustomFont = display.useCustomFont,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KotoNavGraph(
                        onThemeToggle = { lifecycleScope.launch { themeRepository.cycleThemeMode() } },
                        deepLinkHref = deepLinkHref,
                    )
                }
            }
        }
    }

    /** koto://task/{url-encoded href}, used by reminder notifications and other suite apps. */
    private fun taskHrefFrom(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        if (data.scheme != "koto" || data.host != "task") return null
        return data.pathSegments.firstOrNull()?.let { Uri.decode(it) }
    }
}
