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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.LogEntry
import com.smartorders.driverhelper.data.LogType
import com.smartorders.driverhelper.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugScreen() {
    val isConnected by AppState.isServiceConnected
    val totalEvents by AppState.totalEvents
    val lastPkg by AppState.lastPackage
    val lastEvent by AppState.lastEvent
    val lastClass by AppState.lastClass
    val jeenyDetected by AppState.isJeenyDetected
    val acceptFound by AppState.isAcceptButtonFound
    val detectionReason by AppState.detectionReason
    val acceptClick by AppState.acceptClickResult
    val rawText by AppState.rawWindowsText
    val log = AppState.eventLog

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
            Text("Debug Log", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { AppState.eventLog.clear() }) {
                    Text("🗑", fontSize = 18.sp)
                }
            }
        }

        // Service status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DebugRow("Service", if (isConnected) "✓ Connected" else "✗ Disconnected",
                    valueColor = if (isConnected) AccentGreen else AccentRed)
                DebugRow("Total Events", "$totalEvents")
                DebugRow("Last Package", lastPkg)
                DebugRow("Last Event", lastEvent)
                DebugRow("Last Class", lastClass)
                HorizontalDivider(color = BorderColor)
                DebugRow("Jeeny Detected", if (jeenyDetected) "YES" else "NO",
                    valueColor = if (jeenyDetected) AccentGreen else AccentRed)
                DebugRow("Accept Button", if (acceptFound) "FOUND" else "NOT FOUND",
                    valueColor = if (acceptFound) AccentGreen else AccentRed)
                DebugRow("Detection Reason", detectionReason)
                DebugRow("Accept Click", acceptClick)
            }
        }

        // Raw windows text
        if (rawText.isNotEmpty()) {
            Text("Raw Window Text", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    rawText,
                    color = AccentGreen.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }
        }

        // Event log
        Text("Event Log (${log.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0A0A))
                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (log.isEmpty()) {
                    Text("No events yet...", color = TextSecondary, fontSize = 12.sp)
                } else {
                    log.reversed().forEach { entry ->
                        LogRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
fun DebugRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.2f))
    }
}

@Composable
fun LogRow(entry: LogEntry) {
    val color = when (entry.type) {
        LogType.SUCCESS -> AccentGreen
        LogType.ERROR -> AccentRed
        LogType.WARNING -> AccentOrange
        LogType.INFO -> TextSecondary
    }
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val time = sdf.format(Date(entry.timestamp))
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${entry.message} [$time]",
            color = color,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun Modifier.clip(shape: RoundedCornerShape) = this.then(
    Modifier.background(Color.Transparent, shape)
)
