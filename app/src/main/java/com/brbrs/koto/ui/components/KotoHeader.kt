package com.brbrs.koto.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.brbrs.koto.R

/**
 * The app header, shared by every tab.
 *
 * It lived on the Tasks screen originally, which meant moving Agenda to the front hid search,
 * the theme toggle and Settings behind a second tab. Those are app-level controls, so they
 * belong on every tab rather than on whichever one happens to be first.
 *
 * [searchPlaceholder] is null on screens with nothing to search, which hides the search icon
 * rather than offering a box that does nothing.
 */
@Composable
fun KotoHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    onThemeToggle: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    searchPlaceholder: String? = "Search",
    syncing: Boolean = false,
    /** Shown as a leading hamburger when the screen has a drawer. */
    onMenu: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = if (onMenu != null) 4.dp else 20.dp, end = 8.dp, top = 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMenu != null) {
                TopBarIcon(Icons.Outlined.Menu, "Task lists", onClick = onMenu)
                Spacer(Modifier.width(4.dp))
            }
            Image(
                painter = painterResource(R.drawable.koto_logo),
                contentDescription = "Koto",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.height(28.dp),
            )
            Spacer(Modifier.weight(1f))
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            if (searchPlaceholder != null) {
                TopBarIcon(Icons.Outlined.Search, "Search", active = searchOpen) {
                    val opening = !searchOpen
                    onSearchOpenChange(opening)
                    if (!opening) onQueryChange("")
                }
            }
            TopBarIcon(Icons.Outlined.DarkMode, "Theme", onClick = onThemeToggle)
            TopBarIcon(Icons.Outlined.Settings, "Settings", onClick = onSettings)
        }

        AnimatedVisibility(searchOpen && searchPlaceholder != null) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(searchPlaceholder.orEmpty()) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = headerFieldColors(),
            )
        }
    }
}

@Composable
fun headerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
)
