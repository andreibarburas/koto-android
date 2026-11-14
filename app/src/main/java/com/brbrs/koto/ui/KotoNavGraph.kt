package com.brbrs.koto.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brbrs.koto.ui.screens.AgendaScreen
import com.brbrs.koto.ui.screens.AppLockScreen
import com.brbrs.koto.ui.screens.ListsScreen
import com.brbrs.koto.ui.screens.LoginScreen
import com.brbrs.koto.ui.screens.OnboardingScreen
import com.brbrs.koto.ui.screens.SettingsScreen
import com.brbrs.koto.ui.screens.TagsScreen
import com.brbrs.koto.ui.screens.TaskDetailScreen
import com.brbrs.koto.ui.screens.TasksScreen
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object AppLock    : Screen("app_lock")
    object Login      : Screen("login")
    object Tasks      : Screen("tasks")
    object Agenda     : Screen("agenda")
    object Lists      : Screen("lists")
    object Settings   : Screen("settings")
    object Tags       : Screen("tags")
    object TaskDetail : Screen("task/{href}") {
        fun route(href: String) = "task/" + URLEncoder.encode(href, "UTF-8")
    }
}

/** The tabs that carry the bottom bar. Detail, settings and the auth screens don't. */
private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Agenda, "Agenda", Icons.Outlined.CalendarMonth),
    Tab(Screen.Tasks, "Tasks", Icons.Outlined.Checklist),
    Tab(Screen.Lists, "Lists", Icons.AutoMirrored.Outlined.ListAlt),
)

/**
 * The tab the app opens on, and the anchor every tab pops back to. Both must be the same
 * screen or the back stack grows one entry per tab tap.
 */
private val HOME = Screen.Agenda

/**
 * Height of the bottom bar, which is drawn over the content rather than beside it. Tab screens
 * add this to their own bottom padding so a list's last row and the add button clear it.
 */
val BottomBarHeight = 80.dp

@Composable
fun KotoNavGraph(
    onThemeToggle: () -> Unit,
    deepLinkHref: String? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.screen.route }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Screen.Onboarding.route) {

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onContinue = {
                        navController.navigate(Screen.AppLock.route) { popUpTo(Screen.Onboarding.route) { inclusive = true } }
                    }
                )
            }

            composable(Screen.AppLock.route) {
                AppLockScreen(
                    onUnlocked = { navController.toHome(deepLinkHref) },
                    onNoLock = { navController.navigate(Screen.Login.route) { popUpTo(Screen.AppLock.route) { inclusive = true } } },
                    onLoggedInNoLock = { navController.toHome(deepLinkHref) },
                )
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(HOME.route) { popUpTo(Screen.Login.route) { inclusive = true } }
                    }
                )
            }

            composable(Screen.Tasks.route) {
                TasksScreen(
                    onTaskClick = { navController.navigate(Screen.TaskDetail.route(it)) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    onThemeToggle = onThemeToggle,
                )
            }

            composable(Screen.Agenda.route) {
                AgendaScreen(
                    onTaskClick = { navController.navigate(Screen.TaskDetail.route(it)) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    onThemeToggle = onThemeToggle,
                )
            }

            composable(Screen.Lists.route) {
                ListsScreen(
                    onTags = { navController.navigate(Screen.Tags.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    onThemeToggle = onThemeToggle,
                    onListClick = {
                        navController.navigate(Screen.Tasks.route) {
                            popUpTo(HOME.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(
                route = Screen.TaskDetail.route,
                arguments = listOf(navArgument("href") { type = NavType.StringType }),
            ) {
                TaskDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTask = { navController.navigate(Screen.TaskDetail.route(it)) },
                )
            }

            composable(Screen.Tags.route) {
                TagsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = { navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } } },
                )
            }
        }

        if (showBottomBar) {
            NavigationBar(
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(tab.screen.route) {
                                    // Single top plus popUpTo the start destination keeps the back
                                    // stack from growing one entry per tab tap.
                                    popUpTo(HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, tab.label, modifier = Modifier.size(22.dp)) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        }
    }
}

/** Lands on the task list, or straight on the task a notification pointed at. */
private fun NavHostController.toHome(deepLinkHref: String?) {
    navigate(HOME.route) { popUpTo(Screen.AppLock.route) { inclusive = true } }
    if (!deepLinkHref.isNullOrBlank()) navigate(Screen.TaskDetail.route(deepLinkHref))
}
