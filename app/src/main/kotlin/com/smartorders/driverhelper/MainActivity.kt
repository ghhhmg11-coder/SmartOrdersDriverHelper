package com.smartorders.driverhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartorders.driverhelper.ui.screens.*
import com.smartorders.driverhelper.ui.theme.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppState.setAutoAcceptEnabled(PreferencesManager.isAutoAcceptEnabled(this))
        AppState.updateFromPrefs(
            accepted = PreferencesManager.getAcceptedTrips(this),
            rejected = PreferencesManager.getRejectedTrips(this),
            detected = PreferencesManager.getDetectedTrips(this),
            totalSar = PreferencesManager.getTotalSar(this)
        )

        setContent {
            SmartOrdersTheme {
                MainApp()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: String) {
    object Settings : Screen("settings", "Settings", "⚙")
    object Debug : Screen("debug", "Debug", "🐞")
    object Rules : Screen("rules", "Rules", "≡×")
    object Dashboard : Screen("dashboard", "Dashboard", "⊞")
}

val bottomNavItems = listOf(
    Screen.Settings,
    Screen.Debug,
    Screen.Rules,
    Screen.Dashboard
)

@Composable
fun MainApp() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Rules.route) { RulesScreen() }
            composable(Screen.Debug.route) { DebugScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Text(screen.icon, fontSize = 18.sp, color = if (selected) JeenyPurple else TextSecondary)
                },
                label = {
                    Text(screen.label, fontSize = 10.sp, color = if (selected) JeenyPurple else TextSecondary)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = JeenyPurple,
                    selectedTextColor = JeenyPurple,
                    indicatorColor = DarkCard,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}
