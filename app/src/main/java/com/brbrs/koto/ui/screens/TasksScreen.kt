package com.brbrs.koto.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.koto.R
import com.brbrs.koto.ui.BottomBarHeight
import com.brbrs.koto.data.Task
import com.brbrs.koto.data.TaskFilter
import com.brbrs.koto.data.TaskNode
import com.brbrs.koto.ui.components.DueBadge
import com.brbrs.koto.ui.components.EmptyState
import com.brbrs.koto.ui.components.rememberReorderState
import com.brbrs.koto.ui.components.reorderable
import com.brbrs.koto.ui.components.KotoHeader
import com.brbrs.koto.ui.components.ListDot
import com.brbrs.koto.ui.components.ListTile
import com.brbrs.koto.ui.components.ScreenBackground
import com.brbrs.koto.ui.components.SectionLabel
import com.brbrs.koto.ui.components.TagChip
import com.brbrs.koto.ui.components.TaskCheckbox
import com.brbrs.koto.ui.components.TopBarIcon
import com.brbrs.koto.ui.theme.FabShape
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.kotoCard
import com.brbrs.koto.ui.theme.kotoChip
import com.brbrs.koto.ui.viewmodels.TasksViewModel
import com.brbrs.koto.util.QuickAdd
import com.brbrs.koto.util.bucketLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File
import androidx.compose.ui.platform.LocalContext


