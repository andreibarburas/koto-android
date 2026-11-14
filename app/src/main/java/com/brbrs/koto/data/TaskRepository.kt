package com.brbrs.koto.data

import android.util.Log
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.auth.KotoSession
import com.brbrs.koto.ical.DateVal
import com.brbrs.koto.ical.Recurrence
import com.brbrs.koto.ical.VTodo
import com.brbrs.koto.network.CalDavApi
import com.brbrs.koto.network.CalDavException
import com.brbrs.koto.network.RemoteTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KotoRepo"

/**
 * The one place that owns the local cache and the server.
 *
 * Writes are write-through: the local row is updated immediately so the UI never waits on the
 * network, then the PUT is attempted. If the PUT fails for any reason the row stays marked
 * `pending` and [pushPending] retries it on the next sync. A 412 (someone else edited the task
 * first) is resolved by re-reading the server copy and re-applying the local edit on top,
 * which is the right default for a single person using several of their own devices.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val api: CalDavApi,
    private val authRepository: AuthRepository,
) {
    // ── Observation ──────────────────────────────────────────────────────────────

    val lists: Flow<List<TaskList>> = dao.observeLists().map { rows ->
        rows.map { TaskList(it.href, it.displayName, it.color, it.readOnly, it.sortOrder) }
    }

    /** Every task, parsed, with its list's name and color attached. */
    val allTasks: Flow<List<TaskNode>> =
        combine(dao.observeAllTasks(), dao.observeLists()) { tasks, lists ->
            val listsByHref = lists.associateBy { it.href }
            val parsed = tasks.mapNotNull { e ->
                val todo = VTodo.parse(e.rawIcs) ?: return@mapNotNull null
                Task(e.href, e.listHref, todo, e.etag, e.pending)
            }
            val byParent = parsed.groupBy { it.todo.parentUid }
            val allUids = parsed.map { it.uid }.toSet()
            parsed
                // A task whose parent is in another list, or no longer exists, is shown at top
                // level rather than hidden -- an orphan that renders nowhere is the worst outcome.
                .filter { it.todo.parentUid == null || it.todo.parentUid !in allUids }
                .map { task ->
                    val l = listsByHref[task.listHref]
                    TaskNode(
                        task = task,
                        children = byParent[task.uid].orEmpty().sortedBy { it.due?.millis ?: Long.MAX_VALUE },
                        listName = l?.displayName ?: "Tasks",
                        listColor = l?.color ?: DEFAULT_COLOR,
                    )
                }
        }

    fun observeTask(href: String): Flow<Task?> = dao.observeTask(href).map { e ->
        e ?: return@map null
        val todo = VTodo.parse(e.rawIcs) ?: return@map null
        Task(e.href, e.listHref, todo, e.etag, e.pending)
    }

    /**
     * One-shot lookup by the task's stable CalDAV [uid] rather than its (mutable) href. Used
     * to resolve cross-app deep links -- see [com.brbrs.koto.contactlinks.ContactLinksSync],
     * whose contact-links.json entries key tasks by uid since href can change, e.g. on rename.
     */
    suspend fun getTaskByUidOnce(uid: String): Task? {
        val e = dao.getTaskByUid(uid) ?: return null
        val todo = VTodo.parse(e.rawIcs) ?: return null
        return Task(e.href, e.listHref, todo, e.etag, e.pending)
    }

    /** Children of a task, for the detail screen's subtask section. */
    fun observeChildren(parentUid: String): Flow<List<Task>> = dao.observeAllTasks().map { rows ->
        rows.mapNotNull { e ->
            val todo = VTodo.parse(e.rawIcs) ?: return@mapNotNull null
            if (todo.parentUid != parentUid) null else Task(e.href, e.listHref, todo, e.etag, e.pending)
        }.sortedBy { it.due?.millis ?: Long.MAX_VALUE }
    }

    // ── Sync ─────────────────────────────────────────────────────────────────────

    /** Confirms the server is reachable and has at least one VTODO collection. */
    suspend fun probeTasks(session: KotoSession): Result<Int> = runCatching {
        api.listTaskLists(session).size
    }

    /**
     * Full refresh: rediscover collections, push anything still pending, then pull each list
     * incrementally where a sync token allows it and completely where it doesn't.
     */
    suspend fun refreshAll(): Result<Unit> = runCatching {
        val session = authRepository.currentSession() ?: return@runCatching
        val remoteLists = api.listTaskLists(session)
        val existing = dao.getLists().associateBy { it.href }
        dao.replaceLists(
            remoteLists.mapIndexed { index, r ->
                TaskListEntity(
                    href = r.href,
                    displayName = r.displayName,
                    color = r.color?.takeIf { it.length == 6 } ?: DEFAULT_COLOR,
                    // Never seed from discovery. The token the server reports here describes
                    // the collection as it is NOW; storing it before any task has been
                    // downloaded makes the first sync-collection ask for "changes since now"
                    // and return nothing, leaving the list permanently empty. Only a token
                    // earned by an actual fetch is safe to keep.
                    syncToken = existing[r.href]?.syncToken,
                    readOnly = r.readOnly,
                    sortOrder = existing[r.href]?.sortOrder ?: index,
                )
            }
        )
        pushPending(session)
        dao.getLists().forEach { list -> runCatching { syncList(session, list) }.onFailure {
            Log.w(TAG, "sync of ${list.href} failed: ${it.message}")
        } }
    }

    private suspend fun syncList(session: KotoSession, list: TaskListEntity) {
        val token = list.syncToken
        // A token but nothing cached means the two got out of step somehow -- a cleared cache,
        // an interrupted first run, a bad token from an earlier version. Incremental sync would
        // return nothing and the list would stay empty forever, so start over instead.
        if (token.isNullOrBlank() || dao.countTasksForList(list.href) == 0) {
            fullFetch(session, list)
            return
        }
        val result = api.syncCollection(session, list.href, token)
        if (result.tokenInvalid) {
            fullFetch(session, list)
            return
        }
        if (result.removedHrefs.isNotEmpty()) {
            // Don't delete a row the user has edited locally; the push will recreate it server-side.
            val pending = dao.getUnsynced().map { it.href }.toSet()
            dao.deleteTasks(result.removedHrefs.filterNot { it in pending })
        }
        val entities = result.changed.mapNotNull { toEntity(it, list.href) }
        if (entities.isNotEmpty()) {
            val pending = dao.getUnsynced().map { it.href }.toSet()
            dao.upsertTasks(entities.filterNot { it.href in pending })
        }
        dao.setSyncToken(list.href, result.newSyncToken ?: token)
    }

    private suspend fun fullFetch(session: KotoSession, list: TaskListEntity) {
        val remote = api.fetchAllTasks(session, list.href)
        dao.replaceListContent(list.href, remote.mapNotNull { toEntity(it, list.href) })
        // Ask for a fresh token so the next run can go incremental.
        val refreshed = api.listTaskLists(session).firstOrNull { it.href == list.href }
        dao.setSyncToken(list.href, refreshed?.syncToken)
    }

    private fun toEntity(remote: RemoteTask, listHref: String): TaskEntity? {
        val todo = VTodo.parse(remote.ics) ?: return null
        return todo.toEntity(remote.href, listHref, remote.etag, remote.ics, pending = false)
    }

    /** Retries every local edit that hasn't been accepted yet. Safe to call repeatedly. */
    suspend fun pushPending(session: KotoSession) {
        dao.getUnsynced().forEach { row ->
            runCatching {
                if (row.deletedLocally) {
                    api.deleteTask(session, row.href, row.etag)
                    dao.deleteTask(row.href)
                } else {
                    val etag = api.putTask(session, row.href, row.rawIcs, ifMatchEtag = row.etag)
                    val confirmed = etag ?: api.getTask(session, row.href)?.etag
                    dao.upsertTasks(listOf(row.copy(etag = confirmed, pending = false)))
                }
            }.onFailure { e ->
                if (e is CalDavException && e.code == 412) {
                    runCatching { resolveConflict(session, row) }
                } else {
                    Log.w(TAG, "push of ${row.href} failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Someone else changed this task first. Re-read the server copy and re-apply only the
     * fields Koto actually edited on top of it, so a change made elsewhere (a note added on
     * the web, say) is not thrown away by a tick made on the phone.
     */
    private suspend fun resolveConflict(session: KotoSession, local: TaskEntity) {
        val remote = api.getTask(session, local.href) ?: run {
            // Gone from the server. Recreate it from the local copy.
            val etag = api.putTask(session, local.href, local.rawIcs, isNew = true)
            dao.upsertTasks(listOf(local.copy(etag = etag, pending = false)))
            return
        }
        val remoteTodo = VTodo.parse(remote.ics)
        val localTodo = VTodo.parse(local.rawIcs)
        if (remoteTodo == null || localTodo == null) return
        val merged = remoteTodo.copy(
            summary = localTodo.summary,
            description = localTodo.description,
            due = localTodo.due,
            start = localTodo.start,
            status = localTodo.status,
            completed = localTodo.completed,
            percentComplete = localTodo.percentComplete,
            priority = localTodo.priority,
            categories = localTodo.categories,
            parentUid = localTodo.parentUid,
            rrule = localTodo.rrule,
            location = localTodo.location,
            alarms = localTodo.alarms,
            sequence = maxOf(remoteTodo.sequence, localTodo.sequence) + 1,
        )
        val ics = merged.toIcs()
        val etag = api.putTask(session, local.href, ics, ifMatchEtag = remote.etag)
        dao.upsertTasks(
            listOf(merged.toEntity(local.href, local.listHref, etag, ics, pending = false))
        )
    }

    // ── Writes ───────────────────────────────────────────────────────────────────

    suspend fun createTask(listHref: String, todoIn: VTodo): String {
        val todo = VTodo.withMirroredAttachments(todoIn)
        val href = listHref.trimEnd('/') + "/" + todo.uid + ".ics"
        val ics = todo.toIcs()
        dao.upsertTasks(listOf(todo.toEntity(href, listHref, null, ics, pending = true)))
        val session = authRepository.currentSession() ?: return href
        runCatching {
            val etag = api.putTask(session, href, ics, isNew = true)
            val confirmed = etag ?: api.getTask(session, href)?.etag
            dao.upsertTasks(listOf(todo.toEntity(href, listHref, confirmed, ics, pending = false)))
        }.onFailure { Log.w(TAG, "create failed, queued: ${it.message}") }
        return href
    }

    suspend fun updateTask(href: String, transform: (VTodo) -> VTodo) {
        val row = dao.getTask(href) ?: return
        val current = VTodo.parse(row.rawIcs) ?: return
        // Keep the notes' attachment block in step with the ATTACH list on every edit, so the
        // links stay visible in Nextcloud Tasks on the web, which ignores ATTACH.
        val updated = VTodo.withMirroredAttachments(transform(current))
            .copy(lastModified = System.currentTimeMillis())
        val ics = updated.toIcs()
        dao.upsertTasks(listOf(updated.toEntity(href, row.listHref, row.etag, ics, pending = true)))
        val session = authRepository.currentSession() ?: return
        runCatching {
            val etag = api.putTask(session, href, ics, ifMatchEtag = row.etag)
            val confirmed = etag ?: api.getTask(session, href)?.etag
            dao.upsertTasks(listOf(updated.toEntity(href, row.listHref, confirmed, ics, pending = false)))
        }.onFailure { e ->
            if (e is CalDavException && e.code == 412) {
                runCatching {
                    resolveConflict(session, updated.toEntity(href, row.listHref, row.etag, ics, pending = true))
                }
            } else {
                Log.w(TAG, "update failed, queued: ${e.message}")
            }
        }
    }

    /**
     * Ticking a task off. A recurring task rolls forward to its next occurrence instead of
     * closing, matching what Nextcloud Tasks does, so the two never disagree about whether
     * a repeating chore is open.
     */
    suspend fun setCompleted(href: String, completed: Boolean) {
        updateTask(href) { todo ->
            when {
                !completed -> todo.markNotCompleted()
                todo.rrule != null -> Recurrence.completeRecurring(todo)
                else -> todo.markCompleted()
            }
        }
        // Completing a parent closes its open children too, which is what people expect from
        // a checklist and what avoids a "done" task showing 2/5 subtasks.
        if (completed) {
            val row = dao.getTask(href) ?: return
            val parent = VTodo.parse(row.rawIcs) ?: return
            if (parent.rrule != null) return
            val children = dao.getUnsyncedSafeChildren(parent.uid)
            children.forEach { child -> setCompletedLeaf(child.href) }
        }
    }

    private suspend fun setCompletedLeaf(href: String) {
        val row = dao.getTask(href) ?: return
        val todo = VTodo.parse(row.rawIcs) ?: return
        if (todo.isCompleted) return
        updateTask(href) { it.markCompleted() }
    }

    /**
     * Writes a manual order as X-APPLE-SORT-ORDER on each task, which is the property Nextcloud
     * Tasks uses for its own drag-and-drop, so the order shows up on the web too.
     *
     * Positions are spaced ten apart so a later single move can usually slot a task between two
     * neighbours without rewriting the whole list. Only tasks whose position actually changed
     * are written, since each one is a separate PUT.
     */
    suspend fun applyManualOrder(orderedHrefs: List<String>) {
        orderedHrefs.forEachIndexed { index, href ->
            val target = (index + 1) * 10
            val row = dao.getTask(href) ?: return@forEachIndexed
            val current = VTodo.parse(row.rawIcs) ?: return@forEachIndexed
            if (current.sortOrder == target) return@forEachIndexed
            updateTask(href) { it.copy(sortOrder = target) }
        }
    }

    suspend fun deleteTask(href: String) {
        val row = dao.getTask(href) ?: return
        dao.upsertTasks(listOf(row.copy(deletedLocally = true, pending = true)))
        val session = authRepository.currentSession() ?: return
        runCatching {
            api.deleteTask(session, href, row.etag)
            dao.deleteTask(href)
        }.onFailure { Log.w(TAG, "delete queued: ${it.message}") }
    }

    /**
     * Moving a task between lists is a create-then-delete in CalDAV: a resource cannot change
     * collection in place. The new copy keeps the same UID, so any RELATED-TO pointing at it
     * from a subtask still resolves.
     */
    suspend fun moveTask(href: String, toListHref: String) {
        val row = dao.getTask(href) ?: return
        if (row.listHref == toListHref) return
        val todo = VTodo.parse(row.rawIcs) ?: return
        createTask(toListHref, todo)
        deleteTask(href)
    }

    suspend fun createList(name: String, colorHex: String): Result<Unit> = runCatching {
        val session = authRepository.currentSession() ?: error("Not signed in")
        api.createTaskList(session, name, colorHex)
        refreshAll().getOrThrow()
    }

    suspend fun renameList(href: String, name: String, colorHex: String?): Result<Unit> = runCatching {
        val session = authRepository.currentSession() ?: error("Not signed in")
        api.renameTaskList(session, href, name, colorHex)
        val existing = dao.getList(href) ?: return@runCatching
        dao.upsertLists(listOf(existing.copy(displayName = name, color = colorHex ?: existing.color)))
    }

    suspend fun deleteList(href: String): Result<Unit> = runCatching {
        val session = authRepository.currentSession() ?: error("Not signed in")
        api.deleteTaskList(session, href)
        dao.deleteTasksForList(href)
        dao.deleteList(href)
    }

    /** Every tag in use, with how many open tasks carry it. */
    suspend fun tagCounts(): Map<String, Int> {
        val counts = HashMap<String, Int>()
        dao.getAllForGeofencing().forEach { row ->
            VTodo.parse(row.rawIcs)?.categories?.forEach { tag ->
                counts[tag] = (counts[tag] ?: 0) + 1
            }
        }
        return counts
    }

    /**
     * Renames a tag everywhere it appears. CATEGORIES has no identity of its own -- a tag is
     * just a string repeated across tasks -- so renaming means rewriting every task that
     * carries it, one PUT each. Merging into an existing tag is allowed and deduplicates.
     */
    suspend fun renameTag(from: String, to: String): Int {
        val target = to.trim()
        if (target.isBlank() || target == from) return 0
        var touched = 0
        dao.getAllForGeofencing().forEach { row ->
            val todo = VTodo.parse(row.rawIcs) ?: return@forEach
            if (from !in todo.categories) return@forEach
            updateTask(row.href) { t ->
                t.copy(categories = t.categories.map { if (it == from) target else it }.distinct())
            }
            touched++
        }
        return touched
    }

    /** Removes a tag from every task. The tasks themselves are untouched. */
    suspend fun deleteTag(tag: String): Int {
        var touched = 0
        dao.getAllForGeofencing().forEach { row ->
            val todo = VTodo.parse(row.rawIcs) ?: return@forEach
            if (tag !in todo.categories) return@forEach
            updateTask(row.href) { t -> t.copy(categories = t.categories - tag) }
            touched++
        }
        return touched
    }

    suspend fun clearCache() {
        dao.clearAll()
    }

    /** Open tasks that carry GEO coordinates, for the location reminders. */
    suspend fun withLocations(): List<Pair<String, VTodo>> =
        dao.getAllForGeofencing().mapNotNull { e ->
            VTodo.parse(e.rawIcs)?.takeIf { it.hasLocation && !it.isCompleted }?.let { e.href to it }
        }

    suspend fun dueBefore(until: Long): List<Pair<String, VTodo>> =
        dao.getDueBefore(until).mapNotNull { e -> VTodo.parse(e.rawIcs)?.let { e.href to it } }

    /** hrefs of completed/cancelled tasks whose due date falls inside the alarm window. */
    suspend fun completedHrefsWithDueBefore(until: Long): List<String> =
        dao.getCompletedWithDueBefore(until).map { it.href }

    companion object {
        const val DEFAULT_COLOR = "5E9CB4"
    }
}

/** Flattens a parsed VTODO into the denormalized row Room queries against. */
fun VTodo.toEntity(
    href: String,
    listHref: String,
    etag: String?,
    ics: String,
    pending: Boolean,
): TaskEntity = TaskEntity(
    href = href,
    listHref = listHref,
    uid = uid,
    etag = etag,
    rawIcs = ics,
    summary = summary,
    dueMillis = due?.millis,
    dueDateOnly = due?.dateOnly ?: false,
    startMillis = start?.millis,
    completedMillis = completed,
    status = status,
    priority = priority,
    percentComplete = percentComplete,
    parentUid = parentUid,
    hasRrule = !rrule.isNullOrBlank(),
    categories = categories.joinToString("\u001F"),
    createdMillis = created,
    lastModified = lastModified,
    pending = pending,
    deletedLocally = false,
)

/** Direct children of [parentUid] that are still open. */
suspend fun TaskDao.getUnsyncedSafeChildren(parentUid: String): List<TaskEntity> =
    getChildren(parentUid).filter { it.completedMillis == null && it.status != "COMPLETED" }
