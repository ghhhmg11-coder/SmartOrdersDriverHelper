package com.smartorders.driverhelper

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {

    private const val PREFS_NAME = "smart_orders_prefs"
    private const val KEY_AUTO_ACCEPT = "auto_accept_enabled"
    private const val KEY_MIN_PRICE = "min_price"
    private const val KEY_MAX_PRICE = "max_price"
    private const val KEY_MIN_MINUTES = "min_minutes"
    private const val KEY_MAX_MINUTES = "max_minutes"
    private const val KEY_MAX_DISTANCE = "max_distance"
    private const val KEY_ACCEPTED_TRIPS = "accepted_trips"
    private const val KEY_REJECTED_TRIPS = "rejected_trips"
    private const val KEY_DETECTED_TRIPS = "detected_trips"
    private const val KEY_TOTAL_SAR = "total_sar"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoAcceptEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_ACCEPT, false)

    fun setAutoAcceptEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_ACCEPT, enabled).apply()

    fun getMinPrice(context: Context): Float =
        prefs(context).getFloat(KEY_MIN_PRICE, 5f)

    fun getMaxPrice(context: Context): Float =
        prefs(context).getFloat(KEY_MAX_PRICE, 100f)

    fun getMinMinutes(context: Context): Float =
        prefs(context).getFloat(KEY_MIN_MINUTES, 1f)

    fun getMaxMinutes(context: Context): Float =
        prefs(context).getFloat(KEY_MAX_MINUTES, 15f)

    fun getMaxDistance(context: Context): Float =
        prefs(context).getFloat(KEY_MAX_DISTANCE, 100f)

    fun saveRules(
        context: Context,
        minPrice: Float,
        maxPrice: Float,
        minMinutes: Float,
        maxMinutes: Float,
        maxDistance: Float
    ) {
        prefs(context).edit()
            .putFloat(KEY_MIN_PRICE, minPrice)
            .putFloat(KEY_MAX_PRICE, maxPrice)
            .putFloat(KEY_MIN_MINUTES, minMinutes)
            .putFloat(KEY_MAX_MINUTES, maxMinutes)
            .putFloat(KEY_MAX_DISTANCE, maxDistance)
            .apply()
    }

    fun getAcceptedTrips(context: Context): Int =
        prefs(context).getInt(KEY_ACCEPTED_TRIPS, 0)

    fun getRejectedTrips(context: Context): Int =
        prefs(context).getInt(KEY_REJECTED_TRIPS, 0)

    fun getDetectedTrips(context: Context): Int =
        prefs(context).getInt(KEY_DETECTED_TRIPS, 0)

    fun getTotalSar(context: Context): Float =
        prefs(context).getFloat(KEY_TOTAL_SAR, 0f)

    fun incrementAccepted(context: Context, sarAmount: Float) {
        val p = prefs(context)
        val cur = p.getInt(KEY_ACCEPTED_TRIPS, 0)
        val curSar = p.getFloat(KEY_TOTAL_SAR, 0f)
        p.edit()
            .putInt(KEY_ACCEPTED_TRIPS, cur + 1)
            .putFloat(KEY_TOTAL_SAR, curSar + sarAmount)
            .apply()
    }

    fun incrementRejected(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_REJECTED_TRIPS, p.getInt(KEY_REJECTED_TRIPS, 0) + 1).apply()
    }

    fun incrementDetected(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_DETECTED_TRIPS, p.getInt(KEY_DETECTED_TRIPS, 0) + 1).apply()
    }

    fun resetStats(context: Context) {
        prefs(context).edit()
            .putInt(KEY_ACCEPTED_TRIPS, 0)
            .putInt(KEY_REJECTED_TRIPS, 0)
            .putInt(KEY_DETECTED_TRIPS, 0)
            .putFloat(KEY_TOTAL_SAR, 0f)
            .apply()
    }
}
