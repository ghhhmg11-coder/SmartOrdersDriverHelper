package com.smartorders.driverhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.AppState
import com.smartorders.driverhelper.PreferencesManager
import com.smartorders.driverhelper.ui.theme.*

@Composable
fun RulesScreen() {
    val context = LocalContext.current

    var minPrice by remember { mutableStateOf(PreferencesManager.getMinPrice(context).toString()) }
    var maxPrice by remember { mutableStateOf(PreferencesManager.getMaxPrice(context).toString()) }
    var minMinutes by remember { mutableStateOf(PreferencesManager.getMinMinutes(context).toString()) }
    var maxMinutes by remember { mutableStateOf(PreferencesManager.getMaxMinutes(context).toString()) }
    var maxDistance by remember { mutableStateOf(PreferencesManager.getMaxDistance(context).toString()) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Rules", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        Text(
            "com.jeeny.driver / com.jeeny.drivers",
            fontSize = 11.sp,
            color = JeenyPurple
        )

        SectionHeader("Price (SAR ﷼)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RuleTextField(
                modifier = Modifier.weight(1f),
                value = minPrice,
                label = "Min Price ﷼",
                onValueChange = { minPrice = it }
            )
            RuleTextField(
                modifier = Modifier.weight(1f),
                value = maxPrice,
                label = "Max Price ﷼",
                onValueChange = { maxPrice = it }
            )
        }

        SectionHeader("Pickup Time Rules (minutes)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RuleTextField(
                modifier = Modifier.weight(1f),
                value = minMinutes,
                label = "Min Minutes",
                onValueChange = { minMinutes = it }
            )
            RuleTextField(
                modifier = Modifier.weight(1f),
                value = maxMinutes,
                label = "Max Minutes",
                onValueChange = { maxMinutes = it }
            )
        }

        SectionHeader("Pickup Distance (km)")
        RuleTextField(
            modifier = Modifier.fillMaxWidth(),
            value = maxDistance,
            label = "Max Pickup Distance (km)",
            onValueChange = { maxDistance = it }
        )

        Button(
            onClick = {
                val minP = minPrice.toFloatOrNull() ?: 5f
                val maxP = maxPrice.toFloatOrNull() ?: 100f
                val minM = minMinutes.toFloatOrNull() ?: 1f
                val maxM = maxMinutes.toFloatOrNull() ?: 15f
                val maxD = maxDistance.toFloatOrNull() ?: 100f
                PreferencesManager.saveRules(context, minP, maxP, minM, maxM, maxD)
                AppState.addEventLog("Rules saved: price=$minP-$maxP ﷼, minutes=$minM-$maxM, dist≤${maxD}km")
                saved = true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = JeenyPurple)
        ) {
            Text("Save Rules 💾", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (saved) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active Rules Summary", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    HorizontalDivider(color = DarkCard)
                    SummaryRow("Price Range", "﷼ ${maxPrice} – ${minPrice}")
                    SummaryRow("Pickup Minutes", "min ${maxMinutes} – ${minMinutes}")
                    SummaryRow("Max Distance", "km $maxDistance")
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = JeenyPurple)
}

@Composable
fun RuleTextField(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = JeenyPurple,
            unfocusedBorderColor = TextSecondary,
            focusedLabelColor = JeenyPurple,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = JeenyPurple
        )
    )
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
