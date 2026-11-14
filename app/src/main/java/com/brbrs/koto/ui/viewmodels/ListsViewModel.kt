package com.brbrs.koto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.brbrs.koto.covers.CoverRepository
import com.brbrs.koto.data.ListSelection
import com.brbrs.koto.data.TaskList
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.settings.KotoSettings
import com.brbrs.koto.settings.SettingsRepository
import com.brbrs.koto.ui.components.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListRow(
    val list: TaskList,
    val openCount: Int,
    val overdueCount: Int,
    val hidden: Boolean,
    val iconName: String?,
    val coverToken: String?,
)

data class ListsUiState(
    val rows: List<ListRow> = emptyList(),
    val settings: KotoSettings = KotoSettings(),
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val listSelection: ListSelection,
    private val coverRepository: CoverRepository,
) : ViewModel() {

    /** Called when a list is tapped: scopes the Tasks screen to it before navigating there. */
    fun openList(href: String) = listSelection.open(href)

    private val _transient = MutableStateFlow(false to null as String?)
    private val _uiState = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.lists,
                repository.allTasks,
                settingsRepository.settings,
                _transient,
            ) { lists, nodes, settings, transient ->
                val now = System.currentTimeMillis()
                // Children count towards their list too; a parent with five open subtasks
                // isn't "1 task left".
                val flat = nodes.flatMap { listOf(it.task) + it.children }
                ListsUiState(
                    rows = lists.map { l ->
                        val mine = flat.filter { it.listHref == l.href && !it.isCompleted }
                        ListRow(
                            list = l,
                            openCount = mine.size,
                            overdueCount = mine.count { t ->
                                val d = t.todo.effectiveDate ?: return@count false
                                com.brbrs.koto.util.bucketFor(d, now) == com.brbrs.koto.data.TaskBucket.Overdue
                            },
                            hidden = l.href in settings.hiddenLists,
                            iconName = settings.listIcons[l.href],
                            coverToken = settings.listCovers[l.href],
                        )
                    },
                    settings = settings,
                    busy = transient.first,
                    message = transient.second,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun consumeMessage() { _transient.update { it.first to null } }

    fun createList(name: String, color: String) {
        viewModelScope.launch {
            _transient.value = true to null
            val result = repository.createList(name, color)
            _transient.value = false to result.exceptionOrNull()?.let {
                // MKCALENDAR is the one call a locked-down Nextcloud is most likely to refuse,
                // so say what to do rather than just showing the status code.
                if ((it as? com.brbrs.koto.network.CalDavException)?.code == 403)
                    "Your Nextcloud doesn't allow creating calendars from an app. Make the list on the web instead."
                else friendlyError(it)
            }
        }
    }

    fun renameList(href: String, name: String, color: String) {
        viewModelScope.launch {
            val result = repository.renameList(href, name, color)
            _transient.value = false to result.exceptionOrNull()?.let { friendlyError(it) }
        }
    }

    fun deleteList(href: String) {
        viewModelScope.launch {
            _transient.value = true to null
            val result = repository.deleteList(href)
            _transient.value = false to (result.exceptionOrNull()?.let { friendlyError(it) } ?: "List deleted")
        }
    }

    fun setIcon(href: String, icon: String?) = settingsRepository.setListIcon(href, icon)
    fun toggleHidden(href: String) = settingsRepository.toggleHiddenList(href)
    fun setDefault(href: String) = settingsRepository.setDefaultList(href)

    fun pickCover(href: String, uri: Uri) = coverRepository.pickCover(href, uri)
    fun removeCover(href: String) = coverRepository.removeCover(href)
    fun localCoverFile(token: String?) = coverRepository.localFile(token)

}