package com.brbrs.koto.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.brbrs.koto.R
import com.brbrs.koto.di.APP_VERSION
import com.brbrs.koto.ui.components.ConfirmDialog
import com.brbrs.koto.ui.components.ScreenBackground
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.glassCard
import com.brbrs.koto.ui.theme.kotoChip
import com.brbrs.koto.ui.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.refreshPermissions()
        if (grants.values.any { it }) viewModel.setLocationRemindersEnabled(true)
    }
    // A permission can be changed in system settings while Koto sits in the background,
    // so re-read on the way back in rather than trusting what was true when the screen opened.
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var confirmClear by remember { mutableStateOf(false) }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LaunchedEffect(uiState.loggedOut) { if (uiState.loggedOut) onLoggedOut() }
    LaunchedEffect(uiState.message) { uiState.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Koto forgets this server and removes the offline copy of your tasks from this device. " +
                "Everything stays on your Nextcloud.",
            confirmText = "Sign out",
            onDismiss = { confirmLogout = false },
            onConfirm = { confirmLogout = false; viewModel.logout() },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear offline copy?",
            body = "Tasks are removed from this device and downloaded again from Nextcloud. Nothing on the server changes. " +
                "Any edit still waiting to sync would be lost, so do this only when everything is up to date.",
            confirmText = "Clear",
            destructive = false,
            onDismiss = { confirmClear = false },
            onConfirm = { confirmClear = false; viewModel.clearCache() },
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(24.dp))

            // -- Account
            SettingsSectionLabel("Account")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Connected to", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(uiState.serverUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(uiState.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // -- Security
            SettingsSectionLabel("Security")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                SettingsToggleRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "App lock",
                    subtitle = if (uiState.biometricAvailable) "Require biometrics or your screen lock when opening Koto"
                               else "Set up a fingerprint, face unlock or screen lock on this device first",
                    checked = uiState.appLockEnabled,
                    enabled = uiState.biometricAvailable || uiState.appLockEnabled,
                    onCheckedChange = viewModel::setAppLock,
                )
            }

            Spacer(Modifier.height(24.dp))

            // -- Display
            SettingsSectionLabel("Display")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.DarkMode,
                    title = "Theme",
                    options = listOf("system" to "System", "light" to "Light", "dark" to "Dark", "oled" to "OLED Black"),
                    selected = uiState.themeMode,
                    isDark = isDark,
                    onSelect = viewModel::setThemeMode,
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.TextFields,
                    title = "Text size",
                    options = listOf("small" to "Small", "default" to "Default", "large" to "Large", "extra_large" to "Extra Large"),
                    selected = uiState.textSize,
                    isDark = isDark,
                    onSelect = viewModel::setTextSize,
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                SettingsToggleRow(
                    icon = Icons.Outlined.FontDownload,
                    title = "DM Serif + Inter Tight",
                    subtitle = "Use the custom font pairing instead of system fonts",
                    checked = uiState.useCustomFont,
                    onCheckedChange = viewModel::setUseCustomFont,
                )
            }

            Spacer(Modifier.height(24.dp))

            // -- Tasks
            SettingsSectionLabel("Tasks")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    title = "Sort within a group",
                    subtitle = "Manual lets you drag tasks into place; the order is stored on the task and shows up in Nextcloud Tasks too",
                    options = listOf("due" to "Due", "priority" to "Priority", "alpha" to "A to Z", "manual" to "Manual"),
                    selected = uiState.sortMode,
                    isDark = isDark,
                    onSelect = viewModel::setSortMode,
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                SettingsToggleRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Smart quick add",
                    subtitle = "Read \"tomorrow\", \"friday 17:30\", \"!1\" and \"#tag\" out of what you type",
                    checked = uiState.smartQuickAdd,
                    onCheckedChange = viewModel::setSmartQuickAdd,
                )
            }

            Spacer(Modifier.height(24.dp))

            // -- Sync & reminders
            SettingsSectionLabel("Sync & reminders")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                ChipGroup(
                    icon = Icons.Outlined.CloudSync,
                    title = "Background sync",
                    options = listOf("15" to "15 min", "30" to "30 min", "60" to "1 hour", "180" to "3 hours"),
                    selected = uiState.syncIntervalMinutes.toString(),
                    isDark = isDark,
                    onSelect = { viewModel.setSyncIntervalMinutes(it.toInt()) },
                )
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Reminders",
                        subtitle = if (uiState.notificationsPermitted) "A local notification before a task is due"
                                   else "Notifications are off for Koto in your phone's settings",
                        checked = uiState.remindersEnabled,
                        enabled = uiState.notificationsPermitted,
                        onCheckedChange = viewModel::setRemindersEnabled,
                    )
                    if (uiState.remindersEnabled && uiState.notificationsPermitted) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Remind me", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Only used for tasks with no reminder of their own. All-day tasks fire at 09:00.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "On time", 60 to "1 hour", 1440 to "1 day").forEach { (minutes, label) ->
                                    val isSelected = minutes in uiState.reminderOffsetsMinutes
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .kotoChip(isDark = isDark, selected = isSelected)
                                            .clickable { viewModel.toggleReminderOffset(minutes) }
                                            .padding(vertical = 10.dp, horizontal = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsToggleRow(
                        icon = Icons.Outlined.LocationOn,
                        title = "Location reminders",
                        subtitle = "Notify when you arrive somewhere a task points at. Koto uses the " +
                            "built-in Android location service, not Google Play Services",
                        checked = uiState.locationRemindersEnabled,
                        onCheckedChange = { wanted ->
                            if (wanted && !uiState.hasLocationPermission) locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            ) else viewModel.setLocationRemindersEnabled(wanted)
                        },
                    )
                    if (uiState.locationRemindersEnabled && uiState.hasLocationPermission &&
                        !uiState.hasBackgroundLocationPermission
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ActionRow(
                            icon = Icons.Outlined.LocationOn,
                            title = "Allow location all the time",
                            subtitle = "Arrivals are only noticed while Koto is open until this is granted. " +
                                "Choose \"Allow all the time\" on the Location permission",
                            action = "Grant",
                            onAction = {
                                // Android 11+ refuses to show a background-location dialog from the
                                // app at all; it has to be chosen on the app's own settings page.
                                // Below that, a direct request still works.
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(Uri.parse("package:${context.packageName}"))
                                        )
                                    }
                                } else {
                                    backgroundPermissionLauncher.launch(
                                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                    )
                                }
                            },
                        )
                    }
                    if (uiState.locationRemindersEnabled && uiState.hasBackgroundLocationPermission) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            "Android decides when to deliver these, so an arrival can be a few minutes " +
                                "late. Koto also checks your last known position every 20 minutes as a " +
                                "backstop, which costs no GPS.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    if (uiState.needsExactAlarmPermission) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        ActionRow(
                            icon = Icons.Outlined.Alarm,
                            title = "Precise reminders",
                            subtitle = "Allow alarms so reminders arrive exactly on time",
                            action = "Allow",
                            onAction = {
                                runCatching {
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            .setData(Uri.parse("package:${context.packageName}")),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // -- Data
            SettingsSectionLabel("Data")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    ActionRow(
                        icon = Icons.Outlined.CloudSync,
                        title = "Sync now",
                        subtitle = "Push anything waiting, then pull the latest from Nextcloud",
                        busy = uiState.isSyncing,
                        action = "Sync",
                        onAction = viewModel::syncNow,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    ActionRow(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Clear offline copy",
                        subtitle = "Remove tasks stored on this device. Nextcloud is untouched",
                        action = "Clear",
                        onAction = { confirmClear = true },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // -- Support
            SettingsSectionLabel("Support")
            Spacer(Modifier.height(8.dp))
            SettingsCard(isDark) {
                Column {
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.Favorite),
                        title = "Buy me a coffee",
                        subtitle = "If Koto keeps you on top of things, a small tip means a lot",
                    ) { open("https://bunq.me/barburasdonations?description=Donation%20from%20Koto") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.Language),
                        title = "More by andrei BARBURAS",
                        subtitle = "See what else I'm building at barburas.com",
                    ) { open("https://barburas.com") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.PrivacyTip),
                        title = "Privacy Policy",
                        subtitle = "How Koto handles your data",
                    ) { open("https://barburas.com/privacy-policy/") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = rememberVectorPainter(Icons.Outlined.Code),
                        title = "View on GitHub",
                        subtitle = "Feedback, feature requests and source code",
                    ) { open("https://github.com/andreibarburas/android-apps") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    LinkRow(
                        painter = painterResource(R.drawable.si_reddit),
                        title = "Join r/BarburasLab",
                        subtitle = "Discuss Koto and my other apps on Reddit",
                    ) { open("https://www.reddit.com/r/BarburasLab/") }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "Koto $APP_VERSION · by andrei BARBURAS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SettingsCard(isDark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 16.dp, tint = if (isDark) Color.White else Color.Black, bgAlpha = if (isDark) 0.07f else 0.04f),
    ) { content() }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = MaterialTheme.colorScheme.primary,
                checkedTrackColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun ChipGroup(
    icon: ImageVector,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    subtitle: String? = null,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSelected = selected == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .kotoChip(isDark = isDark, selected = isSelected)
                        .clickable { onSelect(value) }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    busy: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun LinkRow(painter: Painter, title: String, subtitle: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(painter = painter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
