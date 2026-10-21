package com.linda.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linda.app.features.demo.DemoOverlay
import com.linda.app.features.demo.DemoOverlayPanel
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.linda.app.features.alerts.WarningRoute
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
    @DrawableRes val icon: Int, // line icons drawn for Linda in res/drawable (no emoji, no icon library)
) {
    Home("home", R.string.nav_home, R.drawable.ic_nav_home),
    Checker("checker", R.string.nav_check, R.drawable.ic_nav_check),
    History("history", R.string.nav_history, R.drawable.ic_nav_history),
    Settings("settings", R.string.nav_settings, R.drawable.ic_nav_settings),
}

/**
 * [openDetectionId]: set when the user tapped a warning notification, to jump to that message.
 * [openWhat]: which button they tapped (AlertNotifier.OPEN_*): the warning itself, "See why" or "Report this number".
 * [sharedText]: set when text was shared into Linda from another app, to check it straight away.
 */
@Composable
fun LindaNavHost(openDetectionId: Long? = null, openWhat: String? = null, sharedText: String? = null) {
    val navController = rememberNavController()
    LaunchedEffect(openDetectionId, openWhat) {
        // Opened from a warning notification. A tap on the warning: a confident scam gets the full-screen alert first.
        // "See why" goes straight to the explanation; "Report this number" opens the explanation with the report sheet up.
        if (openDetectionId != null && openDetectionId >= 0) navController.navigate(WarningRoute.of(openDetectionId, openWhat))
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
                        // No description: the label under the icon already says what it is.
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(text = stringResource(destination.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        val overlayOn by DemoOverlay.enabled.collectAsStateWithLifecycle()
        val overlayLatest by DemoOverlay.latest.collectAsStateWithLifecycle()
        val context = LocalContext.current
        Box(Modifier.padding(innerPadding)) {
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier,
        ) {
            composable(Destination.Home.route) { HomeScreen(onOpenInbox = { navController.navigate("inbox") }, onOpenRecovery = { navController.navigate("recovery/-1") }) }
            composable(Destination.Checker.route) { CheckerScreen(sharedText) }
            composable(Destination.History.route) { HistoryScreen(onOpen = { navController.navigate("detail/$it") }) }
            composable(Destination.Settings.route) { SettingsScreen(onOpenDemo = { navController.navigate("demo") }, onOpenGuardian = { navController.navigate("guardian") }) }
            composable("inbox") { InboxScreen(onOpenDetail = { navController.navigate("detail/$it") }, onBack = { navController.popBackStack() }) }
            composable("guardian") { GuardianScreen(onBack = { navController.popBackStack() }) }
            composable("demo") { DemoScreen(onBack = { navController.popBackStack() }) }
            composable(
                "detail/{id}?takeover={takeover}&report={report}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("takeover") { type = NavType.BoolType; defaultValue = false },
                    navArgument("report") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                DetailScreen(
                    detectionId = entry.arguments?.getLong("id") ?: -1L,
                    takeover = entry.arguments?.getBoolean("takeover") ?: false,
                    openReport = entry.arguments?.getBoolean("report") ?: false,
                    onOpenRecovery = { navController.navigate("recovery/$it") },
                    onBack = { navController.popBackStack() },
                )
            }
            // The guide for someone who already sent money. id = the warning they are reacting to, or -1 from Home.
            composable("recovery/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                RecoveryScreen(detectionId = entry.arguments?.getLong("id") ?: -1L, onBack = { navController.popBackStack() })
            }
        }
        if (overlayOn) {
            DemoOverlayPanel(overlayLatest, onHide = { DemoOverlay.setEnabled(context, false) }, modifier = Modifier.align(Alignment.BottomCenter))
        }
        }
    }
}
