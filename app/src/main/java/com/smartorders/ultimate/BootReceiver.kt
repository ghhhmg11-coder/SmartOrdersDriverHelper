package com.smartorders.ultimate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("jeeny_prefs", Context.MODE_PRIVATE)
            val wasEnabled = prefs.getBoolean("auto_accept", false)
            if (wasEnabled && android.provider.Settings.canDrawOverlays(context)) {
                context.startService(Intent(context, FloatingControllerService::class.java))
            }
        }
    }
}
