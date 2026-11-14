package com.brbrs.koto.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.koto.ui.components.ColorPickerDialog
import com.brbrs.koto.ui.components.ConfirmDialog
import com.brbrs.koto.ui.components.EmptyState
import com.brbrs.koto.ui.components.ScreenBackground
import com.brbrs.koto.ui.components.TextInputDialog
import com.brbrs.koto.ui.components.colorFromHex
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.kotoCard
import com.brbrs.koto.ui.viewmodels.TagRow
import com.brbrs.koto.ui.viewmodels.TagsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<TagRow?>(null) }
    var coloring by remember { mutableStateOf<TagRow?>(null) }
    var deleting by remember { mutableStateOf<TagRow?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    renaming?.let { tag ->
        TextInputDialog(
            title = "Rename tag",
            label = "Tag",
            confirmText = "Rename",
            initial = tag.name,
            onDismiss = { renaming = null },
        ) { newName -> renaming = null; viewModel.rename(tag.name, newName) }
    }
    coloring?.let { tag ->
        ColorPickerDialog(
            title = "Color for #${tag.name}",
            current = tag.color,
            onDismiss = { coloring = null },
            onClear = { coloring = null; viewModel.setColor(tag.name, null) },
        ) { hex -> coloring = null; viewModel.setColor(tag.name, hex) }
    }
    deleting?.let { tag ->
        ConfirmDialog(
            title = "Remove #${tag.name}?",
            body = "The tag is taken off ${tag.count} task${if (tag.count == 1) "" else "s"}. " +
                "The tasks themselves are not touched.",
            confirmText = "Remove",
            onDismiss = { deleting = null },
            onConfirm = { deleting = null; viewModel.delete(tag.name) },
        )
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Text("Tags", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (uiState.busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp).padding(end = 4.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                }
            }

            when {
                uiState.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                uiState.tags.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Sell,
                    title = "No tags yet",
                    body = "Add a tag to a task and it shows up here, where you can rename it, " +
                        "give it a color, or take it off everything at once.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.tags, key = { it.name }) { tag ->
                        TagCard(
                            tag = tag,
                            isDark = isDark,
                            onRename = { renaming = tag },
                            onColor = { coloring = tag },
                            onDelete = { deleting = tag },
                        )
                    }
                    item(key = "note") {
                        Text(
                            "Renaming a tag rewrites every task that carries it, so it can take a moment " +
                                "on a big list. Colors are stored in your own Nextcloud; iCalendar has " +
                                "nowhere to put them, so other clients show the tag without its color.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagCard(
    tag: TagRow,
    isDark: Boolean,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val dot = tag.color?.let { colorFromHex(it) } ?: MaterialTheme.colorScheme.secondary
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .kotoCard(isDark, 14.dp)
                .combinedClickable(onClick = { menuOpen = true }, onLongClick = { menuOpen = true })
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(dot))
            Column(Modifier.weight(1f)) {
                Text(
                    tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (tag.count == 1) "1 task" else "${tag.count} tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = { menuOpen = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Choose color") },
                leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                onClick = { menuOpen = false; onColor() },
            )
            DropdownMenuItem(
                text = { Text("Remove from all tasks", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}
