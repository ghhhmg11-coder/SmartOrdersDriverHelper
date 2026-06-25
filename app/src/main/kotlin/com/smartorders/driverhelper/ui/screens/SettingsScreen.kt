package com.smartorders.driverhelper.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.AppState
import com.smartorders.driverhelper.PreferencesManager
import com.smartorders.driverhelper.service.FloatingOverlayService
import com.smartorders.driverhelper.ui.theme.*

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Settings", color = TextPrimary) },
            text = { Text("This will reset all rules to defaults and clear stats.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    PreferencesManager.saveRules(context, 5f, 100f, 1f, 15f, 100f)
                    PreferencesManager.resetStats(context)
                    AppState.resetStats()
                    AppState.addEventLog("Settings reset to defaults")
                    showResetConfirm = false
                }) {
                    Text("Reset", color = RedInactive)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        SettingsSection("Permissions") {
            SettingsButton(
                icon = "♿",
                title = "Accessibility Service",
                subtitle = "Enable Smart Orders in accessibility settings",
                color = JeenyPurple
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            Spacer(Modifier.height(8.dp))

            SettingsButton(
                icon = "🪟",
                title = "Overlay Permission",
                subtitle = "Allow drawing over other apps",
                color = JeenyPurple
            ) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }

        SettingsSection("Floating Overlay") {
            SettingsButton(
                icon = "▶",
                title = "Start Floating Overlay",
                subtitle = "Shows the floating ON/OFF button",
                color = GreenActive
            ) {
                FloatingOverlayService.start(context)
                AppState.addEventLog("Floating overlay started")
            }

            Spacer(Modifier.height(8.dp))

            SettingsButton(
                icon = "⏹",
                title = "Stop Floating Overlay",
                subtitle = "Remove the floating button",
                color = RedInactive
            ) {
                FloatingOverlayService.stop(context)
                AppState.addEventLog("Floating overlay stopped")
            }
        }

        SettingsSection("App") {
            SettingsButton(
                icon = "🔄",
                title = "Reset Settings",
                subtitle = "Restore default rules and clear all stats",
                color = RedInactive
            ) {
                showResetConfirm = true
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Monitored Packages", fontWeight = FontWeight.SemiBold, color = JeenyPurple, fontSize = 14.sp)
                Text("• com.jeeny.driver", color = TextSecondary, fontSize = 12.sp)
                Text("• com.jeeny.drivers", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Detection Markers", fontWeight = FontWeight.SemiBold, color = JeenyPurple, fontSize = 14.sp)
                Text("• قبول العرض", color = TextSecondary, fontSize = 12.sp)
                Text("• يبعد  •  ﷼  •  مشوار داخل المدينة  •  استريح", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(title, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsButton(
    icon: String,
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
                Text(subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}
