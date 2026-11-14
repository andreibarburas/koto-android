package com.brbrs.koto.contactlinks

import android.util.Log
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.contactlinks.data.ContactLinksDao
import com.brbrs.koto.contactlinks.data.TaskContactLinkEntity
import com.brbrs.koto.data.TaskDao
import com.brbrs.koto.di.ApplicationScope
import com.brbrs.koto.settings.WebDavClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs task <-> contact links to `Koto/contact-links.json`, the sidecar Vinci's
 * ContactLinksRepository resolves to show a contact's "Linked items" section. Same pattern
 * as Tavla's ContactLinksSync: Room is the working copy, pushes are debounced and
 * etag-guarded. Vinci only ever reads this file -- it never writes it.
 */
@Singleton
class ContactLinksSync @Inject constructor(
    private val dao: ContactLinksDao,
    private val taskDao: TaskDao,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val syncMutex = Mutex()
    private var debounceJob: Job? = null
    private var lastEtag: String? = null

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> get() = _lastSyncError

    /** Pulls the remote file so lastEtag is primed, or seeds it on first run. Call on app start / task list open. */
    fun fetchFromRemote() {
        appScope.launch {
            syncMutex.withLock {
                runCatching {
                    val session = authRepository.currentSession() ?: return@launch
                    davClient.ensureFolder(session, REMOTE_FOLDER)
                    val remote = davClient.getFile(session, REMOTE_FILE)
                    if (remote == null) {
                        pushNow()
                    } else {
                        lastEtag = remote.etag
                        // Nothing to merge into Room: the remote file only carries denormalized
                        // titles/snippets for Vinci's benefit. The authoritative taskUid ->
                        // contactUid pairs live in this device's own Room table already.
                    }
                }.onFailure { Log.w(TAG, "fetchFromRemote failed: ${it.message}") }
            }
        }
    }

    /** Call after any local add/remove of a task-contact link. Debounced to one push per burst. */
    fun notifyChanged() {
        debounceJob?.cancel()
        debounceJob = appScope.launch {
            delay(2_000)
            syncMutex.withLock {
                runCatching { pushNow() }
                    .onSuccess { _lastSyncError.value = null }
                    .onFailure {
                        Log.w(TAG, "push failed: ${it.message}")
                        _lastSyncError.value = it.message ?: "Could not sync to Nextcloud"
                    }
            }
        }
    }

    fun reset() {
        lastEtag = null
        _lastSyncError.value = null
    }

    // Deliberately unconditional: there was never anything to merge on a 412 (Room is the sole
    // source of truth for these links, this file is write-only denormalization for Vinci's
    // benefit), and on some Nextcloud setups a proxy tags GET/PUT responses with mismatched
    // ETags for the same file (gzip-compressing proxies commonly do this), so even the retry
    // 412'd every time and the push silently never reached the server. See Tavla's identical fix.
    private suspend fun pushNow() {
        val session = authRepository.currentSession()
        if (session == null) {
            _lastSyncError.value = "Not synced to Nextcloud -- connect an account in Settings so Vinci can see this"
            return
        }
        davClient.ensureFolder(session, REMOTE_FOLDER)
        val json = buildJson().toString()
        lastEtag = davClient.putFile(session, REMOTE_FILE, json, ifMatchEtag = null)
    }

    private suspend fun buildJson(): JSONObject {
        val allLinks = dao.getAll()
        val byTask = allLinks.groupBy { it.taskUid }
        val links = JSONArray()
        for ((taskUid, entries) in byTask) {
            val task = taskDao.getTaskByUid(taskUid) ?: continue
            val list = taskDao.getList(task.listHref)
            val entry = JSONObject().apply {
                put("itemId", taskUid)
                put("title", task.summary)
                put("snippet", list?.displayName.orEmpty())
                put("updatedAt", task.lastModified ?: 0L)
                put("contactUids", JSONArray().apply { entries.forEach { put(it.contactUid) } })
            }
            links.put(entry)
        }
        return JSONObject().put("links", links)
    }

    companion object {
        private const val TAG = "ContactLinksSync"
        private const val REMOTE_FOLDER = "Koto"
        private const val REMOTE_FILE = "Koto/contact-links.json"
    }
}

// Convenience used by the ViewModel for add/remove + resync in one place.
@Singleton
class ContactLinksRepository @Inject constructor(
    private val dao: ContactLinksDao,
    private val sync: ContactLinksSync,
) {
    val lastSyncError: StateFlow<String?> get() = sync.lastSyncError

    fun observeForTask(taskUid: String) = dao.observeForTask(taskUid)

    suspend fun link(taskUid: String, contactUid: String) {
        dao.insert(TaskContactLinkEntity(taskUid, contactUid))
        sync.notifyChanged()
    }

    suspend fun unlink(taskUid: String, contactUid: String) {
        dao.delete(taskUid, contactUid)
        sync.notifyChanged()
    }
}
