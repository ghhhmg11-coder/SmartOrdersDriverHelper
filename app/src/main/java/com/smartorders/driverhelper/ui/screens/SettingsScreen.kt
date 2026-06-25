package com.smartorders.driverhelper.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartorders.driverhelper.ui.theme.*
import com.smartorders.driverhelper.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, onLogout: () -> Unit) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("الإعدادات / Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)

        SectionCard("صلاحيات النظام") {
            SettingsRow(
                icon = Icons.Default.Accessibility,
                title = "إعدادات إمكانية الوصول",
                subtitle = "فتح إعدادات Accessibility Service",
                color = GreenPrimary
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Divider(color = OnSurface.copy(alpha = 0.07f))
            SettingsRow(
                icon = Icons.Default.Layers,
                title = "صلاحية العرض فوق التطبيقات",
                subtitle = "Overlay Permission Settings",
                color = GoldAccent
            ) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
        }

        SectionCard("البيانات والنسخ الاحتياطي") {
            SettingsRow(
                icon = Icons.Default.Upload,
                title = "تصدير الإعدادات",
                subtitle = "Export settings to file",
                color = GreenPrimary
            ) {
                // Export functionality - shows info for now
            }
            Divider(color = OnSurface.copy(alpha = 0.07f))
            SettingsRow(
                icon = Icons.Default.Download,
                title = "استيراد الإعدادات",
                subtitle = "Import settings from file",
                color = GreenPrimary
            ) {
                // Import functionality
            }
        }

        SectionCard("التطبيق") {
            SettingsRow(
                icon = Icons.Default.RestartAlt,
                title = "إعادة ضبط الإعدادات",
                subtitle = "Reset all settings to defaults",
                color = RedAlert
            ) {
                showResetDialog = true
            }
            Divider(color = OnSurface.copy(alpha = 0.07f))
            SettingsRow(
                icon = Icons.Default.Logout,
                title = "تسجيل الخروج",
                subtitle = "Logout from the app",
                color = RedAlert
            ) {
                onLogout()
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(4.dp))
                Text("Smart Orders Driver Helper", fontWeight = FontWeight.Bold, color = OnSurface)
                Text("مساعد الأوامر الذكية", color = OnSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                Text("v2.0.0 (Build 10)", color = OnSurface.copy(alpha = 0.4f), fontSize = 12.sp)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = SurfaceDark,
            title = { Text("إعادة ضبط الإعدادات", color = OnSurface) },
            text = { Text("هل تريد إعادة ضبط جميع الإعدادات إلى القيم الافتراضية؟", color = OnSurface.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAllSettings(); showResetDialog = false }) {
                    Text("إعادة ضبط", color = RedAlert)
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 13.sp)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp), content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(subtitle, color = OnSurface.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurface.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}
