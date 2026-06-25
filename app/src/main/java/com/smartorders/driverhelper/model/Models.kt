package com.smartorders.driverhelper.model

data class TripLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceApp: String = "",
    val rawScreenText: String = "",
    val price: Double = 0.0,
    val pickupDistance: Double = 0.0,
    val tripDistance: Double = 0.0,
    val pickupLocation: String = "",
    val destinationLocation: String = "",
    val accepted: Boolean = false,
    val rejectionReason: String = ""
)

data class Zone(
    val id: Long = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 500.0,
    val isGreen: Boolean = true
)

data class AppRules(
    val minPrice: Double = 0.0,
    val maxPrice: Double = 9999.0,
    val maxPickupDistance: Double = 10.0,
    val maxTripDistance: Double = 100.0,
    val targetApp: String = "all",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val delayMs: Long = 500,
    val autoAcceptEnabled: Boolean = false
)

data class DailyStats(
    val detectedTrips: Int = 0,
    val acceptedTrips: Int = 0,
    val rejectedTrips: Int = 0,
    val totalSar: Double = 0.0
)

data class DetectedTrip(
    val sourceApp: String,
    val rawText: String,
    val price: Double,
    val pickupDistance: Double,
    val tripDistance: Double,
    val pickupLocation: String,
    val destinationLocation: String
)
