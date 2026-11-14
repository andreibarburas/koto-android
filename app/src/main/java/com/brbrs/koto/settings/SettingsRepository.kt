package com.brbrs.koto.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.auth.KotoSession
import com.brbrs.koto.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ncSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "koto_nc_settings")
private const val TAG = "KotoSettings"
private const val REMOTE_FOLDER = "Koto"
private const val REMOTE_FILE = "Koto/koto-settings.json"

/**
 * Single source of truth for [KotoSettings]. A local DataStore cache keeps reads instant and
 * offline-safe; it is mirrored into one JSON file in the user's Nextcloud. Writes are debounced
 * so flipping a few switches is one PUT, not five.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val JSON_KEY = stringPreferencesKey("settings_json")
    private val ETAG_KEY = stringPreferencesKey("settings_etag")
    private val syncMutex = Mutex()
    private var debounceJob: Job? = null
    private var folderEnsured = false

    private val localFlow: Flow<KotoSettings> = context.ncSettingsDataStore.data.map { prefs ->
        prefs[JSON_KEY]?.let { runCatching { KotoSettings.fromJson(JSONObject(it)) }.getOrNull() } ?: KotoSettings()
    }

    val settings: StateFlow<KotoSettings> =
        localFlow.stateIn(appScope, SharingStarted.Eagerly, KotoSettings())

    /** Call after sign-in and on app start to pull in settings saved from another device. */
    fun fetchFromRemote() {
        appScope.launch {
            val session = authRepository.currentSession() ?: return@launch
            syncMutex.withLock {
                runCatching {
                    if (!folderEnsured) { davClient.ensureFolder(session, REMOTE_FOLDER); folderEnsured = true }
                    val remote = davClient.getFile(session, REMOTE_FILE)
                    if (remote == null) {
                        pushNow(session)
                    } else if (remote.etag != context.ncSettingsDataStore.data.first()[ETAG_KEY]) {
                        context.ncSettingsDataStore.edit { it[JSON_KEY] = remote.content; it[ETAG_KEY] = remote.etag.orEmpty() }
                    }
                }.onFailure { Log.w(TAG, "fetchFromRemote failed: ${it.message}") }
            }
        }
    }

    fun update(mutator: (KotoSettings) -> KotoSettings) {
        appScope.launch {
            val updated = mutator(localFlow.first())
            context.ncSettingsDataStore.edit { it[JSON_KEY] = updated.toJson().toString() }
            debounceJob?.cancel()
            debounceJob = appScope.launch {
                delay(1_500)
                val session = authRepository.currentSession() ?: return@launch
                syncMutex.withLock { runCatching { pushNow(session) }.onFailure { Log.w(TAG, "sync failed: ${it.message}") } }
            }
        }
    }

    private suspend fun pushNow(session: KotoSession) {
        if (!folderEnsured) { davClient.ensureFolder(session, REMOTE_FOLDER); folderEnsured = true }
        val prefs = context.ncSettingsDataStore.data.first()
        val content = prefs[JSON_KEY] ?: KotoSettings().toJson().toString()
        val etag = prefs[ETAG_KEY]
        try {
            val newEtag = davClient.putFile(session, REMOTE_FILE, content, etag.takeIf { !it.isNullOrBlank() })
            context.ncSettingsDataStore.edit { it[ETAG_KEY] = newEtag.orEmpty() }
        } catch (e: DavConflictException) {
            // Another device saved first. Our scalar settings win (they're what the person just
            // changed); map and list fields are merged so a list hidden elsewhere stays hidden.
            val remote = davClient.getFile(session, REMOTE_FILE) ?: return
            val remoteSettings = runCatching { KotoSettings.fromJson(JSONObject(remote.content)) }.getOrNull() ?: return
            val ours = runCatching { KotoSettings.fromJson(JSONObject(content)) }.getOrNull() ?: return
            val merged = ours.copy(
                hiddenLists = (remoteSettings.hiddenLists + ours.hiddenLists).distinct(),
                listOrder = remoteSettings.listOrder + ours.listOrder,
                listIcons = remoteSettings.listIcons + ours.listIcons,
                tagColors = remoteSettings.tagColors + ours.tagColors,
                listCovers = remoteSettings.listCovers + ours.listCovers,
            )
            val mergedJson = merged.toJson().toString()
            context.ncSettingsDataStore.edit { it[JSON_KEY] = mergedJson }
            val newEtag = davClient.putFile(session, REMOTE_FILE, mergedJson, remote.etag)
            context.ncSettingsDataStore.edit { it[ETAG_KEY] = newEtag.orEmpty() }
        }
    }

    fun setThemeMode(mode: String) = update { it.copy(themeMode = mode) }
    fun setTextSize(size: String) = update { it.copy(textSize = size) }
    fun setUseCustomFont(enabled: Boolean) = update { it.copy(useCustomFont = enabled) }
    fun setSyncIntervalMinutes(minutes: Int) = update { it.copy(syncIntervalMinutes = minutes) }
    fun setRemindersEnabled(enabled: Boolean) = update { it.copy(remindersEnabled = enabled) }
    fun setLocationRemindersEnabled(enabled: Boolean) = update { it.copy(locationRemindersEnabled = enabled) }
    fun setReminderOffsets(offsets: List<Int>) = update { it.copy(reminderOffsetsMinutes = offsets) }
    fun setSortMode(mode: String) = update { it.copy(sortMode = mode) }
    fun setShowCompleted(enabled: Boolean) = update { it.copy(showCompleted = enabled) }
    fun setGroupByList(enabled: Boolean) = update { it.copy(groupByList = enabled) }
    fun setSmartQuickAdd(enabled: Boolean) = update { it.copy(smartQuickAdd = enabled) }
    fun setDefaultList(href: String?) = update { it.copy(defaultListHref = href) }
    /** Passing null clears the choice, so the tile falls back to the color glyph. */
    fun setListIcon(href: String, icon: String?) = update {
        it.copy(listIcons = if (icon == null) it.listIcons - href else it.listIcons + (href to icon))
    }

    fun setTagColor(tag: String, color: String?) = update {
        it.copy(tagColors = if (color == null) it.tagColors - tag else it.tagColors + (tag to color))
    }

    /** Carries a tag's color across when the tag itself is renamed. */
    fun renameTagColor(from: String, to: String) = update { s ->
        val color = s.tagColors[from] ?: return@update s
        s.copy(tagColors = s.tagColors - from + (to to color))
    }

    fun forgetTagColor(tag: String) = update { it.copy(tagColors = it.tagColors - tag) }

    fun toggleHiddenList(href: String) = update {
        it.copy(hiddenLists = if (href in it.hiddenLists) it.hiddenLists - href else it.hiddenLists + href)
    }

    /**
     * Sets or clears a cover token for [href].
     * Pass null to remove the cover entry from the map entirely.
     */
    fun setCover(href: String, token: String?) = update {
        it.copy(listCovers = if (token == null) it.listCovers - href else it.listCovers + (href to token))
    }

}