package com.smartorders.driverhelper.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_orders_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_MIN_PRICE = "min_price"
        const val KEY_MAX_PRICE = "max_price"
        const val KEY_MIN_MINUTES = "min_minutes"
        const val KEY_MAX_MINUTES = "max_minutes"
        const val KEY_MAX_DISTANCE = "max_distance"
        const val KEY_AUTO_ACCEPT = "auto_accept"
    }

    var minPrice: Float
        get() = prefs.getFloat(KEY_MIN_PRICE, 6f)
        set(v) = prefs.edit().putFloat(KEY_MIN_PRICE, v).apply()

    var maxPrice: Float
        get() = prefs.getFloat(KEY_MAX_PRICE, 100f)
        set(v) = prefs.edit().putFloat(KEY_MAX_PRICE, v).apply()

    var minMinutes: Float
        get() = prefs.getFloat(KEY_MIN_MINUTES, 1f)
        set(v) = prefs.edit().putFloat(KEY_MIN_MINUTES, v).apply()

    var maxMinutes: Float
        get() = prefs.getFloat(KEY_MAX_MINUTES, 15f)
        set(v) = prefs.edit().putFloat(KEY_MAX_MINUTES, v).apply()

    var maxDistance: Float
        get() = prefs.getFloat(KEY_MAX_DISTANCE, 100f)
        set(v) = prefs.edit().putFloat(KEY_MAX_DISTANCE, v).apply()

    var autoAcceptEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ACCEPT, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_ACCEPT, v).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
