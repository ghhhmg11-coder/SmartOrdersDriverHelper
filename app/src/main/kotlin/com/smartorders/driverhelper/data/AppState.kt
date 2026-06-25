package com.smartorders.driverhelper.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData

object AppState {
    // Auto-accept toggle
    val isAutoAcceptEnabled = mutableStateOf(false)

    // Accessibility service connection
    val isServiceConnected = mutableStateOf(false)

    // Stats
    val detectedTrips = mutableStateOf(0)
    val acceptedTrips = mutableStateOf(0)
    val rejectedTrips = mutableStateOf(0)
    val totalSAR = mutableStateOf(0.0)

    // Debug info
    val totalEvents = mutableStateOf(0)
    val lastPackage = mutableStateOf("-")
    val lastEvent = mutableStateOf("-")
    val lastClass = mutableStateOf("-")
    val isJeenyDetected = mutableStateOf(false)
    val isAcceptButtonFound = mutableStateOf(false)
    val detectionReason = mutableStateOf("-")
    val acceptClickResult = mutableStateOf("-")
    val rawWindowsText = mutableStateOf("")

    // Event log (max 100 entries)
    val eventLog = mutableStateListOf<LogEntry>()

    fun addLog(message: String, type: LogType = LogType.INFO) {
        val entry = LogEntry(
            message = message,
            type = type,
            timestamp = System.currentTimeMillis()
        )
        if (eventLog.size >= 100) eventLog.removeAt(0)
        eventLog.add(entry)
    }

    fun clearStats() {
        detectedTrips.value = 0
        acceptedTrips.value = 0
        rejectedTrips.value = 0
        totalSAR.value = 0.0
    }
}

data class LogEntry(
    val message: String,
    val type: LogType,
    val timestamp: Long
)

enum class LogType {
    INFO, SUCCESS, ERROR, WARNING
}
