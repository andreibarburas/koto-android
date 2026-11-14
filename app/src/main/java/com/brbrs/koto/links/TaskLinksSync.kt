package com.brbrs.koto.links

import android.util.Log
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.di.ApplicationScope
import com.brbrs.koto.links.data.TaskLinkEntity
import com.brbrs.koto.links.data.TaskLinksDao
import com.brbrs.koto.settings.WebDavClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs a task's cross-app "Linked items" (#12, mirroring Tavla's own) to
 * `Koto/task-links.json`, so they follow the person to another device. Nothing outside Koto
 * reads this file -- unlike contact-links.json, it's not part of Vinci's resolver -- but it's
 * still worth syncing for the same reason Tavla keeps its own copy in its settings file.
 *
 * Push is deliberately unconditional (no ETag/If-Match): there's nothing to merge on a
 * conflict either way (Room is the sole source of truth here), and a conditional PUT was the
 * exact bug that silently broke Tavla's and Koto's contact-link sync on some Nextcloud setups
 * (a proxy tagging GET/PUT responses with mismatched ETags, so even the retry 412'd).
 */
@Singleton
class TaskLinksSync @Inject constructor(
    private val dao: TaskLinksDao,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val syncMutex = Mutex()
    private var debounceJob: Job? = null
    private var lastEtag: String? = null

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
                        // Nothing to merge into Room: this file only carries a denormalized
                        // read-only copy for other devices' benefit. The authoritative
                        // taskUid -> links live in this device's own Room table already.
                    }
                }.onFailure { Log.w(TAG, "fetchFromRemote failed: ${it.message}") }
            }
        }
    }

    fun notifyChanged() {
        debounceJob?.cancel()
        debounceJob = appScope.launch {
            delay(2_000)
            syncMutex.withLock {
                runCatching { pushNow() }.onFailure { Log.w(TAG, "push failed: ${it.message}") }
            }
        }
    }

    fun reset() { lastEtag = null }

    private suspend fun pushNow() {
        val session = authRepository.currentSession() ?: return
        davClient.ensureFolder(session, REMOTE_FOLDER)
        val json = buildJson().toString()
        lastEtag = davClient.putFile(session, REMOTE_FILE, json, ifMatchEtag = null)
    }

    private suspend fun buildJson(): JSONObject {
        val all = dao.getAll()
        val links = JSONArray()
        for (l in all) {
            links.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("taskUid", l.taskUid)
                    put("appPackage", l.appPackage)
                    put("appLabel", l.appLabel)
                    put("label", l.label)
                    put("uri", l.uri)
                },
            )
        }
        return JSONObject().put("links", links)
    }

    companion object {
        private const val TAG = "TaskLinksSync"
        private const val REMOTE_FOLDER = "Koto"
        private const val REMOTE_FILE = "Koto/task-links.json"
    }
}

@Singleton
class TaskLinksRepository @Inject constructor(
    private val dao: TaskLinksDao,
    private val sync: TaskLinksSync,
) {
    fun observeForTask(taskUid: String) = dao.observeForTask(taskUid)

    suspend fun add(taskUid: String, appPackage: String, appLabel: String, label: String, uri: String) {
        dao.insert(
            TaskLinkEntity(
                id = java.util.UUID.randomUUID().toString(),
                taskUid = taskUid,
                appPackage = appPackage,
                appLabel = appLabel,
                label = label,
                uri = uri,
            ),
        )
        sync.notifyChanged()
    }

    suspend fun remove(id: String) {
        dao.delete(id)
        sync.notifyChanged()
    }
}
