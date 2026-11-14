package com.brbrs.koto.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brbrs.koto.data.TaskRepository
import com.brbrs.koto.settings.SettingsRepository
import com.brbrs.koto.ui.components.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagRow(val name: String, val count: Int, val color: String?)

data class TagsUiState(
    val tags: List<TagRow> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Tags across every list.
 *
 * A tag in iCalendar is just a string inside CATEGORIES, repeated on each task. It has no
 * identity, so renaming one means rewriting every task that carries it -- one CalDAV PUT
 * each. That is why these operations show progress and are not instant.
 */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagsUiState())
    val uiState: StateFlow<TagsUiState> = _uiState.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val counts = repository.tagCounts()
            val colors = settingsRepository.settings.value.tagColors
            _uiState.update {
                it.copy(
                    loading = false,
                    tags = counts.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { e -> e.value }
                            .thenBy { e -> e.key.lowercase() })
                        .map { (name, count) -> TagRow(name, count, colors[name]) },
                )
            }
        }
    }

    fun consumeMessage() { _uiState.update { it.copy(message = null) } }

    fun rename(from: String, to: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = runCatching { repository.renameTag(from, to.trim()) }
            settingsRepository.renameTagColor(from, to.trim())
            _uiState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { n -> "Renamed on $n task${if (n == 1) "" else "s"}" },
                        onFailure = { e -> friendlyError(e) },
                    ),
                )
            }
            reload()
        }
    }

    fun delete(tag: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val result = runCatching { repository.deleteTag(tag) }
            settingsRepository.forgetTagColor(tag)
            _uiState.update {
                it.copy(
                    busy = false,
                    message = result.fold(
                        onSuccess = { n -> "Removed from $n task${if (n == 1) "" else "s"}" },
                        onFailure = { e -> friendlyError(e) },
                    ),
                )
            }
            reload()
        }
    }

    fun setColor(tag: String, color: String?) {
        settingsRepository.setTagColor(tag, color)
        reload()
    }
}
