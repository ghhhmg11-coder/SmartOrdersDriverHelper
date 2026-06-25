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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.LogType
import com.smartorders.driverhelper.data.PrefsManager
import com.smartorders.driverhelper.ui.theme.*

@Composable
fun RulesScreen(prefs: PrefsManager) {
    var minPrice by remember { mutableStateOf(prefs.minPrice.toString()) }
    var maxPrice by remember { mutableStateOf(prefs.maxPrice.toString()) }
    var minMinutes by remember { mutableStateOf(prefs.minMinutes.toString()) }
    var maxMinutes by remember { mutableStateOf(prefs.maxMinutes.toString()) }
    var maxDistance by remember { mutableStateOf(prefs.maxDistance.toString()) }
    var savedMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Rules",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )

        // Package info
        Text(
            "com.jeeny.driver / com.jeeny.drivers",
            color = AccentPurple,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Price section
        SectionHeader("(﷼ SAR) Price")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuleField(
                modifier = Modifier.weight(1f),
                label = "﷼ Max Price",
                value = maxPrice,
                onValueChange = { maxPrice = it }
            )
            RuleField(
                modifier = Modifier.weight(1f),
                label = "﷼ Min Price",
                value = minPrice,
                onValueChange = { minPrice = it }
            )
        }

        // Time section
        SectionHeader("Pickup Time Rules (minutes)")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuleField(
                modifier = Modifier.weight(1f),
                label = "Max Minutes",
                value = maxMinutes,
                onValueChange = { maxMinutes = it }
            )
            RuleField(
                modifier = Modifier.weight(1f),
                label = "Min Minutes",
                value = minMinutes,
                onValueChange = { minMinutes = it }
            )
        }

        // Distance section
        SectionHeader("Pickup Distance (km)")
        RuleField(
            modifier = Modifier.fillMaxWidth(),
            label = "Max Pickup Distance (km)",
            value = maxDistance,
            onValueChange = { maxDistance = it }
        )

        // Save Button
        Button(
            onClick = {
                val minP = minPrice.toFloatOrNull() ?: prefs.minPrice
                val maxP = maxPrice.toFloatOrNull() ?: prefs.maxPrice
                val minM = minMinutes.toFloatOrNull() ?: prefs.minMinutes
                val maxM = maxMinutes.toFloatOrNull() ?: prefs.maxMinutes
                val maxD = maxDistance.toFloatOrNull() ?: prefs.maxDistance

                prefs.minPrice = minP
                prefs.maxPrice = maxP
                prefs.minMinutes = minM
                prefs.maxMinutes = maxM
                prefs.maxDistance = maxD

                val msg = "💾 Rules saved: price=$minP-$maxP ﷼, minutes=$minM-$maxM, dist≤${maxD}km"
                savedMessage = msg
                AppState.addLog(msg, LogType.INFO)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
        ) {
            Text("💾 Save Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (savedMessage.isNotEmpty()) {
            // Active rules summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Active Rules Summary",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    RuleSummaryRow("Price Range", "﷼ ${maxPrice} – ${minPrice}")
                    RuleSummaryRow("Pickup Minutes", "min ${maxMinutes} – ${minMinutes}")
                    RuleSummaryRow("Max Distance", "km ${maxDistance}")
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        color = AccentPurple,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End
    )
}

@Composable
fun RuleField(modifier: Modifier = Modifier, label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPurple,
            unfocusedBorderColor = BorderColor,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = AccentPurple
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun RuleSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