@Composable
fun TasksScreen(
    onTaskClick: (String) -> Unit,
    onSettings: () -> Unit,
    onThemeToggle: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var searching by remember { mutableStateOf(false) }
    var quickAdd by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // While a drag is in flight the rows are reordered locally; the write happens on drop,
    // because committing on every frame would mean a CalDAV PUT per pixel of movement.
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    val manualSort = uiState.settings.sortMode == "manual"
    // The bottom bar is drawn over the content, so everything anchored low has to clear it.
    val barInset = BottomBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !manualSort, // a drag near the edge shouldn't open the drawer
        drawerContent = {
            ListDrawer(
                lists = uiState.lists,
                counts = uiState.listCounts,
                totalOpen = uiState.totalOpen,
                icons = uiState.settings.listIcons,
                covers = uiState.settings.listCovers,
                selected = uiState.activeListHref,
                isDark = isDark,
            ) { href ->
                viewModel.setActiveList(href)
                scope.launch { drawerState.close() }
            }
        },
    ) {
    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

            KotoHeader(
                onMenu = { scope.launch { drawerState.open() } },
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
                searchOpen = searching,
                onSearchOpenChange = { searching = it },
                onThemeToggle = onThemeToggle,
                onSettings = onSettings,
                searchPlaceholder = "Search every task",
                syncing = uiState.syncing,
            )

            // ── Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskFilter.entries.forEach { filter ->
                    val selected = uiState.filter == filter
                    val count = uiState.counts[filter] ?: 0
                    Box(
                        modifier = Modifier
                            .kotoChip(isDark = isDark, selected = selected)
                            .clickable { viewModel.setFilter(filter) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (count > 0) "${filter.label()}  $count" else filter.label(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            // ── The list
            Box(Modifier.weight(1f)) {
                when {
                    uiState.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    uiState.sections.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.CheckCircle,
                        title = emptyTitle(uiState.filter, uiState.query),
                        body = emptyBody(uiState.filter, uiState.query),
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    else -> {
                        val sections = uiState.sections
                        // Flat list of hrefs in display order, which is what a drag rearranges.
                        val displayed = remember(sections, dragOrder) {
                            val natural = sections.flatMap { sec -> sec.nodes.map { it.task.href } }
                            dragOrder?.takeIf { it.toSet() == natural.toSet() } ?: natural
                        }
                        val byHref = remember(sections) {
                            sections.flatMap { it.nodes }.associateBy { it.task.href }
                        }
                        val reorder = rememberReorderState(
                            listState = listState,
                            onMove = { from, to ->
                                val current = (dragOrder ?: displayed).toMutableList()
                                // Indices are into the LazyColumn, which also holds headers;
                                // map back through the keys instead of trusting the raw index.
                                val info = listState.layoutInfo.visibleItemsInfo
                                val fromKey = info.firstOrNull { it.index == from }?.key as? String
                                val toKey = info.firstOrNull { it.index == to }?.key as? String
                                if (fromKey != null && toKey != null) {
                                    val f = current.indexOf(fromKey)
                                    val t = current.indexOf(toKey)
                                    if (f >= 0 && t >= 0) {
                                        current.add(t, current.removeAt(f))
                                        dragOrder = current
                                    }
                                }
                            },
                            onDrop = {
                                dragOrder?.let { viewModel.reorder(it) }
                                dragOrder = null
                            },
                        )
                        LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = barInset + 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (manualSort) {
                            // One flat, draggable list. Grouping by due date and letting the
                            // user impose an order at the same time would contradict itself.
                            items(displayed, key = { it }) { href ->
                                val node = byHref[href] ?: return@items
                                TaskCard(
                                    node = node,
                                    showList = uiState.activeListHref == null && uiState.lists.size > 1,
                                    dragging = reorder.draggingKey == href,
                                    dragOffset = if (reorder.draggingKey == href) reorder.dragOffset else 0f,
                                    onClick = { onTaskClick(node.task.href) },
                                    onToggle = { done -> viewModel.setCompleted(node.task.href, done) },
                                    onToggleChild = { child, done -> viewModel.setCompleted(child.href, done) },
                                    onChildClick = { child -> onTaskClick(child.href) },
                                    tagColors = uiState.settings.tagColors,
                                    modifier = Modifier.reorderable(reorder, href, enabled = true),
                                )
                            }
                        } else {
                            uiState.sections.forEach { section ->
                                item(key = "h-${section.bucket}") {
                                    SectionLabel(
                                        bucketLabel(section.bucket),
                                        trailing = section.nodes.size.toString(),
                                        accent = if (section.bucket == com.brbrs.koto.data.TaskBucket.Overdue)
                                            MaterialTheme.colorScheme.error else null,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                items(section.nodes, key = { it.task.href }) { node ->
                                    TaskCard(
                                        node = node,
                                        showList = uiState.activeListHref == null && uiState.lists.size > 1,
                                        onClick = { onTaskClick(node.task.href) },
                                        onToggle = { done -> viewModel.setCompleted(node.task.href, done) },
                                        onToggleChild = { child, done -> viewModel.setCompleted(child.href, done) },
                                        onChildClick = { child -> onTaskClick(child.href) },
                                        tagColors = uiState.settings.tagColors,
                                    )
                                }
                            }
                        }
                    }
                    }
                }

                // ── Quick add
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .imePadding()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .padding(bottom = barInset + 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    AnimatedVisibility(addOpen) {
                        val parsed = remember(quickAdd, uiState.settings.smartQuickAdd) {
                            QuickAdd.parse(quickAdd, uiState.settings.smartQuickAdd)
                        }
                        Column(modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp).padding(12.dp)) {
                            OutlinedTextField(
                                value = quickAdd,
                                onValueChange = { quickAdd = it },
                                placeholder = { Text("Add a task") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    viewModel.quickAdd(quickAdd, uiState.activeListHref)
                                    quickAdd = ""
                                }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = searchFieldColors(),
                            )
                            val hint = QuickAdd.hint(parsed)
                            if (quickAdd.isNotBlank() && hint != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            // A shadow is what separates the button from the list behind it;
                            // without one the flat accent fill reads as part of the background.
                            .shadow(10.dp, FabShape, clip = false)
                            .clip(FabShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                if (addOpen && quickAdd.isNotBlank()) {
                                    viewModel.quickAdd(quickAdd, uiState.activeListHref)
                                    quickAdd = ""
                                } else {
                                    addOpen = !addOpen
                                    if (!addOpen) focusManager.clearFocus()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (addOpen && quickAdd.isBlank()) Icons.Outlined.Close else Icons.Outlined.Add,
                            contentDescription = if (addOpen) "Add task" else "New task",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = barInset + 80.dp),
        )
    }
    }
}

/**
 * The task lists, in a drawer rather than a row of pills.
 *
 * Pills were fine for three lists and unusable past six: they ate a row of vertical space and
 * pushed everything but the first few off-screen. A drawer scrolls, shows each list's icon and
 * open count, and leaves the whole screen to the tasks themselves.
 */
@Composable
private fun ListDrawer(
    lists: List<com.brbrs.koto.data.TaskList>,
    counts: Map<String, Int>,
    totalOpen: Int,
    icons: Map<String, String>,
    covers: Map<String, String> = emptyMap(),
    selected: String?,
    isDark: Boolean,
    onSelect: (String?) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
        ) {
            Text(
                "Task lists",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 12.dp),
            )

            DrawerRow(
                title = "All lists",
                count = totalOpen,
                colorHex = null,
                iconName = null,
                selected = selected == null,
                isDark = isDark,
            ) { onSelect(null) }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))

            lists.forEach { list ->
                DrawerRow(
                    title = list.displayName,
                    count = counts[list.href] ?: 0,
                    colorHex = list.color,
                    iconName = icons[list.href],
                    coverToken = covers[list.href],
                    selected = selected == list.href,
                    isDark = isDark,
                ) { onSelect(list.href) }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Manage lists, icons and colors on the Lists tab.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun DrawerRow(
    title: String,
    count: Int,
    colorHex: String?,
    iconName: String?,
    coverToken: String? = null,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    // Resolve cover file here (inside @Composable scope) so LocalContext.current works.
    val coverFile: java.io.File? = coverToken?.let {
        java.io.File(LocalContext.current.filesDir, "covers/$it.jpg").takeIf { f -> f.exists() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.12f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (colorHex != null) {
            ListTile(colorHex, size = 34.dp, iconName = iconName, coverFile = coverFile)
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.AllInbox, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun TaskFilter.label() = when (this) {
    TaskFilter.Today -> "Today"
    TaskFilter.Upcoming -> "Upcoming"
    TaskFilter.All -> "All"
    TaskFilter.Completed -> "Done"
    TaskFilter.Everything -> "Everything"
}

private fun emptyTitle(filter: TaskFilter, query: String) = when {
    query.isNotBlank() -> "Nothing matches"
    filter == TaskFilter.Today -> "Nothing due today"
    filter == TaskFilter.Completed -> "Nothing finished yet"
    filter == TaskFilter.Everything -> "This list is empty"
    else -> "No tasks"
}

private fun emptyBody(filter: TaskFilter, query: String) = when {
    query.isNotBlank() -> "Try a shorter search, or a different word."
    filter == TaskFilter.Today -> "Anything overdue would show here too. Add a task with the button below."
    filter == TaskFilter.Completed -> "Tasks you tick off appear here for a while."
    filter == TaskFilter.Everything -> "Every task in scope would show here, finished ones included."
    else -> "Add your first task with the button below."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    node: TaskNode,
    showList: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleChild: (Task, Boolean) -> Unit,
    onChildClick: (Task) -> Unit,
    tagColors: Map<String, String>,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    dragOffset: Float = 0f,
) {
    val isDark = LocalIsDark.current
    val task = node.task
    val done = task.isCompleted

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .shadow(if (dragging) 12.dp else 0.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp), clip = false)
            .kotoCard(isDark, 14.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TaskCheckbox(checked = done, priority = task.todo.priority, onCheckedChange = onToggle)
            // The checkbox's 44dp touch target centers its square 22dp down. Match the title's
            // first line to that rather than to the top of the card, or the square floats high.
            Column(modifier = Modifier.weight(1f).padding(top = 10.dp, bottom = 14.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList<@Composable () -> Unit> {
                    task.todo.effectiveDate?.let { d -> add { DueBadge(d, dimmed = done) } }
                    if (node.children.isNotEmpty()) add {
                        MetaText("${node.doneChildren}/${node.children.size} subtasks", Icons.Outlined.CheckCircle)
                    }
                    if (task.todo.rrule != null) add { MetaText("Repeats", Icons.Outlined.Repeat) }
                    if (!task.todo.description.isNullOrBlank()) add { MetaText("Notes", Icons.Outlined.Subject) }
                    if (showList) add {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            ListDot(node.listColor, 7.dp)
                            Text(
                                node.listName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) { meta.forEach { it() } }
                }
                if (task.todo.categories.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) { task.todo.categories.take(4).forEach { TagChip(it, colorHex = tagColors[it]) } }
                }
            }
            if (task.pending) {
                // A small, quiet marker: the edit is saved locally and will reach the server.
                Box(
                    Modifier.padding(top = 16.dp).size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                )
            }
        }

        // Subtasks, inline. A parent whose children are hidden here is a parent nobody trusts.
        if (node.children.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 30.dp, end = 12.dp, bottom = 10.dp)) {
                node.children.forEach { child ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onChildClick(child) },
                    ) {
                        TaskCheckbox(
                            checked = child.isCompleted,
                            priority = child.todo.priority,
                            onCheckedChange = { onToggleChild(child, it) },
                            size = 15.dp,
                        )
                        Text(
                            child.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (child.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            textDecoration = if (child.isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        child.todo.effectiveDate?.let {
                            Spacer(Modifier.width(8.dp))
                            DueBadge(it, dimmed = child.isCompleted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaText(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun searchFieldColors() = com.brbrs.koto.ui.components.headerFieldColors()
