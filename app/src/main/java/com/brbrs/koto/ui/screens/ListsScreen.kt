package com.brbrs.koto.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.koto.data.TaskList
import com.brbrs.koto.icons.IconCatalog
import com.brbrs.koto.ui.BottomBarHeight
import com.brbrs.koto.ui.components.ConfirmDialog
import com.brbrs.koto.ui.components.EmptyState
import com.brbrs.koto.ui.components.KotoHeader
import com.brbrs.koto.ui.components.ListColorPalette
import com.brbrs.koto.ui.components.IconPickerDialog
import com.brbrs.koto.ui.components.ListTile
import com.brbrs.koto.ui.components.ScreenBackground
import com.brbrs.koto.ui.components.TitleColorDialog
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.kotoCard
import com.brbrs.koto.ui.viewmodels.ListRow
import com.brbrs.koto.ui.viewmodels.ListsViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.HideImage
import java.io.File


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListsScreen(
    onListClick: (String) -> Unit,
    onTags: () -> Unit,
    onSettings: () -> Unit,
    onThemeToggle: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    val barInset = BottomBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var createOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val rows = if (query.isBlank()) uiState.rows
               else uiState.rows.filter { it.list.displayName.contains(query, ignoreCase = true) }
    var editing by remember { mutableStateOf<TaskList?>(null) }
    var deleting by remember { mutableStateOf<TaskList?>(null) }
    var iconFor by remember { mutableStateOf<ListRow?>(null) }

    // Which list href is waiting for a photo pick
    var coverPickHref by remember { mutableStateOf<String?>(null) }
    val coverPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val href = coverPickHref ?: return@rememberLauncherForActivityResult
        coverPickHref = null
        if (uri != null) viewModel.pickCover(href, uri)
    }

    LaunchedEffect(uiState.message) { uiState.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    if (createOpen) {
        TitleColorDialog(
            title = "New list",
            confirmText = "Create",
            initialColor = ListColorPalette.first(),
            onDismiss = { createOpen = false },
        ) { name, color -> createOpen = false; viewModel.createList(name, color) }
    }
    editing?.let { list ->
        TitleColorDialog(
            title = "Edit list",
            confirmText = "Save",
            initialTitle = list.displayName,
            initialColor = list.color,
            onDismiss = { editing = null },
        ) { name, color -> editing = null; viewModel.renameList(list.href, name, color) }
    }
    iconFor?.let { row ->
        IconPickerDialog(
            currentIcon = row.iconName,
            onDismiss = { iconFor = null },
        ) { icon -> iconFor = null; viewModel.setIcon(row.list.href, icon) }
    }
    deleting?.let { list ->
        ConfirmDialog(
            title = "Delete \"${list.displayName}\"?",
            body = "The list and every task in it are removed from your Nextcloud for good. " +
                "If you just want it out of the way, hide it instead.",
            confirmText = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { deleting = null; viewModel.deleteList(list.href) },
        )
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            KotoHeader(
                query = query,
                onQueryChange = { query = it },
                searchOpen = searching,
                onSearchOpenChange = { searching = it },
                onThemeToggle = onThemeToggle,
                onSettings = onSettings,
                searchPlaceholder = "Search lists",
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Lists", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (uiState.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    TextButton(onClick = onTags) {
                        Icon(Icons.Outlined.Sell, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tags", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { createOpen = true }) {
                        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (rows.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.PlaylistAddCheck,
                    title = "No task lists",
                    body = "Koto reads the task lists on your Nextcloud. Create one here, or in Nextcloud Tasks on the web.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = barInset + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.list.href }) { row ->
                        ListCard(
                            row = row,
                            isDefault = uiState.settings.defaultListHref == row.list.href,
                            isDark = isDark,
                            coverFile = viewModel.localCoverFile(row.coverToken),
                            onClick = { viewModel.openList(row.list.href); onListClick(row.list.href) },
                            onEdit = { editing = row.list },
                            onDelete = { deleting = row.list },
                            onToggleHidden = { viewModel.toggleHidden(row.list.href) },
                            onSetDefault = { viewModel.setDefault(row.list.href) },
                            onPickIcon = { iconFor = row },
                            onPickCover = {
                                coverPickHref = row.list.href
                                coverPickLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemoveCover = { viewModel.removeCover(row.list.href) },
                        )
                    }
                    item(key = "footnote") {
                        Text(
                            "Long-press a list for its icon, name, color and the rest. Hidden lists stay on " +
                                "your Nextcloud and keep syncing; they're just left out of the combined views here.",
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barInset + 16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListCard(
    row: ListRow,
    isDefault: Boolean,
    isDark: Boolean,
    coverFile: File? = null,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleHidden: () -> Unit,
    onSetDefault: () -> Unit,
    onPickIcon: () -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    // Long-press for the menu, matching Tavla's boards. Keeping the actions off the row itself
    // leaves it clean, and the row's whole job is to be tapped.
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .kotoCard(isDark, 14.dp)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ListTile(row.list.color, iconName = row.iconName, coverFile = coverFile)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.list.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (row.hidden) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (row.openCount == 1) "1 task" else "${row.openCount} tasks")
                        if (row.overdueCount > 0) append(" · ${row.overdueCount} overdue")
                        if (row.list.readOnly) append(" · read only")
                        if (isDefault) append(" · default")
                        if (row.hidden) append(" · hidden")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.overdueCount > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isDefault) {
                Icon(Icons.Outlined.Star, "Default list", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            if (row.hidden) {
                Icon(Icons.Outlined.VisibilityOff, "Hidden", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Choose icon") },
                leadingIcon = { Icon(IconCatalog.iconFor(row.iconName), null) },
                onClick = { menuOpen = false; onPickIcon() },
            )
            // Cover image
            DropdownMenuItem(
                text = { Text(if (coverFile != null) "Change cover image" else "Choose cover image") },
                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                onClick = { menuOpen = false; onPickCover() },
            )
            if (coverFile != null) {
                DropdownMenuItem(
                    text = { Text("Remove cover image") },
                    leadingIcon = { Icon(Icons.Outlined.HideImage, null) },
                    onClick = { menuOpen = false; onRemoveCover() },
                )
            }
            if (!row.list.readOnly) {
                DropdownMenuItem(
                    text = { Text("Edit name and color") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menuOpen = false; onEdit() },
                )
            }
            DropdownMenuItem(
                text = { Text(if (isDefault) "Already the default" else "Use as default list") },
                leadingIcon = { Icon(if (isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder, null) },
                onClick = { menuOpen = false; if (!isDefault) onSetDefault() },
            )
            DropdownMenuItem(
                text = { Text(if (row.hidden) "Show in combined views" else "Hide from combined views") },
                leadingIcon = { Icon(if (row.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null) },
                onClick = { menuOpen = false; onToggleHidden() },
            )
            if (!row.list.readOnly) {
                DropdownMenuItem(
                    text = { Text("Delete list", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
