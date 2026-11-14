package com.brbrs.koto.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.koto.data.TaskNode
import com.brbrs.koto.ui.BottomBarHeight
import com.brbrs.koto.ui.components.DueBadge
import com.brbrs.koto.ui.components.EmptyState
import com.brbrs.koto.ui.components.KotoHeader
import com.brbrs.koto.ui.components.ListDot
import com.brbrs.koto.ui.components.ScreenBackground
import com.brbrs.koto.ui.components.SectionLabel
import com.brbrs.koto.ui.components.TaskCheckbox
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.kotoCard
import com.brbrs.koto.ui.viewmodels.AgendaViewModel
import com.brbrs.koto.util.formatDayHeader

/**
 * Every dated task across every list, in date order. The one view Nextcloud Tasks on the web
 * doesn't offer, and the reason to keep this on a phone.
 */
@Composable
fun AgendaScreen(
    onTaskClick: (String) -> Unit,
    onSettings: () -> Unit,
    onThemeToggle: () -> Unit,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val barInset = BottomBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    fun List<TaskNode>.matching(): List<TaskNode> =
        if (query.isBlank()) this else filter { it.task.title.contains(query, ignoreCase = true) ||
            it.task.todo.description.orEmpty().contains(query, ignoreCase = true) ||
            it.task.todo.categories.any { t -> t.contains(query, ignoreCase = true) } }

    val overdue = uiState.overdue.matching()
    val days = uiState.days.map { it.copy(nodes = it.nodes.matching()) }.filter { it.nodes.isNotEmpty() }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            KotoHeader(
                query = query,
                onQueryChange = { query = it },
                searchOpen = searching,
                onSearchOpenChange = { searching = it },
                onThemeToggle = onThemeToggle,
                onSettings = onSettings,
                searchPlaceholder = "Search the agenda",
            )
            Text(
                "Agenda",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
            )

            when {
                uiState.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                overdue.isEmpty() && days.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.EventAvailable,
                    title = if (query.isNotBlank()) "Nothing matches" else "Nothing scheduled",
                    body = if (query.isNotBlank()) "Try a shorter search." else if (uiState.undated > 0)
                        "You have ${uiState.undated} task${if (uiState.undated == 1) "" else "s"} without a date. Give one a due date and it appears here."
                    else "Tasks with a due date show up here, in order.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = barInset + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (overdue.isNotEmpty()) {
                        item(key = "overdue-header") {
                            SectionLabel(
                                "Overdue",
                                trailing = overdue.size.toString(),
                                accent = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(overdue, key = { "o-" + it.task.href }) { node ->
                            AgendaRow(node, isDark, { onTaskClick(node.task.href) }) {
                                viewModel.setCompleted(node.task.href, it)
                            }
                        }
                    }
                    days.forEach { day ->
                        item(key = "d-${day.date}") {
                            SectionLabel(
                                formatDayHeader(day.date),
                                trailing = day.nodes.size.toString(),
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        items(day.nodes, key = { "t-" + it.task.href }) { node ->
                            AgendaRow(node, isDark, { onTaskClick(node.task.href) }) {
                                viewModel.setCompleted(node.task.href, it)
                            }
                        }
                    }
                    if (uiState.undated > 0) {
                        item(key = "undated") {
                            Text(
                                "${uiState.undated} task${if (uiState.undated == 1) "" else "s"} with no date",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 20.dp, start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(node: TaskNode, isDark: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val task = node.task
    Row(
        modifier = Modifier.fillMaxWidth().kotoCard(isDark, 12.dp).clickable { onClick() }.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(checked = task.isCompleted, priority = task.todo.priority, onCheckedChange = onToggle, size = 18.dp)
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                task.todo.effectiveDate?.let { DueBadge(it, dimmed = task.isCompleted) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ListDot(node.listColor, 6.dp)
                    Text(node.listName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
