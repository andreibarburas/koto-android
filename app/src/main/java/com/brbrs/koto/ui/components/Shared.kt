package com.brbrs.koto.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brbrs.koto.ical.DateVal
import com.brbrs.koto.ui.theme.AmberWarn
import com.brbrs.koto.ui.theme.GlowSteel
import com.brbrs.koto.ui.theme.LightBg
import com.brbrs.koto.ui.theme.LightGlow
import com.brbrs.koto.ui.theme.LightSurface2
import com.brbrs.koto.ui.theme.LocalIsDark
import com.brbrs.koto.ui.theme.LocalIsOled
import com.brbrs.koto.ui.theme.NavyDeep
import com.brbrs.koto.ui.theme.NavyMid
import com.brbrs.koto.ui.theme.PriorityHigh
import com.brbrs.koto.ui.theme.PriorityLow
import com.brbrs.koto.ui.theme.PriorityMedium
import com.brbrs.koto.util.DueState
import com.brbrs.koto.util.dueState
import com.brbrs.koto.util.formatDue
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File


/** Parses a hex color ("5E9CB4" or "#5E9CB4"); falls back to [fallback] on garbage. */
fun colorFromHex(hex: String, fallback: Color = Color(0xFF5E9CB4)): Color {
    val clean = hex.removePrefix("#").trim()
    return runCatching {
        when (clean.length) {
            6 -> Color(("FF$clean").toLong(16))
            8 -> Color(clean.toLong(16))
            3 -> Color(("FF" + clean.map { "$it$it" }.joinToString("")).toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

fun Color.toHex(): String {
    fun c(v: Float) = (v * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return (c(red) + c(green) + c(blue)).uppercase()
}

/** List colors offered when creating one -- Nextcloud's own defaults plus the suite accents. */
val ListColorPalette = listOf(
    "5E9CB4", "2E7A96", "0082C9", "4CAF78", "31CC7C", "E8A33D",
    "FF8F00", "E53935", "E37D9C", "AB47BC", "A8726A", "7C8B99",
)

// ── Screen background ────────────────────────────────────────────────────────

@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val isDark = LocalIsDark.current
    val isOled = LocalIsOled.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                when {
                    isOled -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
                    isDark -> Brush.verticalGradient(0f to NavyDeep, 0.45f to NavyMid, 1f to NavyDeep)
                    else   -> Brush.verticalGradient(0f to LightBg, 0.5f to Color(0xFFE8F0F4), 1f to LightSurface2)
                }
            ),
    ) {
        if (!isOled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(if (isDark) GlowSteel else LightGlow, Color.Transparent),
                            radius = if (isDark) 650f else 750f,
                        )
                    )
            )
        }
        content()
    }
}

@Composable
fun TopBarIcon(icon: ImageVector, contentDescription: String, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null, accent: Color? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = (accent ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.75f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The square that stands in for a list, matching Tavla's board tile so the two apps read as
 * one family. With no icon chosen it draws a three-bar glyph in the list's own color.
 */
@Composable
fun ListTile(colorHex: String, size: Dp = 44.dp, iconName: String? = null, coverFile: File? = null) {
    val color = colorFromHex(colorHex)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = if (iconName != null) Alignment.Center else Alignment.TopCenter,
    ) {
        when {
            coverFile != null -> {
                AsyncImage(
                    model = coverFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            iconName != null -> {
                Icon(
                    imageVector = com.brbrs.koto.icons.IconCatalog.iconFor(iconName),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(size * 0.56f),
                )
            }
            else -> {
                Row(
                    modifier = Modifier.padding(top = size * 0.25f),
                    horizontalArrangement = Arrangement.spacedBy(size * 0.07f),
                ) {
                    Box(Modifier.width(size * 0.14f).height(size * 0.5f).clip(RoundedCornerShape(2.dp)).background(color))
                    Box(Modifier.width(size * 0.14f).height(size * 0.34f).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.7f)))
                    Box(Modifier.width(size * 0.14f).height(size * 0.2f).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

// ── Checkbox ─────────────────────────────────────────────────────────────────

/**
 * The task checkbox. Deliberately not Material's Checkbox: the tick target needs to be
 * comfortably tappable (44dp) while the drawn box stays small enough not to dominate a
 * dense list, and the box is tinted by the task's priority so urgency reads at a glance.
 */
@Composable
fun TaskCheckbox(
    checked: Boolean,
    priority: Int,
    onCheckedChange: (Boolean) -> Unit,
    size: Dp = 20.dp,
) {
    val accent = priorityColor(priority) ?: MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(if (checked) 1f else 0.9f, label = "checkScale")
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(if (checked) accent else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (checked) accent else accent.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(size * 0.3f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.7f),
                )
            }
        }
    }
}

/** null for "no priority set", so the checkbox falls back to the theme accent. */
@Composable
fun priorityColor(priority: Int): Color? = when (priority) {
    in 1..3 -> PriorityHigh
    in 4..6 -> PriorityMedium
    in 7..9 -> PriorityLow
    else -> null
}

fun priorityLabel(priority: Int): String = when (priority) {
    0 -> "None"
    in 1..3 -> "High ($priority)"
    in 4..6 -> "Medium ($priority)"
    else -> "Low ($priority)"
}

// ── Chips and badges ─────────────────────────────────────────────────────────

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    colorHex: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val isDark = LocalIsDark.current
    val base = colorHex?.let { colorFromHex(it) } ?: MaterialTheme.colorScheme.secondary
    val fg = if (isDark) lerp(base, Color.White, 0.3f) else lerp(base, Color.Black, 0.4f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(base.copy(alpha = if (isDark) 0.16f else 0.14f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(tag, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ListDot(colorHex: String, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(colorFromHex(colorHex)))
}

@Composable
fun dueColor(due: DateVal): Color = when (dueState(due)) {
    DueState.Overdue -> MaterialTheme.colorScheme.error
    DueState.Today   -> if (LocalIsDark.current) AmberWarn else Color(0xFFB26A00)
    DueState.Soon    -> MaterialTheme.colorScheme.onSurfaceVariant
    DueState.Later   -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun DueBadge(due: DateVal, dimmed: Boolean = false) {
    val color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else dueColor(due)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Outlined.Schedule, null, tint = color, modifier = Modifier.size(13.dp))
        Text(formatDue(due), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Human-readable error for network failures, shared by all screens. */
fun friendlyError(e: Throwable): String {
    val msg = e.message.orEmpty()
    val code = (e as? com.brbrs.koto.network.CalDavException)?.code
    return when {
        code == 401 -> "Nextcloud rejected the login. Sign in again from Settings."
        code == 403 -> "You don't have permission to change that list."
        code == 404 -> "Not found on the server. It may have been deleted."
        code == 412 -> "That task changed on the server. Koto reloaded it."
        code == 423 -> "The server is busy with another sync. Koto will retry."
        code == 507 -> "Your Nextcloud is out of storage."
        msg.contains("Unable to resolve host") -> "Offline -- showing what's saved on this device."
        msg.contains("timeout", ignoreCase = true) -> "The server took too long to answer."
        msg.contains("CLEARTEXT") -> "This server requires HTTPS."
        msg.isBlank() -> "Something went wrong (${e.javaClass.simpleName})."
        else -> msg
    }
}
