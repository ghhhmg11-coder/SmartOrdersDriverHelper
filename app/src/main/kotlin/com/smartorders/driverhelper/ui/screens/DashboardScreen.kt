package com.smartorders.driverhelper.ui.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.AppState
import com.smartorders.driverhelper.PreferencesManager
import com.smartorders.driverhelper.service.JeenyAccessibilityService
import com.smartorders.driverhelper.ui.theme.*

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val autoAccept by AppState.autoAcceptEnabled.collectAsState()
    val serviceConnected by AppState.serviceConnected.collectAsState()
    val accepted by AppState.acceptedTrips.collectAsState()
    val rejected by AppState.rejectedTrips.collectAsState()
    val detected by AppState.detectedTrips.collectAsState()
    val totalSar by AppState.totalSar.collectAsState()

    val autoAcceptColor by animateColorAsState(
        targetValue = if (autoAccept) GreenActive else DarkCard,
        animationSpec = tween(300),
        label = "autoAcceptColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Smart Orders",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Driver Helper",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.offset(y = (-8).dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = autoAcceptColor)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto Accept", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = if (autoAccept) "ACTIVE – Monitoring Jeeny ●" else "INACTIVE",
                        fontSize = 12.sp,
                        color = if (autoAccept) GreenActive else TextSecondary
                    )
                }
                Switch(
                    checked = autoAccept,
                    onCheckedChange = { enabled ->
                        PreferencesManager.setAutoAcceptEnabled(context, enabled)
                        AppState.setAutoAcceptEnabled(enabled)
                        AppState.addEventLog(if (enabled) "Auto-accept ENABLED ▶" else "Auto-accept DISABLED ⏸")
                        val intent = Intent(JeenyAccessibilityService.ACTION_TOGGLE_AUTO_ACCEPT).apply {
                            putExtra(JeenyAccessibilityService.EXTRA_ENABLED, enabled)
                        }
                        context.sendBroadcast(intent)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = JeenyPurple,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⬡", fontSize = 24.sp, color = JeenyPurple)
                    Column {
                        Text("Accessibility Service", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = if (serviceConnected) "✓ Connected" else "✗ Disconnected",
                            fontSize = 12.sp,
                            color = if (serviceConnected) GreenActive else RedInactive
                        )
                    }
                }
                Text(
                    text = "🏃",
                    fontSize = 24.sp,
                    color = if (serviceConnected) GreenActive else TextSecondary
                )
            }
        }

        Text("Today's Stats", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(modifier = Modifier.weight(1f), icon = "✅", value = accepted.toString(), label = "Accepted", color = GreenActive)
            StatCard(modifier = Modifier.weight(1f), icon = "⊙", value = detected.toString(), label = "Detected", color = AccentYellow)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(modifier = Modifier.weight(1f), icon = "﷼", value = "%.2f".format(totalSar), label = "Total SAR", color = JeenyPurple)
            StatCard(modifier = Modifier.weight(1f), icon = "⊗", value = rejected.toString(), label = "Rejected", color = RedInactive)
        }

        OutlinedButton(
            onClick = {
                PreferencesManager.resetStats(context)
                AppState.resetStats()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedInactive),
            border = androidx.compose.foundation.BorderStroke(1.dp, RedInactive)
        ) {
            Text("Clear Today Stats 🗑", color = RedInactive)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 24.sp, color = color)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, fontSize = 12.sp, color = TextSecondary)
        }
    }
}
