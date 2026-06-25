package com.smartorders.driverhelper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 500.0,
    val isGreen: Boolean = true
)
