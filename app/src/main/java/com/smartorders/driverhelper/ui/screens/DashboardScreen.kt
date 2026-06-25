package com.smartorders.driverhelper.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.model.DailyStats
import com.smartorders.driverhelper.model.TripLog
import com.smartorders.driverhelper.service.SmartOrdersAccessibilityService
import com.smartorders.driverhelper.ui.theme.*
import com.smartorders.driverhelper.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val autoAccept by viewModel.autoAcceptEnabled.collectAsState()
    val stats by viewModel.dailyStats.collectAsState()
    val lastTrip by viewModel.lastTrip.collectAsState()
    val serviceRunning = SmartOrdersAccessibilityService.isRunning

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "لوحة التحكم", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        // Auto Accept Toggle
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (autoAccept) GreenPrimary.copy(alpha = 0.2f) else CardDark
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("القبول التلقائي", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = 16.sp)
                    Text("Auto Accept", color = OnSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Switch(
                    checked = autoAccept,
                    onCheckedChange = { viewModel.setAutoAccept(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary, checkedTrackColor = GreenLight)
                )
            }
        }

        // Accessibility Service Status
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (serviceRunning) GreenPrimary.copy(alpha = 0.15f) else RedAlert.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (serviceRunning) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (serviceRunning) GreenLight else RedAlert
                    )
                    Column {
                        Text("خدمة إمكانية الوصول", color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(
                            if (serviceRunning) "تعمل" else "متوقفة",
                            color = if (serviceRunning) GreenLight else RedAlert,
                            fontSize = 12.sp
                        )
                    }
                }
                if (!serviceRunning) {
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("تفعيل", fontSize = 12.sp)
                    }
                }
            }
        }

        // Daily Stats
        Text("إحصائيات اليوم", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = 16.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("رحلات مكتشفة", stats.detectedTrips.toString(), Icons.Default.Search, GoldAccent, Modifier.weight(1f))
            StatCard("مقبولة", stats.acceptedTrips.toString(), Icons.Default.CheckCircle, GreenLight, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("مرفوضة", stats.rejectedTrips.toString(), Icons.Default.Cancel, RedAlert, Modifier.weight(1f))
            StatCard("إجمالي SAR", String.format("%.1f", stats.totalSar), Icons.Default.AttachMoney, GoldAccent, Modifier.weight(1f))
        }

        // Last Trip
        lastTrip?.let { trip ->
            Text("آخر رحلة مكتشفة", fontWeight = FontWeight.Bold, color = OnSurface, fontSize = 16.sp)
            TripSummaryCard(trip)
        }

        // Clear Stats Button
        OutlinedButton(
            onClick = { viewModel.clearTodayStats() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAlert),
            border = ButtonDefaults.outlinedButtonBorder.copy()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("مسح إحصائيات اليوم")
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
            Text(label, fontSize = 11.sp, color = OnSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun TripSummaryCard(trip: TripLog) {
    val sdf = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault())
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(trip.sourceApp, color = GoldAccent, fontWeight = FontWeight.Bold)
                Text(sdf.format(Date(trip.timestamp)), color = OnSurface.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Text("السعر: ${trip.price} SAR", color = OnSurface)
            if (trip.pickupDistance > 0) Text("مسافة الاستلام: ${trip.pickupDistance} km", color = OnSurface.copy(alpha = 0.8f))
            if (trip.tripDistance > 0) Text("مسافة الرحلة: ${trip.tripDistance} km", color = OnSurface.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    if (trip.accepted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (trip.accepted) GreenLight else RedAlert,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    if (trip.accepted) "تم القبول" else "مرفوضة: ${trip.rejectionReason}",
                    color = if (trip.accepted) GreenLight else RedAlert,
                    fontSize = 13.sp
                )
            }
        }
    }
}
