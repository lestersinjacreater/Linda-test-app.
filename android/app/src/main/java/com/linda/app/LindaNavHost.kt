package com.linda.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linda.app.features.checker.CheckerScreen
import com.linda.app.features.demo.DemoScreen
import com.linda.app.features.detail.DetailScreen
import com.linda.app.features.history.HistoryScreen
import com.linda.app.features.guardian.GuardianScreen
import com.linda.app.features.home.HomeScreen
import com.linda.app.features.inbox.InboxScreen
import com.linda.app.features.recovery.RecoveryScreen
import com.linda.app.features.settings.SettingsScreen

/** The screens reachable from the bottom navigation bar. */
private enum class Destination(
    val route: String,
    @StringRes val label: Int,
    @StringRes val glyph: Int,
) {
    Home("home", R.string.nav_home, R.string.nav_glyph_home),
    Checker("checker", R.string.nav_check, R.string.nav_glyph_check),
    History("history", R.string.nav_history, R.string.nav_glyph_history),
    Settings("settings", R.string.nav_settings, R.string.nav_glyph_settings),
}

/**
 * [openDetectionId]: set when the user tapped a warning notification, to jump to that message.
 * [sharedText]: set when text was shared into Linda from another app, to check it straight away.
 */
@Composable
fun LindaNavHost(openDetectionId: Long? = null, sharedText: String? = null) {
    val navController = rememberNavController()
    LaunchedEffect(openDetectionId) {
        if (openDetectionId != null && openDetectionId >= 0) navController.navigate("detail/$openDetectionId")
    }
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) navController.navigate(Destination.Checker.route)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Keep one copy of each screen and restore its state when coming back.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(text = stringResource(destination.glyph), fontSize = 22.sp) },
                        label = { Text(text = stringResource(destination.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) { HomeScreen(onOpenInbox = { navController.navigate("inbox") }, onOpenRecovery = { navController.navigate("recovery/-1") }) }
            composable(Destination.Checker.route) { CheckerScreen(sharedText) }
            composable(Destination.History.route) { HistoryScreen(onOpen = { navController.navigate("detail/$it") }) }
            composable(Destination.Settings.route) { SettingsScreen(onOpenDemo = { navController.navigate("demo") }, onOpenGuardian = { navController.navigate("guardian") }) }
            composable("inbox") { InboxScreen(onOpenDetail = { navController.navigate("detail/$it") }, onBack = { navController.popBackStack() }) }
            composable("guardian") { GuardianScreen(onBack = { navController.popBackStack() }) }
            composable("demo") { DemoScreen(onBack = { navController.popBackStack() }) }
            composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                DetailScreen(
                    detectionId = entry.arguments?.getLong("id") ?: -1L,
                    onOpenRecovery = { navController.navigate("recovery/$it") },
                    onBack = { navController.popBackStack() },
                )
            }
            // The guide for someone who already sent money. id = the warning they are reacting to, or -1 from Home.
            composable("recovery/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                RecoveryScreen(detectionId = entry.arguments?.getLong("id") ?: -1L, onBack = { navController.popBackStack() })
            }
        }
    }
}
