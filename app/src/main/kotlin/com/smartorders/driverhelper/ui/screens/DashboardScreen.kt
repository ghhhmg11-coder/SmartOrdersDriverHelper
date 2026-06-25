package com.smartorders.driverhelper.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.LogType
import com.smartorders.driverhelper.data.PrefsManager
import com.smartorders.driverhelper.ui.theme.*

@Composable
fun DashboardScreen(prefs: PrefsManager) {
    val isAutoAccept by AppState.isAutoAcceptEnabled
    val isConnected by AppState.isServiceConnected
    val detected by AppState.detectedTrips
    val accepted by AppState.acceptedTrips
    val rejected by AppState.rejectedTrips
    val totalSAR by AppState.totalSAR

    val toggleColor by animateColorAsState(
        targetValue = if (isAutoAccept) AccentGreen else DarkCard,
        label = "toggle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Smart Orders",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Driver Helper",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        // Auto Accept Toggle Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(toggleColor)
                .border(
                    1.dp,
                    if (isAutoAccept) AccentGreen else BorderColor,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Auto Accept",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAutoAccept) {
                        Text(
                            "ACTIVE – Monitoring Jeeny •",
                            color = AccentGreen,
                            fontSize = 12.sp
                        )
                    }
                }
                Switch(
                    checked = isAutoAccept,
                    onCheckedChange = { enabled ->
                        AppState.isAutoAcceptEnabled.value = enabled
                        prefs.autoAcceptEnabled = enabled
                        AppState.addLog(
                            if (enabled) "▶ Auto-accept ENABLED" else "⏹ Auto-accept DISABLED",
                            if (enabled) LogType.SUCCESS else LogType.WARNING
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentGreen.copy(alpha = 0.8f)
                    )
                )
            }
        }

        // Accessibility Service Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Accessibility Service",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isConnected) {
                        Text("✓ Connected", color = AccentGreen, fontSize = 14.sp)
                    } else {
                        Text("✗ Disconnected", color = AccentRed, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) AccentGreen else AccentRed)
                    )
                }
            }
        }

        // Stats section label
        Text(
            "Today's Stats",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )

        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), label = "Detected", value = "$detected", icon = "◎", iconColor = AccentOrange)
            StatCard(modifier = Modifier.weight(1f), label = "Accepted", value = "$accepted", icon = "✓", iconColor = AccentGreen)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(modifier = Modifier.weight(1f), label = "Rejected", value = "$rejected", icon = "✗", iconColor = AccentRed)
            StatCard(modifier = Modifier.weight(1f), label = "Total SAR", value = "${"%.2f".format(totalSAR)} ﷼", icon = "﷼", iconColor = AccentPurple)
        }

        // Clear Stats
        OutlinedButton(
            onClick = { AppState.clearStats() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(AccentRed)
            )
        ) {
            Text("🗑 Clear Today Stats", color = AccentRed)
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: String,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, color = iconColor, fontSize = 24.sp)
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}
