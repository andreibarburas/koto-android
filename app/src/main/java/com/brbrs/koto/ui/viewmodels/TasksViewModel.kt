package com.brbrs.koto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.koto.data.Task
import com.brbrs.koto.data.TaskBucket
import com.brbrs.koto.data.TaskFilter
import com.brbrs.koto.data.TaskList
import com.brbrs.koto.data.TaskNode
import com.brbrs.koto.data.ListSelection
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.ical.VTodo
import com.brbrs.koto.reminders.ReminderScheduler
import com.brbrs.koto.settings.KotoSettings
import com.brbrs.koto.settings.SettingsRepository
import com.brbrs.koto.sync.WorkScheduler
import com.brbrs.koto.ui.components.friendlyError
import com.brbrs.koto.util.QuickAdd
import com.brbrs.koto.util.bucketFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** One rendered section of the main list. */
data class TaskSection(val bucket: TaskBucket, val nodes: List<TaskNode>)

data class TasksUiState(
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val filter: TaskFilter = TaskFilter.Today,
    val sections: List<TaskSection> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val activeListHref: String? = null,
    val query: String = "",
    val settings: KotoSettings = KotoSettings(),
    val message: String? = null,
    val counts: Map<TaskFilter, Int> = emptyMap(),
    /** Open tasks per list href, for the drawer. Counted before scoping, or every list but
     *  the selected one would read zero while the drawer is open. */
    val listCounts: Map<String, Int> = emptyMap(),
    val totalOpen: Int = 0,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val workScheduler: WorkScheduler,
    private val reminderScheduler: ReminderScheduler,
    private val listSelection: ListSelection,
    private val contactLinksSync: com.brbrs.koto.contactlinks.ContactLinksSync,
    private val taskLinksSync: com.brbrs.koto.links.TaskLinksSync,
) : ViewModel() {

    private val _filter = MutableStateFlow(TaskFilter.Today)
    private val _query = MutableStateFlow("")
    private val _transient = MutableStateFlow(TransientState())

    private data class TransientState(val syncing: Boolean = false, val message: String? = null)

    /** Everything the main screen needs, recomputed whenever the cache or any control changes. */
    private data class Controls(
        val filter: TaskFilter,
        val query: String,
        val activeList: String?,
        val transient: TransientState,
    )

    private val controls: Flow<Controls> =
        combine(_filter, _query, listSelection.selected, _transient, ::Controls)

    val uiState: StateFlow<TasksUiState> = combine(
        repository.allTasks,
        repository.lists,
        settingsRepository.settings,
        controls,
    ) { nodes, lists, settings, c ->
        // A hidden list is left out of the combined views, but scoping to it explicitly
        // (by tapping it on the Lists screen) has to win, or it would open to nothing.
        val visibleLists = lists.filterNot { it.href in settings.hiddenLists && it.href != c.activeList }
        val visibleHrefs = visibleLists.map { it.href }.toSet()
        val scoped = scopeToList(nodes.filter { it.task.listHref in visibleHrefs }, c.activeList, lists)
        val searched = if (c.query.isBlank()) scoped else scoped.filter { it.matches(c.query) }

        val everythingVisible = flatten(nodes.filter { it.task.listHref in visibleHrefs })
            .distinctBy { it.href }
            .filterNot { it.isCompleted || it.todo.isCancelled }

        TasksUiState(
            loading = false,
            syncing = c.transient.syncing,
            filter = c.filter,
            sections = buildSections(searched, c.filter, settings),
            lists = visibleLists,
            activeListHref = c.activeList,
            query = c.query,
            settings = settings,
            message = c.transient.message,
            counts = countsFor(scoped, settings),
            listCounts = everythingVisible.groupingBy { it.listHref }.eachCount(),
            totalOpen = everythingVisible.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    init {
        syncNow()
        contactLinksSync.fetchFromRemote()
        taskLinksSync.fetchFromRemote()
        // Opening a list from the Lists screen widens the filter, so the list actually shows
        // its contents instead of just today's handful.
        viewModelScope.launch {
            listSelection.showAllRequests.drop(1).collect { _filter.value = TaskFilter.All }
        }
    }

    // ── Grouping ─────────────────────────────────────────────────────────────

    private fun TaskNode.matches(query: String): Boolean {
        val q = query.trim().lowercase()
        fun Task.hit() = title.lowercase().contains(q) ||
            todo.description.orEmpty().lowercase().contains(q) ||
            todo.categories.any { it.lowercase().contains(q) }
        return task.hit() || children.any { it.hit() }
    }

    private fun buildSections(
        nodes: List<TaskNode>,
        filter: TaskFilter,
        settings: KotoSettings,
    ): List<TaskSection> {
        val now = System.currentTimeMillis()
        val today = LocalDate.now(ZoneId.systemDefault())

        if (filter == TaskFilter.Everything) {
            // Literally everything in scope, open and finished alike, in one list.
            val everything = nodes.sortedWith(comparatorFor(settings.sortMode))
            return if (everything.isEmpty()) emptyList()
                   else listOf(TaskSection(TaskBucket.Everything, everything))
        }

        if (filter == TaskFilter.Completed) {
            val cutoff = now - settings.completedRetentionDays * 24L * 60 * 60 * 1000
            val done = nodes
                .filter { it.task.isCompleted && (it.task.todo.completed ?: now) >= cutoff }
                .sortedByDescending { it.task.todo.completed ?: 0L }
            return if (done.isEmpty()) emptyList() else listOf(TaskSection(TaskBucket.Completed, done))
        }

        val open = nodes.filterNot { it.task.isCompleted || it.task.todo.isCancelled }
        val scoped = when (filter) {
            TaskFilter.Today -> open.filter { node ->
                val b = bucketFor(node.task.todo.effectiveDate, now)
                b == TaskBucket.Overdue || b == TaskBucket.Today
            }
            TaskFilter.Upcoming -> open.filter { node ->
                val due = node.task.todo.effectiveDate ?: return@filter false
                val day = java.time.Instant.ofEpochMilli(due.millis).atZone(ZoneId.systemDefault()).toLocalDate()
                !day.isBefore(today)
            }
            else -> open
        }

        val order = listOf(
            TaskBucket.Overdue, TaskBucket.Today, TaskBucket.Tomorrow,
            TaskBucket.ThisWeek, TaskBucket.Later, TaskBucket.NoDate,
        )
        return scoped
            .groupBy { bucketFor(it.task.todo.effectiveDate, now) }
            .toList()
            .sortedBy { (bucket, _) -> order.indexOf(bucket) }
            .map { (bucket, items) -> TaskSection(bucket, items.sortedWith(comparatorFor(settings.sortMode))) }
            .filter { it.nodes.isNotEmpty() }
    }

    private fun comparatorFor(mode: String): Comparator<TaskNode> = when (mode) {
        "priority" -> compareBy(
            // Priority 0 means "unset" in RFC 5545, so it must sort last, not first.
            { if (it.task.todo.priority == 0) 10 else it.task.todo.priority },
            { it.task.todo.effectiveDate?.millis ?: Long.MAX_VALUE },
        )
        "alpha" -> compareBy({ it.task.title.lowercase() })
        // Manual order comes from X-APPLE-SORT-ORDER. A task that has never been dragged has
        // none, so it sorts after the arranged ones rather than jumping to the top.
        "manual" -> compareBy(
            { it.task.todo.sortOrder ?: Int.MAX_VALUE },
            { it.task.todo.effectiveDate?.millis ?: Long.MAX_VALUE },
            { it.task.title.lowercase() },
        )
        "created" -> compareByDescending<TaskNode> { it.task.todo.created ?: 0L }
        else -> compareBy(
            { it.task.todo.effectiveDate?.millis ?: Long.MAX_VALUE },
            { if (it.task.todo.priority == 0) 10 else it.task.todo.priority },
            { it.task.title.lowercase() },
        )
    }

    /**
     * Narrows to one list.
     *
     * A subtask lives in its own collection, which is not always its parent's. Filtering only
     * on the parent's list would make such a subtask vanish from both lists: hidden under a
     * parent that got filtered out of one, and never top-level in the other. So when scoping,
     * a child in the chosen list is promoted to a row of its own, and a parent outside it keeps
     * only the children that belong.
     */
    private fun scopeToList(
        nodes: List<TaskNode>,
        activeList: String?,
        lists: List<TaskList>,
    ): List<TaskNode> {
        if (activeList == null) return nodes
        val list = lists.firstOrNull { it.href == activeList }
        val out = ArrayList<TaskNode>()
        nodes.forEach { node ->
            val parentHere = node.task.listHref == activeList
            val childrenHere = node.children.filter { it.listHref == activeList }
            when {
                // Parent in this list: keep it, with every child, wherever they live.
                parentHere -> out.add(node)
                // Parent elsewhere but children here: show those children in their own right.
                childrenHere.isNotEmpty() -> childrenHere.forEach { child ->
                    out.add(
                        node.copy(
                            task = child,
                            children = emptyList(),
                            // The promoted child belongs to the scoped list, not its parent's.
                            listName = list?.displayName ?: node.listName,
                            listColor = list?.color ?: node.listColor,
                        )
                    )
                }
            }
        }
        return out
    }

    /** Every task in these nodes, parents and children alike. Counts must not miss subtasks. */
    private fun flatten(nodes: List<TaskNode>): List<Task> =
        nodes.flatMap { listOf(it.task) + it.children }

    private fun countsFor(nodes: List<TaskNode>, settings: KotoSettings): Map<TaskFilter, Int> {
        val now = System.currentTimeMillis()
        val all = flatten(nodes).distinctBy { it.href }
        val open = all.filterNot { it.isCompleted || it.todo.isCancelled }
        val cutoff = now - settings.completedRetentionDays * 24L * 60 * 60 * 1000
        return mapOf(
            TaskFilter.Today to open.count {
                val b = bucketFor(it.todo.effectiveDate, now)
                b == TaskBucket.Overdue || b == TaskBucket.Today
            },
            TaskFilter.Upcoming to open.count { it.todo.effectiveDate != null },
            TaskFilter.All to open.size,
            TaskFilter.Everything to all.size,
            TaskFilter.Completed to all.count { it.isCompleted && (it.todo.completed ?: now) >= cutoff },
        )
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    fun setFilter(filter: TaskFilter) { _filter.value = filter }

    /**
     * Commits a drag. The screen hands back the rows in their new order; everything is written
     * as X-APPLE-SORT-ORDER so the arrangement syncs like any other edit.
     */
    fun reorder(orderedHrefs: List<String>) {
        viewModelScope.launch {
            repository.applyManualOrder(orderedHrefs)
            workScheduler.syncNow()
        }
    }
    fun setQuery(query: String) { _query.value = query }
    fun setActiveList(href: String?) = listSelection.select(href)
    fun consumeMessage() { _transient.update { it.copy(message = null) } }

    fun syncNow() {
        viewModelScope.launch {
            _transient.update { it.copy(syncing = true) }
            val result = repository.refreshAll()
            _transient.update {
                it.copy(syncing = false, message = result.exceptionOrNull()?.let { e -> friendlyError(e) })
            }
            rearmReminders()
        }
    }

    fun quickAdd(text: String, listHref: String?) {
        if (text.isBlank()) return
        val settings = uiState.value.settings
        val target = listHref ?: settings.defaultListHref
            ?: uiState.value.lists.firstOrNull { !it.readOnly }?.href
            ?: return
        viewModelScope.launch {
            val parsed = QuickAdd.parse(text, settings.smartQuickAdd)
            // When the current filter is Today and the text carried no date, "add to today"
            // is what the person meant by typing it on that screen.
            val due = parsed.due ?: if (_filter.value == TaskFilter.Today) {
                com.brbrs.koto.ical.DateVal.allDay(LocalDate.now())
            } else null
            val todo = VTodo.newTask(parsed.title).copy(
                due = due,
                priority = parsed.priority,
                categories = parsed.tags,
            )
            val href = repository.createTask(target, todo)
            reminderScheduler.reschedule(href, todo)
            // If the PUT didn't land (offline, server asleep), the row is queued; make sure a
            // worker is on the hook to retry rather than waiting for the next periodic sync.
            workScheduler.syncNow()
        }
    }

    fun setCompleted(href: String, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(href, completed)
            if (completed) reminderScheduler.cancel(href) else rearmOne(href)
        }
    }

    fun deleteTask(href: String) {
        viewModelScope.launch {
            reminderScheduler.cancel(href)
            repository.deleteTask(href)
            workScheduler.syncNow()
            _transient.update { it.copy(message = "Task deleted") }
        }
    }

    /** Pushes a task's due date out by a day without opening it. */
    fun snoozeOneDay(href: String) {
        viewModelScope.launch {
            repository.updateTask(href) { todo ->
                val current = todo.due ?: todo.start ?: return@updateTask todo
                val next = java.time.Instant.ofEpochMilli(current.millis)
                    .atZone(ZoneId.systemDefault()).plus(1, ChronoUnit.DAYS).toInstant().toEpochMilli()
                todo.copy(due = current.copy(millis = next))
            }
            rearmOne(href)
        }
    }

    private suspend fun rearmOne(href: String) {
        val until = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
        repository.dueBefore(until).firstOrNull { it.first == href }
            ?.let { (h, todo) -> reminderScheduler.reschedule(h, todo) }
    }

    private suspend fun rearmReminders() {
        val until = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
        repository.dueBefore(until).forEach { (href, todo) -> reminderScheduler.reschedule(href, todo) }
    }

}
