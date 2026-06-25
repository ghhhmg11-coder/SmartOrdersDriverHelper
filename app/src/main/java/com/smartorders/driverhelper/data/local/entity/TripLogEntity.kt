package com.smartorders.driverhelper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_logs")
data class TripLogEntity(
    @PrimaryKey(autoGenerate = true)
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
