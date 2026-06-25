package com.smartorders.driverhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.AppState
import com.smartorders.driverhelper.service.JeenyAccessibilityService
import com.smartorders.driverhelper.ui.theme.*
import android.content.Intent

@Composable
fun DebugScreen() {
    val context = LocalContext.current
    val debug by AppState.debugInfo.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Debug Log", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { }) {
                    Text("⬇", fontSize = 18.sp, color = TextSecondary)
                }
                IconButton(onClick = { }) {
                    Text("🗑", fontSize = 18.sp, color = RedInactive)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DebugRow(
                    label = "Service",
                    value = if (debug.serviceConnected) "✓ Connected" else "✗ Disconnected",
                    valueColor = if (debug.serviceConnected) GreenActive else RedInactive
                )
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow("Total Events", debug.totalEvents.toString())
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow("Last Package", debug.lastPackage)
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow("Last Event", debug.lastEvent)
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow("Last Class", debug.lastClass)
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow(
                    "Jeeny Detected",
                    if (debug.jeenyDetected) "YES" else "NO",
                    valueColor = if (debug.jeenyDetected) GreenActive else RedInactive
                )
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow(
                    "Accept Button",
                    if (debug.acceptButtonFound) "FOUND" else "NOT FOUND",
                    valueColor = if (debug.acceptButtonFound) GreenActive else RedInactive
                )
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow("Detection Reason", debug.detectionReason)
                HorizontalDivider(color = DarkCard, thickness = 0.5.dp)
                DebugRow("Accept Click", debug.acceptClickResult)
            }
        }

        if (debug.rawVisibleText.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Raw Visible Text", fontWeight = FontWeight.SemiBold, color = JeenyPurple, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = debug.rawVisibleText,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Text(
            "Event Log (${debug.eventLog.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                debug.eventLog.forEach { entry ->
                    val color = when {
                        entry.contains("✅") || entry.contains("Connected") || entry.contains("ENABLED") || entry.contains("started") -> GreenActive
                        entry.contains("❌") || entry.contains("Disconnected") || entry.contains("destroyed") || entry.contains("DISABLED") -> RedInactive
                        entry.contains("Rejected") -> Color(0xFFFF9800)
                        else -> TextSecondary
                    }
                    Text(
                        text = entry,
                        fontSize = 11.sp,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
                if (debug.eventLog.isEmpty()) {
                    Text("No events yet...", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun DebugRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.4f)
        )
    }
}
