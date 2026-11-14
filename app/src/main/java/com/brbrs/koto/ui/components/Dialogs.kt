package com.brbrs.koto.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.brbrs.koto.data.TaskList
import com.brbrs.koto.ical.DateVal
import com.brbrs.koto.icons.IconCatalog
import com.brbrs.koto.ical.Recurrence
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.kotoChip
import com.brbrs.koto.util.composeDue
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    initial: String = "",
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmText, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleColorDialog(
    title: String,
    confirmText: String,
    initialTitle: String = "",
    initialColor: String = ListColorPalette.first(),
    onDismiss: () -> Unit,
    onConfirm: (title: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialTitle) }
    var color by remember { mutableStateOf(initialColor.uppercase()) }
    val palette = remember(initialColor) {
        if (ListColorPalette.any { it.equals(initialColor, true) }) ListColorPalette
        else listOf(initialColor.uppercase()) + ListColorPalette
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    palette.forEach { hex ->
                        val c = colorFromHex(hex)
                        val selected = hex.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Outlined.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) {
                Text(confirmText, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    destructive: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

/**
 * Due date picker. The all-day switch is the important control here, not a detail: a task
 * due "Friday" and one due "Friday 00:00" behave differently in every other CalDAV client,
 * so Koto makes the choice explicit rather than guessing from whether a time was touched.
 */
@Composable
fun DuePickerDialog(
    initial: DateVal?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (DateVal) -> Unit,
) {
    val context = LocalContext.current
    val isDark = LocalIsDark.current
    val zone = ZoneId.systemDefault()
    val initialDate = initial?.let { Instant.ofEpochMilli(it.millis).atZone(zone).toLocalDate() } ?: LocalDate.now()
    val initialTime = initial?.takeIf { !it.dateOnly }?.let {
        Instant.ofEpochMilli(it.millis).atZone(zone).toLocalTime()
    }

    var date by remember { mutableStateOf(initialDate) }
    var hour by remember { mutableStateOf(initialTime?.hour) }
    var minute by remember { mutableStateOf(initialTime?.minute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Due date") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        "Today" to LocalDate.now(),
                        "Tomorrow" to LocalDate.now().plusDays(1),
                        "Next week" to LocalDate.now().plusWeeks(1),
                    ).forEach { (label, d) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .kotoChip(isDark = isDark, selected = date == d)
                                .clickable { date = d }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (date == d) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                PickerRow(
                    label = "Date",
                    value = com.brbrs.koto.util.formatDate(date),
                ) {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year, date.monthValue - 1, date.dayOfMonth,
                    ).show()
                }
                PickerRow(
                    label = "Time",
                    value = if (hour == null) "All day" else "%02d:%02d".format(hour, minute ?: 0),
                ) {
                    TimePickerDialog(
                        context,
                        { _, h, m -> hour = h; minute = m },
                        hour ?: 9, minute ?: 0, true,
                    ).show()
                }
                if (hour != null) {
                    TextButton(onClick = { hour = null; minute = null }) {
                        Text("Make it all day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(composeDue(date, hour, minute)) }) {
                Text("Set", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onClear) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Priority picker on the RFC 5545 1-9 scale Nextcloud Tasks uses (1 highest). */
@Composable
fun PriorityDialog(current: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Priority") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(p) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == current, onClick = { onSelect(p) })
                        Spacer(Modifier.width(4.dp))
                        Text(priorityLabel(p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) }
        },
    )
}

/**
 * Recurrence picker, limited to the presets Koto's own RRULE engine can advance reliably.
 * A rule written by another client that Koto can't parse is shown as read-only rather than
 * silently replaced, so an elaborate rule from Thunderbird survives being looked at here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceDialog(current: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    val isDark = LocalIsDark.current
    val parsed = Recurrence.parse(current)
    val unparseable = current != null && current.isNotBlank() && parsed == null

    var freq by remember { mutableStateOf(parsed?.freq ?: "WEEKLY") }
    var interval by remember { mutableStateOf(parsed?.interval ?: 1) }
    var days by remember { mutableStateOf(parsed?.plainDays.orEmpty().toSet()) }
    // Monthly rules can target "the last Friday" rather than a date; null means by date.
    var monthlyOrdinal by remember { mutableStateOf(parsed?.byDay?.firstOrNull()?.ordinal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (unparseable) {
                    Text(
                        "This task repeats on a rule Koto can't edit yet. It is kept exactly as it is " +
                            "unless you replace it below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text("Presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Every weekday" to Recurrence.weekdaysRule(),
                        "Last weekday of month" to Recurrence.lastWeekdayOfMonthRule(),
                        "Last Friday of month" to "FREQ=MONTHLY;BYDAY=-1FR",
                    ).forEach { (label, rule) ->
                        val sel = current == rule
                        Box(
                            modifier = Modifier
                                .kotoChip(isDark = isDark, selected = sel)
                                .clickable { onSelect(rule) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly")
                        .forEach { (value, label) ->
                            val sel = freq == value
                            Box(
                                modifier = Modifier
                                    .kotoChip(isDark = isDark, selected = sel)
                                    .clickable { freq = value }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                }
                Spacer(Modifier.height(14.dp))
                Text("Every", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 4, 6).forEach { n ->
                        val sel = interval == n
                        Box(
                            modifier = Modifier
                                .kotoChip(isDark = isDark, selected = sel)
                                .clickable { interval = n }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (n == 1) "1" else "$n",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (freq == "WEEKLY" || freq == "MONTHLY") {
                    Spacer(Modifier.height(14.dp))
                    Text("On", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            val sel = d in days
                            Box(
                                modifier = Modifier
                                    .kotoChip(isDark = isDark, selected = sel)
                                    .clickable { days = if (sel) days - d else days + d }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    d.name.take(2).lowercase().replaceFirstChar(Char::uppercase),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (freq == "MONTHLY" && days.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Which one in the month",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "First", 2 to "Second", 3 to "Third", -1 to "Last").forEach { (ord, label) ->
                                val sel = monthlyOrdinal == ord
                                Box(
                                    modifier = Modifier
                                        .kotoChip(isDark = isDark, selected = sel)
                                        .clickable { monthlyOrdinal = ord }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(
                    Recurrence.buildRule(
                        freq = freq,
                        interval = interval,
                        byDay = days.sortedBy { it.value },
                        // Only meaningful for a monthly rule; buildRule ignores it otherwise.
                        monthlyOrdinal = monthlyOrdinal ?: if (freq == "MONTHLY" && days.isNotEmpty()) -1 else null,
                    )
                )
            }) { Text("Set", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSelect(null) }) { Text("Don't repeat", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

@Composable
fun ListPickerDialog(
    lists: List<TaskList>,
    current: String?,
    title: String = "Move to list",
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                lists.forEach { l ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !l.readOnly) { onSelect(l.href) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ListDot(l.color, 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            l.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                l.readOnly -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                l.href == current -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (l.readOnly) {
                            Text("Read only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (l.href == current) {
                            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

/**
 * Coordinates for a location reminder.
 *
 * Typed rather than picked on a map: bundling a map SDK would mean either a Google dependency
 * (which would cost Koto its place on F-Droid) or a tile server Koto would be calling out to,
 * and neither is worth it for a field most tasks never use. Pasting a pair of coordinates from
 * a maps app covers the real case.
 */
@Composable
fun CoordinatesDialog(
    latitude: Double?,
    longitude: Double?,
    radius: Int?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (Double, Double, Int) -> Unit,
) {
    val isDark = LocalIsDark.current
    var latText by remember { mutableStateOf(latitude?.toString().orEmpty()) }
    var lonText by remember { mutableStateOf(longitude?.toString().orEmpty()) }
    var metres by remember { mutableStateOf(radius ?: 200) }

    // Accepts "52.0199, 4.4304" pasted whole into either box, which is how maps apps share.
    fun absorbPair(text: String): Boolean {
        val parts = text.split(',', ';').map { it.trim() }
        if (parts.size != 2) return false
        val a = parts[0].toDoubleOrNull() ?: return false
        val b = parts[1].toDoubleOrNull() ?: return false
        latText = a.toString(); lonText = b.toString()
        return true
    }

    val lat = latText.trim().toDoubleOrNull()
    val lon = lonText.trim().toDoubleOrNull()
    val valid = lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me here") },
        text = {
            Column {
                Text(
                    "Koto notifies you when you arrive within this radius. Paste a coordinate pair " +
                        "from a maps app into either box and it will split itself.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = latText,
                    onValueChange = { if (!absorbPair(it)) latText = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    isError = latText.isNotBlank() && lat == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { if (!absorbPair(it)) lonText = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    isError = lonText.isNotBlank() && lon == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Radius", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(100, 200, 500, 1000).forEach { m ->
                        val sel = metres == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .kotoChip(isDark = isDark, selected = sel)
                                .clickable { metres = m }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (m >= 1000) "${m / 1000}km" else "${m}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(lat!!, lon!!, metres) }, enabled = valid) {
                Text("Set", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row {
                if (latitude != null) {
                    TextButton(onClick = onClear) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

/** Pick a Material icon for a list's tile, or "Automatic" to fall back to the color glyph. */
@Composable
fun IconPickerDialog(
    currentIcon: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("List icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.height(280.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(span = { GridItemSpan(5) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(null) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(IconCatalog.iconFor(null), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Automatic",
                            color = if (currentIcon == null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                items(IconCatalog.icons.entries.toList()) { (name, icon) ->
                    val selected = name == currentIcon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon, null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) }
        },
    )
}

/** Just a color, with no name field. Used for tags, which already have their name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    current: String?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val palette = remember(current) {
        if (current == null || ListColorPalette.any { it.equals(current, true) }) ListColorPalette
        else listOf(current.uppercase()) + ListColorPalette
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                palette.forEach { hex ->
                    val selected = hex.equals(current, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colorFromHex(hex))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = onClear) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
