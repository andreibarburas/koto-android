package com.brbrs.koto.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.koto.R
import com.brbrs.koto.ui.components.ScreenBackground
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.glassCard
import com.brbrs.koto.ui.viewmodels.OnboardingViewModel

/** First-run welcome. Shown once; afterwards it hands straight off to [onContinue]. */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val isDark = LocalIsDark.current
    val done by viewModel.onboardingDone.collectAsState()

    LaunchedEffect(done) { if (done == true) onContinue() }
    if (done != false) return

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Image(
                painter = painterResource(R.drawable.koto_logo),
                contentDescription = "Koto",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.height(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your Nextcloud tasks, made for your phone.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Koto is a CalDAV client for the task lists already on your own server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OnboardingPoint(
                    Icons.Outlined.CloudOff, "Your tasks, your server",
                    "Koto talks only to your Nextcloud. No accounts, no tracking, no third-party servers.", isDark,
                )
                OnboardingPoint(
                    Icons.Outlined.Sync, "Standard CalDAV, not a silo",
                    "Due dates, priorities, tags, subtasks and notes are plain VTODO, so Nextcloud Tasks, " +
                        "Thunderbird and anything else see exactly the same thing.", isDark,
                )
                OnboardingPoint(
                    Icons.Outlined.EventRepeat, "Works offline",
                    "Tasks are kept on this device, so they open instantly. Edits you make without a " +
                        "connection are sent as soon as there is one.", isDark,
                )
                OnboardingPoint(
                    Icons.Outlined.Lock, "Open source & private",
                    "Free, GPLv3 and ad-free. Reminders are local alarms, with no push server involved.", isDark,
                )
            }

            Spacer(Modifier.height(36.dp))

            Button(
                onClick = { viewModel.markDone() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Get started", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OnboardingPoint(icon: ImageVector, title: String, body: String, isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.07f else 0.04f)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
