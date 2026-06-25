package com.smartorders.driverhelper.utils

import android.util.Log
import com.smartorders.driverhelper.model.Zone
import kotlin.math.*

object ZoneChecker {
    private const val TAG = "ZoneChecker"

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun isInsideZone(lat: Double, lon: Double, zone: Zone): Boolean {
        if (lat == 0.0 && lon == 0.0) {
            Log.d(TAG, "Location coordinates unavailable")
            return false
        }
        return distanceMeters(lat, lon, zone.latitude, zone.longitude) <= zone.radiusMeters
    }

    fun checkZones(
        pickupLat: Double, pickupLon: Double,
        destLat: Double, destLon: Double,
        zones: List<Zone>
    ): ZoneCheckResult {
        val blocked = zones.filter { !it.isGreen }
        val preferred = zones.filter { it.isGreen }

        for (zone in blocked) {
            if (isInsideZone(pickupLat, pickupLon, zone)) {
                return ZoneCheckResult(blocked = true, reason = "موقع الاستلام في منطقة محظورة: ${zone.name}")
            }
            if (isInsideZone(destLat, destLon, zone)) {
                return ZoneCheckResult(blocked = true, reason = "الوجهة في منطقة محظورة: ${zone.name}")
            }
        }

        val isPreferred = preferred.any { isInsideZone(pickupLat, pickupLon, it) }
        return ZoneCheckResult(blocked = false, preferred = isPreferred)
    }
}

data class ZoneCheckResult(
    val blocked: Boolean = false,
    val preferred: Boolean = false,
    val reason: String = ""
)
