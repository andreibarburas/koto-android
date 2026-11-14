package com.brbrs.koto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.koto.data.TaskNode
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.reminders.ReminderScheduler
import com.brbrs.koto.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class AgendaDay(val date: LocalDate, val nodes: List<TaskNode>)

data class AgendaUiState(
    val overdue: List<TaskNode> = emptyList(),
    val days: List<AgendaDay> = emptyList(),
    val undated: Int = 0,
    val loading: Boolean = true,
)

/**
 * A flat, dated timeline across every list. This is the view the Nextcloud Tasks web UI
 * doesn't give you, and the reason Koto is worth having on a phone rather than a bookmark.
 */
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.allTasks, settingsRepository.settings) { nodes, settings ->
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val visible = nodes
                    .filterNot { it.task.listHref in settings.hiddenLists }
                    .filterNot { it.task.isCompleted || it.task.todo.isCancelled }

                val dated = visible.mapNotNull { node ->
                    val d = node.task.todo.effectiveDate ?: return@mapNotNull null
                    Instant.ofEpochMilli(d.millis).atZone(zone).toLocalDate() to node
                }
                AgendaUiState(
                    overdue = dated.filter { it.first.isBefore(today) }
                        .sortedBy { it.first }
                        .map { it.second },
                    days = dated.filterNot { it.first.isBefore(today) }
                        .groupBy { it.first }
                        .toSortedMap()
                        .map { (date, pairs) ->
                            AgendaDay(
                                date,
                                pairs.map { it.second }
                                    .sortedBy { it.task.todo.effectiveDate?.millis ?: Long.MAX_VALUE },
                            )
                        },
                    undated = visible.count { it.task.todo.effectiveDate == null },
                    loading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setCompleted(href: String, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(href, completed)
            if (completed) reminderScheduler.cancel(href)
        }
    }
}
