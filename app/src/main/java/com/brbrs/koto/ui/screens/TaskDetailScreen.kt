package com.brbrs.koto.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brbrs.koto.ical.Recurrence
import com.brbrs.koto.ui.components.ConfirmDialog
import com.brbrs.koto.ui.components.CoordinatesDialog
import com.brbrs.koto.ui.components.DuePickerDialog
import com.brbrs.koto.ui.components.ListDot
import com.brbrs.koto.ui.components.ListPickerDialog
import com.brbrs.koto.ui.components.PriorityDialog
import com.brbrs.koto.ui.components.RecurrenceDialog
import com.brbrs.koto.ui.components.ScreenBackground
import com.brbrs.koto.ui.components.SectionLabel
import com.brbrs.koto.ui.components.TagChip
import com.brbrs.koto.ui.components.TaskCheckbox
import com.brbrs.koto.ui.components.TextInputDialog
import com.brbrs.koto.ui.components.priorityLabel
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.kotoCard
import com.brbrs.koto.ui.theme.kotoChip
import com.brbrs.koto.ui.viewmodels.TaskDetailViewModel
import com.brbrs.koto.util.formatDue
import com.brbrs.koto.util.formatTimestamp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val linkedContacts by viewModel.linkedContacts.collectAsState()
    val linkSyncError by viewModel.linkSyncError.collectAsState()
    val links by viewModel.links.collectAsState()
    var showAddLink by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var sendToApp by remember { mutableStateOf<String?>(null) }
    val isDark = LocalIsDark.current
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val onOpenLink: (com.brbrs.koto.links.data.TaskLinkEntity) -> Unit = { link ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            // The other app doesn't register this scheme yet -- fall back to opening it if installed.
            runCatching { context.packageManager.getLaunchIntentForPackage(link.appPackage)?.let { context.startActivity(it) } }
        }
    }

    var showContactPicker by remember { mutableStateOf(false) }
    // Contact linking needs READ_CONTACTS -- requested lazily, only when the picker is
    // opened, since most people never touch this feature.
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showContactPicker = true }
    val requestContactPicker = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) showContactPicker = true
        else contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
    }

    var duePicker by remember { mutableStateOf(false) }
    var startPicker by remember { mutableStateOf(false) }
    var priorityPicker by remember { mutableStateOf(false) }
    var recurrencePicker by remember { mutableStateOf(false) }
    var listPicker by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var locationDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var coordsDialog by remember { mutableStateOf(false) }
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.attachFile(it) } }
    var newSubtask by remember { mutableStateOf("") }

    LaunchedEffect(uiState.deleted) { if (uiState.deleted) onBack() }
    LaunchedEffect(uiState.message) { uiState.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    val task = uiState.task
    if (uiState.loading) {
        ScreenBackground { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() } }
        return
    }
    if (task == null) {
        ScreenBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(60.dp))
                Text("This task is no longer here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val todo = task.todo

    if (duePicker) {
        DuePickerDialog(
            initial = todo.due,
            onDismiss = { duePicker = false },
            onClear = { duePicker = false; viewModel.setDue(null) },
            onConfirm = { duePicker = false; viewModel.setDue(it) },
        )
    }
    if (startPicker) {
        DuePickerDialog(
            initial = todo.start,
            onDismiss = { startPicker = false },
            onClear = { startPicker = false; viewModel.setStart(null) },
            onConfirm = { startPicker = false; viewModel.setStart(it) },
        )
    }
    if (priorityPicker) {
        PriorityDialog(todo.priority, { priorityPicker = false }) { priorityPicker = false; viewModel.setPriority(it) }
    }
    if (recurrencePicker) {
        RecurrenceDialog(todo.rrule, { recurrencePicker = false }) { recurrencePicker = false; viewModel.setRecurrence(it) }
    }
    if (listPicker) {
        ListPickerDialog(uiState.lists, task.listHref, onDismiss = { listPicker = false }) {
            listPicker = false; viewModel.moveToList(it)
        }
    }
    if (tagDialog) {
        TextInputDialog("Add a tag", "Tag", "Add", onDismiss = { tagDialog = false }) {
            tagDialog = false; viewModel.addTag(it)
        }
    }
    if (locationDialog) {
        TextInputDialog("Location", "Where", "Save", initial = todo.location.orEmpty(), onDismiss = { locationDialog = false }) {
            locationDialog = false; viewModel.setLocation(it)
        }
    }
    if (showContactPicker) {
        ContactPickerDialog(
            onDismiss = { showContactPicker = false },
            onSearch = { query -> viewModel.searchContacts(query) },
            onPick = { contact -> viewModel.linkContact(contact); showContactPicker = false },
        )
    }
    if (showAddLink) {
        AddLinkDialog(
            onDismiss = { showAddLink = false },
            onConfirm = { appPackage, appLabel, label, uri ->
                viewModel.addLink(appPackage, appLabel, label, uri)
                showAddLink = false
            },
        )
    }
    sendToApp?.let { pkg ->
        val appLabel = if (pkg == "com.brbrs.nota") "Nóta" else "Tavla"
        SendToAppDialog(
            appLabel = appLabel,
            initialTitle = todo.summary,
            body = todo.description.orEmpty(),
            onDismiss = { sendToApp = null },
            onConfirm = { title, body ->
                com.brbrs.koto.sharesend.SendToAppHelper.send(context, pkg, title, body)
                sendToApp = null
            },
        )
    }
    if (coordsDialog) {
        CoordinatesDialog(
            latitude = todo.latitude,
            longitude = todo.longitude,
            radius = todo.geoRadius,
            onDismiss = { coordsDialog = false },
            onClear = { coordsDialog = false; viewModel.setCoordinates(null, null, null) },
            onConfirm = { lat, lon, r -> coordsDialog = false; viewModel.setCoordinates(lat, lon, r) },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this task?",
            body = "It is removed from your Nextcloud for good. Subtasks are left where they are.",
            confirmText = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.delete() },
        )
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { listPicker = true }) {
                    Icon(Icons.Outlined.DriveFileMove, "Move to another list", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { moreMenuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Send to Nóta") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                            onClick = { moreMenuOpen = false; sendToApp = "com.brbrs.nota" },
                        )
                        DropdownMenuItem(
                            text = { Text("Send to Tavla") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                            onClick = { moreMenuOpen = false; sendToApp = "com.brbrs.tavla" },
                        )
                    }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }

            // ── Title with its checkbox
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Nudged down so the square sits on the title's first line, not above it.
                Box(modifier = Modifier.padding(top = 6.dp)) {
                    TaskCheckbox(
                        checked = task.isCompleted,
                        priority = todo.priority,
                        onCheckedChange = viewModel::setCompleted,
                        size = 24.dp,
                    )
                }
                EditableTitle(
                    value = todo.summary,
                    done = task.isCompleted,
                    onSave = viewModel::setTitle,
                    modifier = Modifier.weight(1f),
                )
            }

            if (task.isCompleted && todo.completed != null) {
                Text(
                    "Completed " + formatTimestamp(todo.completed!!),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp),
                )
            }
            if (task.pending) {
                Text(
                    "Saved on this device, waiting to reach Nextcloud",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 44.dp, top = 2.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Properties
            Column(modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp)) {
                PropertyRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "Due",
                    value = todo.due?.let { formatDue(it) } ?: "Not set",
                    highlight = todo.due != null,
                ) { duePicker = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PropertyRow(
                    icon = Icons.Outlined.PlayArrow,
                    label = "Start",
                    value = todo.start?.let { formatDue(it) } ?: "Not set",
                    highlight = todo.start != null,
                ) { startPicker = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PropertyRow(
                    icon = Icons.Outlined.Flag,
                    label = "Priority",
                    value = priorityLabel(todo.priority),
                    highlight = todo.priority > 0,
                ) { priorityPicker = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PropertyRow(
                    icon = Icons.Outlined.Repeat,
                    label = "Repeat",
                    value = Recurrence.describe(todo.rrule) ?: "Never",
                    highlight = todo.rrule != null,
                ) { recurrencePicker = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PropertyRow(
                    icon = Icons.Outlined.Place,
                    label = "Location",
                    value = todo.location ?: "Not set",
                    highlight = todo.location != null,
                ) { locationDialog = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                PropertyRow(
                    icon = Icons.Outlined.MyLocation,
                    label = "Remind me here",
                    value = if (todo.hasLocation)
                        "%.4f, %.4f · %dm".format(todo.latitude, todo.longitude, todo.geoRadius ?: 200)
                    else "Not set",
                    highlight = todo.hasLocation,
                ) { coordsDialog = true }
            }

            Spacer(Modifier.height(10.dp))

            // ── Reminder
            Column(modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Reminder", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "A reminder set here is stored on the task itself, so other clients see it too. " +
                        "Leave it on Default to use the offsets from Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                val currentAlarm = todo.alarms.firstOrNull()?.minutesBefore
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Default", 0 to "On time", 60 to "1 hour", 1440 to "1 day", 10080 to "1 week")
                        .forEach { (minutes, label) ->
                            val selected = currentAlarm == minutes && (minutes != null || todo.alarms.isEmpty())
                            Box(
                                modifier = Modifier
                                    .kotoChip(isDark = isDark, selected = selected)
                                    .clickable { viewModel.setAlarm(minutes) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Progress
            Column(modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp).padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Progress", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${todo.percentComplete ?: 0}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (todo.percentComplete ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(0, 25, 50, 75, 100).forEach { p ->
                        val selected = (todo.percentComplete ?: 0) == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .kotoChip(isDark = isDark, selected = selected)
                                .clickable { viewModel.setPercent(if (p == 0) null else p) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$p",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Tags
            SectionLabel("Tags")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                todo.categories.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagChip(tag)
                        IconButton(onClick = { viewModel.removeTag(tag) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Close, "Remove $tag", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                TextButton(onClick = { tagDialog = true }) {
                    Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Notes
            SectionLabel("Notes")
            Spacer(Modifier.height(8.dp))
            EditableNotes(value = todo.description.orEmpty(), onSave = viewModel::setDescription)

            Spacer(Modifier.height(18.dp))

            // ── Subtasks
            SectionLabel(
                "Subtasks",
                trailing = if (uiState.children.isEmpty()) null
                           else "${uiState.children.count { it.isCompleted }}/${uiState.children.size}",
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp)) {
                uiState.children.forEach { child ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 6.dp)) {
                        TaskCheckbox(
                            checked = child.isCompleted,
                            priority = child.todo.priority,
                            onCheckedChange = { viewModel.setChildCompleted(child.href, it) },
                            size = 17.dp,
                        )
                        Text(
                            child.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (child.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (child.isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenTask(child.href) }
                                .padding(vertical = 10.dp),
                        )
                        IconButton(onClick = { viewModel.deleteSubtask(child.href) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, "Delete subtask", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = newSubtask,
                    onValueChange = { newSubtask = it },
                    placeholder = { Text("Add a subtask") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.addSubtask(newSubtask); newSubtask = "" }),
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    colors = searchFieldColors(),
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Linked items (#12) -- links to a Vinci contact, a Nóta note, a Tavla card, or
            // any other pasted link. Mirrors Tavla's own "Linked items".
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Linked items", modifier = Modifier.weight(1f), trailing = links.size.takeIf { it > 0 }?.toString())
                IconButton(onClick = { showAddLink = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Add, "Add link", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (links.isEmpty()) {
                    Text(
                        "Link a Vinci contact, a Nóta note, a Tavla card, or any other BarburasLab item.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        links.forEach { link ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(onClick = { onOpenLink(link) }, onLongClick = { viewModel.removeLink(link.id) })
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(link.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(link.appLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Linked contacts -- cross-app integration with Vinci (see cross-app-linking
            // notes). This is the reverse index Vinci's ContactLinksRepository reads to show
            // this task on a contact's "Linked items" section.
            SectionLabel("Linked contacts", trailing = linkedContacts.size.takeIf { it > 0 }?.toString())
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp)) {
                if (linkedContacts.isEmpty()) {
                    Text(
                        "Link this task to a Vinci contact -- it'll show up on their profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    linkedContacts.forEach { contact ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            ContactPhotoAvatar(contact.photoUri, size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                contact.displayName.ifBlank { "Contact no longer on this device" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (contact.displayName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.unlinkContact(contact.cardavUid) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Close, "Unlink", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                if (linkSyncError != null && linkedContacts.isNotEmpty()) {
                    Text(
                        linkSyncError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                TextButton(onClick = { requestContactPicker() }) {
                    Icon(Icons.Outlined.PersonAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link a contact", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Attachments
            SectionLabel(
                "Attachments",
                trailing = todo.attachments.size.takeIf { it > 0 }?.toString(),
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth().kotoCard(isDark, 16.dp)) {
                todo.attachments.forEach { url ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            // The file name is the useful part; the rest is the server path.
                            Uri.decode(url.substringAfterLast('/')),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeAttachment(url) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, "Remove attachment", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }
                    }
                }
                if (uiState.uploading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Uploading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    TextButton(
                        onClick = { attachLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add a file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                "Files are uploaded to /Koto/attachments in your own Nextcloud and linked from the task.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )

            Spacer(Modifier.height(18.dp))

            // ── Where it lives
            Row(
                modifier = Modifier.fillMaxWidth().clickable { listPicker = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val list = uiState.lists.firstOrNull { it.href == task.listHref }
                ListDot(list?.color ?: "5E9CB4", 9.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    list?.displayName ?: "Tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("Change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(48.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
}

/**
 * Title and notes commit on focus loss rather than on every keystroke: each save is a CalDAV
 * PUT, and one per character would hammer the server and churn the ETag.
 */
@Composable
private fun EditableTitle(value: String, done: Boolean, onSave: (String) -> Unit, modifier: Modifier = Modifier) {
    var draft by remember(value) { mutableStateOf(value) }
    var wasFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        ),
        modifier = modifier.onFocusChanged { state ->
            if (wasFocused && !state.isFocused) {
                if (draft.isNotBlank() && draft != value) onSave(draft) else draft = value
            }
            wasFocused = state.isFocused
        },
        colors = transparentFieldColors(),
    )
}

@Composable
private fun EditableNotes(value: String, onSave: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    var wasFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = { Text("Anything worth writing down") },
        minLines = 3,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (wasFocused && !state.isFocused && draft != value) onSave(draft)
                wasFocused = state.isFocused
            },
        colors = searchFieldColors(),
    )
}

@Composable
private fun transparentFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun PropertyRow(
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}

/** Searches the device's address book (same Nextcloud-synced contacts Vinci reads) by name. */
@Composable
private fun ContactPickerDialog(
    onDismiss: () -> Unit,
    onSearch: suspend (String) -> List<com.brbrs.koto.contactlinks.PickableContact>,
    onPick: (com.brbrs.koto.contactlinks.PickableContact) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.brbrs.koto.contactlinks.PickableContact>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        results = runCatching { onSearch(query) }.getOrDefault(emptyList())
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link a contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search contacts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Text("Searching...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (results.isEmpty()) {
                    Text(
                        "No contacts found. Only contacts synced with a Nextcloud address book can be linked.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        results.forEach { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onPick(contact) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ContactPhotoAvatar(contact.photoUri, size = 26.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(contact.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

/**
 * #12: the person names what they're linking to and pastes a share link, since Koto has no
 * way to browse Vinci's contacts, Nóta's notes, or Tavla's cards from inside this app. Mirrors
 * Tavla's own AddLinkDialog.
 */
@Composable
private fun AddLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (appPackage: String, appLabel: String, label: String, uri: String) -> Unit,
) {
    var appChoice by remember { mutableStateOf(0) }
    var label by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    val isDark = LocalIsDark.current
    val apps = listOf(
        Triple("com.brbrs.vinci", "Vinci", "vinci://"),
        Triple("com.brbrs.nota", "Nóta", "nota://"),
        Triple("com.brbrs.tavla", "Tavla", "tavla://"),
        Triple("", "Other app", ""),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add linked item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEachIndexed { index, (_, name, _) ->
                        val selected = appChoice == index
                        Box(
                            modifier = Modifier
                                .kotoChip(isDark = isDark, selected = selected)
                                .clickable { appChoice = index }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("What is it? (e.g. \"Sarah\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("Link") },
                    placeholder = { Text(apps[appChoice].third.ifBlank { "https:// or app://..." }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Paste a link shared from the other app. If it doesn't support links yet, this just opens the app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(apps[appChoice].first, apps[appChoice].second, label.ifBlank { apps[appChoice].second }, uri) },
                enabled = uri.isNotBlank(),
            ) { Text("Add", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

/**
 * Lets the person review/edit the title before sending this task to another suite app as a
 * new item there (a generic share -- see [com.brbrs.koto.sharesend.SendToAppHelper]).
 */
@Composable
private fun SendToAppDialog(
    appLabel: String,
    initialTitle: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, body: String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send to $appLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (body.isNotBlank()) {
                    Text(
                        "The task's description will be sent along with it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, body) }, enabled = title.isNotBlank()) {
                Text("Send", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

/**
 * A device contact's photo, decoded directly via ContentResolver (no Coil dependency in this
 * app) -- falls back to a generic person icon while loading or when there's no photo.
 */
@Composable
private fun ContactPhotoAvatar(photoUri: String?, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val context = LocalContext.current
    var bmp by remember(photoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(photoUri) {
        bmp = if (photoUri.isNullOrBlank()) null else runCatching {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }
    Box(
        modifier = Modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.6f))
        }
    }
}
