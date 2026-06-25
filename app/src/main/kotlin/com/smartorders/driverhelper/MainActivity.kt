package com.smartorders.driverhelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.PrefsManager
import com.smartorders.driverhelper.service.FloatingOverlayService
import com.smartorders.driverhelper.ui.screens.DashboardScreen
import com.smartorders.driverhelper.ui.screens.DebugScreen
import com.smartorders.driverhelper.ui.screens.RulesScreen
import com.smartorders.driverhelper.ui.screens.SettingsScreen
import com.smartorders.driverhelper.ui.theme.SmartOrdersTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        AppState.isAutoAcceptEnabled.value = prefs.autoAcceptEnabled

        setContent {
            SmartOrdersTheme {
                MainNavigation(
                    prefs = prefs,
                    onStartOverlay = { startFloatingService() },
                    onStopOverlay = { stopFloatingService() },
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onOpenOverlayPermission = { openOverlayPermissionSettings() }
                )
            }
        }
    }

    private fun startFloatingService() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlayPermissionSettings()
            return
        }
        startForegroundService(Intent(this, FloatingOverlayService::class.java))
        AppState.addLog("Floating overlay started", com.smartorders.driverhelper.data.LogType.INFO)
    }

    private fun stopFloatingService() {
        stopService(Intent(this, FloatingOverlayService::class.java))
        AppState.addLog("Floating overlay stopped", com.smartorders.driverhelper.data.LogType.WARNING)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlayPermissionSettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }
}

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard)
    object Rules : Screen("rules", "Rules", Icons.Filled.Rule)
    object Debug : Screen("debug", "Debug", Icons.Filled.BugReport)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun MainNavigation(
    prefs: PrefsManager,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(Screen.Dashboard, Screen.Rules, Screen.Debug, Screen.Settings)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(prefs = prefs)
            }
            composable(Screen.Rules.route) {
                RulesScreen(prefs = prefs)
            }
            composable(Screen.Debug.route) {
                DebugScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    prefs = prefs,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlayPermission = onOpenOverlayPermission
                )
            }
        }
    }
}
