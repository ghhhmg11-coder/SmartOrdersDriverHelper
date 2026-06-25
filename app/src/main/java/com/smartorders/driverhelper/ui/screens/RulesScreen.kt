package com.smartorders.driverhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.smartorders.driverhelper.model.AppRules
import com.smartorders.driverhelper.ui.theme.*
import com.smartorders.driverhelper.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: MainViewModel) {
    val rules by viewModel.rules.collectAsState()
    var minPrice by remember(rules) { mutableStateOf(rules.minPrice.toString()) }
    var maxPrice by remember(rules) { mutableStateOf(rules.maxPrice.toString()) }
    var maxPickupDist by remember(rules) { mutableStateOf(rules.maxPickupDistance.toString()) }
    var maxTripDist by remember(rules) { mutableStateOf(rules.maxTripDistance.toString()) }
    var targetApp by remember(rules) { mutableStateOf(rules.targetApp) }
    var soundEnabled by remember(rules) { mutableStateOf(rules.soundEnabled) }
    var vibrationEnabled by remember(rules) { mutableStateOf(rules.vibrationEnabled) }
    var delayMs by remember(rules) { mutableStateOf(rules.delayMs.toString()) }
    var saved by remember { mutableStateOf(false) }

    val targetApps = listOf("all" to "جميع التطبيقات", "jeeny" to "Jeeny Driver", "uber" to "Uber Driver", "careem" to "Careem Captain", "bolt" to "Bolt Driver")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("القواعد والإعدادات", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)

        SectionHeader("قواعد السعر - Price Rules")
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleTextField("الحد الأدنى للسعر (SAR)", minPrice, Icons.Default.TrendingDown) { minPrice = it }
                RuleTextField("الحد الأقصى للسعر (SAR)", maxPrice, Icons.Default.TrendingUp) { maxPrice = it }
            }
        }

        SectionHeader("قواعد المسافة - Distance Rules")
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleTextField("أقصى مسافة استلام (km)", maxPickupDist, Icons.Default.MyLocation) { maxPickupDist = it }
                RuleTextField("أقصى مسافة رحلة (km)", maxTripDist, Icons.Default.Route) { maxTripDist = it }
            }
        }

        SectionHeader("التطبيق المستهدف - Target App")
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                targetApps.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = targetApp == key,
                            onClick = { targetApp = key },
                            colors = RadioButtonDefaults.colors(selectedColor = GreenPrimary)
                        )
                        Text(label, color = OnSurface, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        SectionHeader("السلوك - Behavior")
        Card(colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الصوت / Sound", color = OnSurface)
                    Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("الاهتزاز / Vibration", color = OnSurface)
                    Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = GreenPrimary))
                }
                RuleTextField("التأخير قبل القبول (ms)", delayMs, Icons.Default.Timer) { delayMs = it }
            }
        }

        if (saved) {
            Card(colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.2f)), shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenLight)
                    Text("تم حفظ القواعد بنجاح", color = GreenLight)
                }
            }
        }

        Button(
            onClick = {
                viewModel.saveRules(AppRules(
                    minPrice = minPrice.toDoubleOrNull() ?: 0.0,
                    maxPrice = maxPrice.toDoubleOrNull() ?: 9999.0,
                    maxPickupDistance = maxPickupDist.toDoubleOrNull() ?: 10.0,
                    maxTripDistance = maxTripDist.toDoubleOrNull() ?: 100.0,
                    targetApp = targetApp,
                    soundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    delayMs = delayMs.toLongOrNull() ?: 500L,
                    autoAcceptEnabled = false
                ))
                saved = true
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("حفظ القواعد", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 14.sp)
}

@Composable
private fun RuleTextField(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = GreenPrimary) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GreenPrimary,
            focusedLabelColor = GreenPrimary,
            unfocusedTextColor = OnSurface,
            focusedTextColor = OnSurface
        )
    )
}
