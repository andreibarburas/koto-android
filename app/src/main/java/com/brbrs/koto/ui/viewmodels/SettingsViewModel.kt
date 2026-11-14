package com.brbrs.koto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.biometric.BiometricHelper
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.location.GeofenceManager
import com.brbrs.koto.reminders.NotificationHelper
import com.brbrs.koto.reminders.ReminderScheduler
import com.brbrs.koto.settings.SettingsRepository
import com.brbrs.koto.sync.WorkScheduler
import com.brbrs.koto.ui.components.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val appLockEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val themeMode: String = "system",
    val textSize: String = "default",
    val useCustomFont: Boolean = true,
    val syncIntervalMinutes: Int = 60,
    val remindersEnabled: Boolean = true,
    val reminderOffsetsMinutes: List<Int> = listOf(0, 1440),
    val notificationsPermitted: Boolean = true,
    val needsExactAlarmPermission: Boolean = false,
    val locationRemindersEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val sortMode: String = "due",
    val smartQuickAdd: Boolean = true,
    val isSyncing: Boolean = false,
    val loggedOut: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val workScheduler: WorkScheduler,
    private val reminderScheduler: ReminderScheduler,
    private val notificationHelper: NotificationHelper,
    private val geofenceManager: GeofenceManager,
    private val biometricHelper: BiometricHelper,
) : ViewModel() {

    private val _local = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _local.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authRepository.session,
                authRepository.appLockEnabled,
                settingsRepository.settings,
            ) { session, appLock, settings ->
                Triple(session, appLock, settings)
            }.collect { (session, appLock, settings) ->
                _local.update {
                    it.copy(
                        serverUrl = session?.serverUrl.orEmpty(),
                        username = session?.username.orEmpty(),
                        appLockEnabled = appLock,
                        biometricAvailable = biometricHelper.isAvailable(),
                        themeMode = settings.themeMode,
                        textSize = settings.textSize,
                        useCustomFont = settings.useCustomFont,
                        syncIntervalMinutes = settings.syncIntervalMinutes,
                        remindersEnabled = settings.remindersEnabled,
                        reminderOffsetsMinutes = settings.reminderOffsetsMinutes,
                        notificationsPermitted = notificationHelper.canNotify(),
                        needsExactAlarmPermission = reminderScheduler.needsExactAlarmPermission(),
                        locationRemindersEnabled = settings.locationRemindersEnabled,
                        hasLocationPermission = geofenceManager.hasForegroundPermission(),
                        hasBackgroundLocationPermission = geofenceManager.hasBackgroundPermission(),
                        sortMode = settings.sortMode,
                        smartQuickAdd = settings.smartQuickAdd,
                    )
                }
            }
        }
    }

    fun consumeMessage() { _local.update { it.copy(message = null) } }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { authRepository.setAppLockEnabled(enabled) }
    }

    fun setThemeMode(mode: String) = settingsRepository.setThemeMode(mode)
    fun setTextSize(size: String) = settingsRepository.setTextSize(size)
    fun setUseCustomFont(enabled: Boolean) = settingsRepository.setUseCustomFont(enabled)
    fun setSortMode(mode: String) = settingsRepository.setSortMode(mode)
    fun setSmartQuickAdd(enabled: Boolean) = settingsRepository.setSmartQuickAdd(enabled)

    fun setSyncIntervalMinutes(minutes: Int) {
        settingsRepository.setSyncIntervalMinutes(minutes)
        workScheduler.scheduleSync(minutes)
    }

    fun setRemindersEnabled(enabled: Boolean) {
        settingsRepository.setRemindersEnabled(enabled)
        viewModelScope.launch { rearmAll() }
    }

    fun toggleReminderOffset(minutes: Int) {
        val current = _local.value.reminderOffsetsMinutes
        val next = if (minutes in current) current - minutes else current + minutes
        // Leaving the list empty would silently turn reminders off while the switch still
        // reads as on, so keep at least one offset.
        settingsRepository.setReminderOffsets(next.ifEmpty { listOf(0) })
        viewModelScope.launch { rearmAll() }
    }

    private suspend fun rearmAll() {
        val until = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
        taskRepository.dueBefore(until).forEach { (href, todo) -> reminderScheduler.reschedule(href, todo) }
    }

    fun setLocationRemindersEnabled(enabled: Boolean) {
        settingsRepository.setLocationRemindersEnabled(enabled)
        workScheduler.scheduleLocationChecks(enabled)
        viewModelScope.launch {
            if (enabled) geofenceManager.refresh(taskRepository.withLocations()) else geofenceManager.clearAll()
        }
    }

    /** Re-read after a permission dialog, since the grant doesn't come back through a flow. */
    fun refreshPermissions() {
        _local.update {
            it.copy(
                hasLocationPermission = geofenceManager.hasForegroundPermission(),
                hasBackgroundLocationPermission = geofenceManager.hasBackgroundPermission(),
                notificationsPermitted = notificationHelper.canNotify(),
                needsExactAlarmPermission = reminderScheduler.needsExactAlarmPermission(),
            )
        }
        viewModelScope.launch {
            if (_local.value.locationRemindersEnabled && _local.value.hasLocationPermission) {
                geofenceManager.refresh(taskRepository.withLocations())
                workScheduler.scheduleLocationChecks(true)
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _local.update { it.copy(isSyncing = true) }
            val result = taskRepository.refreshAll()
            rearmAll()
            _local.update {
                it.copy(
                    isSyncing = false,
                    message = result.exceptionOrNull()?.let { e -> friendlyError(e) } ?: "Everything is up to date",
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            taskRepository.clearCache()
            _local.update { it.copy(message = "Offline copy cleared") }
            syncNow()
        }
    }

    fun logout() {
        viewModelScope.launch {
            workScheduler.cancelSync()
            taskRepository.clearCache()
            authRepository.clearSession()
            _local.update { it.copy(loggedOut = true) }
        }
    }
}
