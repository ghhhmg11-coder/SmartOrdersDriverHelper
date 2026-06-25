package com.smartorders.driverhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.model.Zone
import com.smartorders.driverhelper.ui.theme.*
import com.smartorders.driverhelper.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesScreen(viewModel: MainViewModel) {
    val zones by viewModel.zones.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var isGreenZone by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("المناطق / Zones", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = GreenPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة منطقة", tint = androidx.compose.ui.graphics.Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Map placeholder
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(48.dp))
                    Text("خريطة المناطق", color = OnSurface, fontWeight = FontWeight.Medium)
                    Text("أضف مناطق أدناه لعرضها هنا", color = OnSurface.copy(alpha = 0.5f), fontSize = 12.sp)
                    Text("(يتطلب Google Maps API Key)", color = GoldAccent.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Zone type tabs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val greenZones = zones.filter { it.isGreen }
            val redZones = zones.filter { !it.isGreen }
            Card(
                colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = GreenLight, modifier = Modifier.size(12.dp))
                    Text("مناطق مفضلة: ${greenZones.size}", color = GreenLight, fontSize = 13.sp)
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = RedAlert.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = RedAlert, modifier = Modifier.size(12.dp))
                    Text("مناطق محظورة: ${redZones.size}", color = RedAlert, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(zones, key = { it.id }) { zone ->
                ZoneCard(zone = zone, onDelete = { viewModel.deleteZone(zone) })
            }
            if (zones.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد مناطق محفوظة. اضغط + لإضافة منطقة", color = OnSurface.copy(alpha = 0.4f), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddZoneDialog(
            onDismiss = { showAddDialog = false },
            onSave = { zone -> viewModel.addZone(zone); showAddDialog = false }
        )
    }
}

@Composable
private fun ZoneCard(zone: Zone, onDelete: () -> Unit) {
    val color = if (zone.isGreen) GreenLight else RedAlert
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (zone.isGreen) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = null, tint = color, modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(zone.name, color = OnSurface, fontWeight = FontWeight.Medium)
                    Text(
                        "${zone.latitude.format()}, ${zone.longitude.format()} | نطاق: ${zone.radiusMeters.toInt()}م",
                        color = OnSurface.copy(alpha = 0.5f), fontSize = 11.sp
                    )
                    Text(if (zone.isGreen) "منطقة مفضلة" else "منطقة محظورة", color = color, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = RedAlert)
            }
        }
    }
}

private fun Double.format() = String.format("%.4f", this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddZoneDialog(onDismiss: () -> Unit, onSave: (Zone) -> Unit) {
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("500") }
    var isGreen by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("إضافة منطقة جديدة", color = OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المنطقة") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary))
                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("خط العرض (Latitude)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary))
                OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("خط الطول (Longitude)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary))
                OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("النطاق بالمتر") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GreenPrimary))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نوع المنطقة:", color = OnSurface)
                    FilterChip(selected = isGreen, onClick = { isGreen = true },
                        label = { Text("مفضلة") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenPrimary))
                    FilterChip(selected = !isGreen, onClick = { isGreen = false },
                        label = { Text("محظورة") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RedAlert))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(Zone(
                        name = name.ifEmpty { "منطقة جديدة" },
                        latitude = lat.toDoubleOrNull() ?: 0.0,
                        longitude = lon.toDoubleOrNull() ?: 0.0,
                        radiusMeters = radius.toDoubleOrNull() ?: 500.0,
                        isGreen = isGreen
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
