package com.smartorders.driverhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.LogType
import com.smartorders.driverhelper.data.PrefsManager
import com.smartorders.driverhelper.service.JeenyAccessibilityService
import com.smartorders.driverhelper.ui.theme.*

@Composable
fun SettingsScreen(
    prefs: PrefsManager,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )

        // Permissions section
        SettingsSection(label = "Permissions") {
            SettingsActionCard(
                title = "Accessibility Service",
                subtitle = "Enable Smart Orders in accessibility settings",
                emoji = "♿",
                accentColor = AccentPurple,
                onClick = onOpenAccessibility
            )
            SettingsActionCard(
                title = "Overlay Permission",
                subtitle = "Allow drawing over other apps",
                emoji = "▣",
                accentColor = AccentPurple,
                onClick = onOpenOverlayPermission
            )
        }

        // Floating Overlay section
        SettingsSection(label = "Floating Overlay") {
            SettingsActionCard(
                title = "Start Floating Overlay",
                subtitle = "Shows the floating ON/OFF button",
                emoji = "▶",
                accentColor = AccentGreen,
                onClick = onStartOverlay
            )
            SettingsActionCard(
                title = "Stop Floating Overlay",
                subtitle = "Remove the floating button",
                emoji = "■",
                accentColor = AccentRed,
                onClick = onStopOverlay
            )
        }

        // App section
        SettingsSection(label = "App") {
            SettingsActionCard(
                title = "Reset Settings",
                subtitle = "Restore default rules and clear all stats",
                emoji = "↺",
                accentColor = AccentRed,
                onClick = {
                    prefs.resetAll()
                    AppState.clearStats()
                    AppState.eventLog.clear()
                    AppState.addLog("↺ Settings reset to defaults", LogType.WARNING)
                }
            )
        }

        // Monitored packages
        SettingsSection(label = "Monitored Packages") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    JeenyAccessibilityService.JEENY_PACKAGES.forEach { pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(AccentPurple, shape = RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(pkg, color = AccentPurple, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Detection markers: قبول العرض • يبعد • مشوار داخل المدينة • استريح • ﷼",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    subtitle: String,
    emoji: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Text(emoji, fontSize = 22.sp)
        }
    }
}
