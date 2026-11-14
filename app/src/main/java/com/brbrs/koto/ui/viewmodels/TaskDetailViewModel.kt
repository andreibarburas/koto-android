package com.brbrs.koto.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.koto.data.Task
import com.brbrs.koto.data.TaskList
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.auth.AuthRepository
import com.brbrs.koto.location.GeofenceManager
import com.brbrs.koto.settings.WebDavClient
import com.brbrs.koto.ui.components.friendlyError
import dagger.hilt.android.qualifiers.ApplicationContext
import com.brbrs.koto.ical.DateVal
import com.brbrs.koto.ical.VAlarm
import com.brbrs.koto.ical.VTodo
import com.brbrs.koto.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class TaskDetailUiState(
    val uploading: Boolean = false,
    val loading: Boolean = true,
    val task: Task? = null,
    val children: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val deleted: Boolean = false,
    val message: String? = null,
)

/** A linked contact as shown on the task -- blank name if the contact is no longer on-device. */
data class LinkedContact(val cardavUid: String, val displayName: String, val photoUri: String? = null)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    private val authRepository: AuthRepository,
    private val davClient: WebDavClient,
    private val geofenceManager: GeofenceManager,
    private val contactLinks: com.brbrs.koto.contactlinks.ContactLinksRepository,
    private val deviceContacts: com.brbrs.koto.contactlinks.DeviceContactsRepository,
    private val taskLinks: com.brbrs.koto.links.TaskLinksRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val href: String = URLDecoder.decode(savedStateHandle.get<String>("href").orEmpty(), "UTF-8")

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    private val _linkedContacts = MutableStateFlow<List<LinkedContact>>(emptyList())
    val linkedContacts: StateFlow<List<LinkedContact>> = _linkedContacts.asStateFlow()
    private var contactLinksJob: kotlinx.coroutines.Job? = null

    val linkSyncError: StateFlow<String?> = contactLinks.lastSyncError

    private val _links = MutableStateFlow<List<com.brbrs.koto.links.data.TaskLinkEntity>>(emptyList())
    val links: StateFlow<List<com.brbrs.koto.links.data.TaskLinkEntity>> = _links.asStateFlow()
    private var taskLinksJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            // `href` here may actually be the task's stable CalDAV uid instead of its real
            // (mutable) href: in-app navigation and reminder taps always pass a real href, but
            // a cross-app deep link from Vinci/Tavla/Nóta passes the uid, since that's the
            // identifier contact-links.json entries are keyed by (href can change, e.g. on
            // rename, so it isn't safe to use as a durable cross-app pointer). Try the href
            // interpretation first since it's the common case and needs no extra lookup; only
            // fall back to a uid lookup when that comes up empty.
            val resolvedHref = if (repository.observeTask(href).first() != null) {
                href
            } else {
                repository.getTaskByUidOnce(href)?.href ?: href
            }
            repository.observeTask(resolvedHref).collect { task ->
                _uiState.update { it.copy(loading = false, task = task) }
                if (task != null) {
                    observeChildrenOnce(task.uid)
                    observeContactLinksOnce(task.uid)
                    observeTaskLinksOnce(task.uid)
                }
            }
        }
        viewModelScope.launch {
            repository.lists.collect { lists -> _uiState.update { it.copy(lists = lists) } }
        }
    }

    private fun observeContactLinksOnce(taskUid: String) {
        if (contactLinksJob != null) return
        contactLinksJob = viewModelScope.launch {
            contactLinks.observeForTask(taskUid).collect { uids ->
                val details = deviceContacts.detailsFor(uids.toSet())
                _linkedContacts.value = uids.map { uid ->
                    val d = details[uid]
                    LinkedContact(uid, d?.first.orEmpty(), d?.second)
                }
            }
        }
    }

    fun linkContact(contact: com.brbrs.koto.contactlinks.PickableContact) {
        val taskUid = uiState.value.task?.uid ?: return
        viewModelScope.launch { contactLinks.link(taskUid, contact.cardavUid) }
    }

    fun unlinkContact(cardavUid: String) {
        val taskUid = uiState.value.task?.uid ?: return
        viewModelScope.launch { contactLinks.unlink(taskUid, cardavUid) }
    }

    private fun observeTaskLinksOnce(taskUid: String) {
        if (taskLinksJob != null) return
        taskLinksJob = viewModelScope.launch {
            taskLinks.observeForTask(taskUid).collect { _links.value = it }
        }
    }

    fun addLink(appPackage: String, appLabel: String, label: String, uri: String) {
        val taskUid = uiState.value.task?.uid ?: return
        viewModelScope.launch { taskLinks.add(taskUid, appPackage, appLabel, label, uri) }
    }

    fun removeLink(id: String) {
        viewModelScope.launch { taskLinks.remove(id) }
    }

    suspend fun searchContacts(query: String) = deviceContacts.search(query)

    private var childrenJob: kotlinx.coroutines.Job? = null
    private fun observeChildrenOnce(uid: String) {
        if (childrenJob != null) return
        childrenJob = viewModelScope.launch {
            repository.observeChildren(uid).collect { kids ->
                _uiState.update { it.copy(children = kids) }
            }
        }
    }

    fun consumeMessage() { _uiState.update { it.copy(message = null) } }

    private fun edit(transform: (VTodo) -> VTodo) {
        viewModelScope.launch {
            repository.updateTask(href, transform)
            rearm()
        }
    }

    private suspend fun rearm() {
        val until = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
        val match = repository.dueBefore(until).firstOrNull { it.first == href }
        if (match == null) reminderScheduler.cancel(href)
        else reminderScheduler.reschedule(match.first, match.second)
    }

    fun setTitle(title: String) = edit { it.copy(summary = title.trim()) }
    fun setDescription(text: String) = edit { it.copy(description = text.trim().ifBlank { null }) }
    fun setDue(due: DateVal?) = edit { it.copy(due = due) }
    fun setStart(start: DateVal?) = edit { it.copy(start = start) }
    fun setPriority(priority: Int) = edit { it.copy(priority = priority) }
    fun setRecurrence(rule: String?) = edit { it.copy(rrule = rule) }
    fun setLocation(location: String?) = edit { it.copy(location = location?.trim()?.ifBlank { null }) }

    /** Coordinates for a location reminder. Passing null clears them and stops the watching. */
    fun setCoordinates(lat: Double?, lon: Double?, radius: Int?) {
        edit { it.copy(latitude = lat, longitude = lon, geoRadius = if (lat == null) null else radius) }
        viewModelScope.launch {
            if (lat == null) geofenceManager.cancel(href)
            geofenceManager.refresh(repository.withLocations())
        }
    }

    /**
     * Uploads a file to `/Koto/attachments/<task uid>/` in the user's own Nextcloud and hangs
     * it off the task with a standard ATTACH property, the same path the share sheet uses.
     */
    fun attachFile(uri: Uri) {
        val task = _uiState.value.task ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true) }
            val session = authRepository.currentSession()
            if (session == null) {
                _uiState.update { it.copy(uploading = false, message = "Sign in first") }
                return@launch
            }
            val result = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read that file")
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val name = displayName(uri)
                davClient.ensureFolder(session, "Koto")
                davClient.ensureFolder(session, "Koto/attachments")
                davClient.ensureFolder(session, "Koto/attachments/${task.uid}")
                davClient.putBytes(session, "Koto/attachments/${task.uid}/$name", bytes, mime)
            }
            result.onSuccess { url ->
                repository.updateTask(href) { it.copy(attachments = (it.attachments + url).distinct()) }
                _uiState.update { it.copy(uploading = false, message = "Attached") }
            }.onFailure { e ->
                _uiState.update { it.copy(uploading = false, message = friendlyError(e)) }
            }
        }
    }

    /** Removes the reference. The file itself is left in Nextcloud rather than silently deleted. */
    fun removeAttachment(url: String) = edit { it.copy(attachments = it.attachments - url) }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { return sanitize(it) }
                }
            }
        }
        return "attachment-" + java.util.UUID.randomUUID().toString().take(8)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[/\\?%*:|"<>\x00-\x1F]"""), "_").take(120).ifBlank { "attachment" }
    fun setPercent(percent: Int?) = edit {
        // Nudging progress to 100 should also close the task, or the two disagree.
        when (percent) {
            100 -> it.markCompleted()
            null, 0 -> it.copy(percentComplete = percent, status = com.brbrs.koto.ical.TodoStatus.NEEDS_ACTION, completed = null)
            else -> it.copy(percentComplete = percent, status = com.brbrs.koto.ical.TodoStatus.IN_PROCESS, completed = null)
        }
    }

    fun addTag(tag: String) = edit { it.copy(categories = (it.categories + tag.trim()).distinct().filter { t -> t.isNotBlank() }) }
    fun removeTag(tag: String) = edit { it.copy(categories = it.categories - tag) }

    fun setAlarm(minutesBefore: Int?) = edit {
        if (minutesBefore == null) it.copy(alarms = emptyList())
        else it.copy(alarms = listOf(VAlarm(minutesBefore = minutesBefore, description = it.summary)))
    }

    fun setCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(href, completed)
            if (completed) reminderScheduler.cancel(href) else rearm()
        }
    }

    fun setChildCompleted(childHref: String, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(childHref, completed)
            if (completed) reminderScheduler.cancel(childHref)
        }
    }

    fun addSubtask(title: String) {
        val parent = _uiState.value.task ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            val child = VTodo.newTask(title.trim()).copy(parentUid = parent.uid)
            repository.createTask(parent.listHref, child)
        }
    }

    fun deleteSubtask(childHref: String) {
        viewModelScope.launch {
            reminderScheduler.cancel(childHref)
            repository.deleteTask(childHref)
        }
    }

    fun moveToList(listHref: String) {
        viewModelScope.launch {
            repository.moveTask(href, listHref)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            reminderScheduler.cancel(href)
            repository.deleteTask(href)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}
