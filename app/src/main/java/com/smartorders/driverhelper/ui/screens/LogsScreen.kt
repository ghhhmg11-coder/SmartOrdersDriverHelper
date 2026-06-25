package com.smartorders.driverhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.model.TripLog
import com.smartorders.driverhelper.ui.theme.*
import com.smartorders.driverhelper.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogsScreen(viewModel: MainViewModel) {
    val logs by viewModel.allLogs.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("سجل الرحلات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
            IconButton(onClick = { showClearDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "مسح السجل", tint = RedAlert)
            }
        }

        Text("${logs.size} رحلة مسجلة", color = OnSurface.copy(alpha = 0.5f), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ListAlt, contentDescription = null, tint = OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("لا توجد سجلات بعد", color = OnSurface.copy(alpha = 0.4f), fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    LogCard(log)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("مسح السجلات") },
            text = { Text("هل أنت متأكد من مسح جميع سجلات الرحلات؟") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllLogs(); showClearDialog = false }) {
                    Text("مسح", color = RedAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("إلغاء") }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun LogCard(log: TripLog) {
    val sdf = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault())
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (log.accepted) GreenPrimary.copy(alpha = 0.1f) else RedAlert.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (log.accepted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (log.accepted) GreenLight else RedAlert,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(log.sourceApp, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(sdf.format(Date(log.timestamp)), color = OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${log.price} SAR", color = OnSurface, fontWeight = FontWeight.Medium)
                if (log.pickupDistance > 0) Text("استلام: ${log.pickupDistance} km", color = OnSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                if (log.tripDistance > 0) Text("رحلة: ${log.tripDistance} km", color = OnSurface.copy(alpha = 0.7f), fontSize = 13.sp)
            }

            if (!log.accepted && log.rejectionReason.isNotEmpty()) {
                Text("سبب الرفض: ${log.rejectionReason}", color = RedAlert.copy(alpha = 0.9f), fontSize = 12.sp)
            }

            if (expanded) {
                Divider(color = OnSurface.copy(alpha = 0.1f))
                Text("نص الشاشة الخام:", color = OnSurface.copy(alpha = 0.5f), fontSize = 11.sp)
                Text(
                    log.rawScreenText.take(300).let { if (log.rawScreenText.length > 300) "$it..." else it },
                    color = OnSurface.copy(alpha = 0.7f), fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = OnSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
