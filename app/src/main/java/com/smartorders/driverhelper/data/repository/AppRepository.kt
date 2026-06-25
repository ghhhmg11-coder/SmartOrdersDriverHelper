package com.smartorders.driverhelper.data.repository

import android.content.Context
import com.smartorders.driverhelper.data.local.AppDatabase
import com.smartorders.driverhelper.data.local.entity.TripLogEntity
import com.smartorders.driverhelper.data.local.entity.ZoneEntity
import com.smartorders.driverhelper.data.preferences.AppPreferences
import com.smartorders.driverhelper.model.AppRules
import com.smartorders.driverhelper.model.DailyStats
import com.smartorders.driverhelper.model.TripLog
import com.smartorders.driverhelper.model.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

class AppRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val prefs = AppPreferences(context)
    private val tripLogDao = db.tripLogDao()
    private val zoneDao = db.zoneDao()

    val allLogs: Flow<List<TripLog>> = tripLogDao.getAllLogs().map { list ->
        list.map { it.toModel() }
    }

    val todayLogs: Flow<List<TripLog>> = tripLogDao.getTodayLogs(startOfDay()).map { list ->
        list.map { it.toModel() }
    }

    val zones: Flow<List<Zone>> = zoneDao.getAllZones().map { list ->
        list.map { it.toModel() }
    }

    val rules: Flow<AppRules> = combine(
        prefs.minPrice, prefs.maxPrice, prefs.maxPickupDistance,
        prefs.maxTripDistance, prefs.targetApp, prefs.soundEnabled,
        prefs.vibrationEnabled, prefs.delayMs, prefs.autoAcceptEnabled
    ) { values ->
        AppRules(
            minPrice = values[0] as Double,
            maxPrice = values[1] as Double,
            maxPickupDistance = values[2] as Double,
            maxTripDistance = values[3] as Double,
            targetApp = values[4] as String,
            soundEnabled = values[5] as Boolean,
            vibrationEnabled = values[6] as Boolean,
            delayMs = values[7] as Long,
            autoAcceptEnabled = values[8] as Boolean
        )
    }

    val autoAcceptEnabled = prefs.autoAcceptEnabled
    val isLoggedIn = prefs.isLoggedIn

    suspend fun setAutoAccept(enabled: Boolean) = prefs.setAutoAccept(enabled)
    suspend fun setLoggedIn(value: Boolean) = prefs.setLoggedIn(value)

    suspend fun saveRules(rules: AppRules) {
        prefs.setMinPrice(rules.minPrice)
        prefs.setMaxPrice(rules.maxPrice)
        prefs.setMaxPickupDistance(rules.maxPickupDistance)
        prefs.setMaxTripDistance(rules.maxTripDistance)
        prefs.setTargetApp(rules.targetApp)
        prefs.setSoundEnabled(rules.soundEnabled)
        prefs.setVibrationEnabled(rules.vibrationEnabled)
        prefs.setDelayMs(rules.delayMs)
    }

    suspend fun clearTodayStats() = tripLogDao.clearToday(startOfDay())
    suspend fun clearAllLogs() = tripLogDao.clearAll()

    suspend fun addZone(zone: Zone) = zoneDao.insert(zone.toEntity())
    suspend fun deleteZone(zone: Zone) = zoneDao.delete(zone.toEntity())

    suspend fun getDailyStats(): DailyStats {
        val start = startOfDay()
        return DailyStats(
            detectedTrips = tripLogDao.getTodayCount(start),
            acceptedTrips = tripLogDao.getTodayAccepted(start),
            rejectedTrips = tripLogDao.getTodayRejected(start),
            totalSar = tripLogDao.getTodayTotalSar(start) ?: 0.0
        )
    }

    suspend fun resetAllSettings() = prefs.resetAll()

    private fun startOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun TripLogEntity.toModel() = TripLog(id, timestamp, sourceApp, rawScreenText,
        price, pickupDistance, tripDistance, pickupLocation, destinationLocation, accepted, rejectionReason)
    private fun ZoneEntity.toModel() = Zone(id, name, latitude, longitude, radiusMeters, isGreen)
    private fun Zone.toEntity() = ZoneEntity(id, name, latitude, longitude, radiusMeters, isGreen)
}
