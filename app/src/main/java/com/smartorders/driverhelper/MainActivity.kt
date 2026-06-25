package com.smartorders.driverhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.smartorders.driverhelper.ui.screens.*
import com.smartorders.driverhelper.ui.theme.*
import com.smartorders.driverhelper.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartOrdersTheme {
                SmartOrdersApp(viewModel)
            }
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val navItems = listOf(
    NavItem("dashboard", "الرئيسية", Icons.Default.Dashboard),
    NavItem("rules", "القواعد", Icons.Default.Rule),
    NavItem("zones", "المناطق", Icons.Default.Map),
    NavItem("logs", "السجل", Icons.Default.List),
    NavItem("settings", "الإعدادات", Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartOrdersApp(viewModel: MainViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val navController = rememberNavController()

    if (!isLoggedIn) {
        LoginScreen(viewModel = viewModel, onLoginSuccess = {})
    } else {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

        Scaffold(
            containerColor = BackgroundDark,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark) {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified) },
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenPrimary,
                                selectedTextColor = GreenPrimary,
                                indicatorColor = GreenPrimary.copy(alpha = 0.15f),
                                unselectedIconColor = OnSurface.copy(alpha = 0.5f),
                                unselectedTextColor = OnSurface.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize().background(BackgroundDark)) {
                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(viewModel) }
                    composable("rules") { RulesScreen(viewModel) }
                    composable("zones") { ZonesScreen(viewModel) }
                    composable("logs") { LogsScreen(viewModel) }
                    composable("settings") {
                        SettingsScreen(viewModel, onLogout = { viewModel.logout() })
                    }
                }
            }
        }
    }
}
