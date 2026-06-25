package com.smartorders.driverhelper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugInfo(
    val serviceConnected: Boolean = false,
    val totalEvents: Int = 0,
    val lastPackage: String = "-",
    val lastEvent: String = "-",
    val lastClass: String = "-",
    val jeenyDetected: Boolean = false,
    val acceptButtonFound: Boolean = false,
    val rawVisibleText: String = "",
    val detectionReason: String = "-",
    val acceptClickResult: String = "-",
    val eventLog: List<String> = emptyList()
)

object AppState {
    private val _debugInfo = MutableStateFlow(DebugInfo())
    val debugInfo: StateFlow<DebugInfo> = _debugInfo.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _autoAcceptEnabled = MutableStateFlow(false)
    val autoAcceptEnabled: StateFlow<Boolean> = _autoAcceptEnabled.asStateFlow()

    private val _acceptedTrips = MutableStateFlow(0)
    val acceptedTrips: StateFlow<Int> = _acceptedTrips.asStateFlow()

    private val _rejectedTrips = MutableStateFlow(0)
    val rejectedTrips: StateFlow<Int> = _rejectedTrips.asStateFlow()

    private val _detectedTrips = MutableStateFlow(0)
    val detectedTrips: StateFlow<Int> = _detectedTrips.asStateFlow()

    private val _totalSar = MutableStateFlow(0f)
    val totalSar: StateFlow<Float> = _totalSar.asStateFlow()

    private val eventLog = mutableListOf<String>()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        _debugInfo.value = _debugInfo.value.copy(serviceConnected = connected)
    }

    fun setAutoAcceptEnabled(enabled: Boolean) {
        _autoAcceptEnabled.value = enabled
    }

    fun updateFromPrefs(accepted: Int, rejected: Int, detected: Int, totalSar: Float) {
        _acceptedTrips.value = accepted
        _rejectedTrips.value = rejected
        _detectedTrips.value = detected
        _totalSar.value = totalSar
    }

    fun resetStats(accepted: Int = 0, rejected: Int = 0, detected: Int = 0, sar: Float = 0f) {
        _acceptedTrips.value = accepted
        _rejectedTrips.value = rejected
        _detectedTrips.value = detected
        _totalSar.value = sar
    }

    fun incrementAccepted(sarAmount: Float) {
        _acceptedTrips.value += 1
        _totalSar.value += sarAmount
    }

    fun incrementRejected() {
        _rejectedTrips.value += 1
    }

    fun incrementDetected() {
        _detectedTrips.value += 1
    }

    fun addEventLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$ts] $msg"
        synchronized(eventLog) {
            eventLog.add(0, entry)
            if (eventLog.size > 100) eventLog.removeLast()
        }
        _debugInfo.value = _debugInfo.value.copy(eventLog = eventLog.toList())
    }

    fun updateDebug(
        totalEvents: Int? = null,
        lastPackage: String? = null,
        lastEvent: String? = null,
        lastClass: String? = null,
        jeenyDetected: Boolean? = null,
        acceptButtonFound: Boolean? = null,
        rawVisibleText: String? = null,
        detectionReason: String? = null,
        acceptClickResult: String? = null
    ) {
        val cur = _debugInfo.value
        _debugInfo.value = cur.copy(
            totalEvents = totalEvents ?: cur.totalEvents,
            lastPackage = lastPackage ?: cur.lastPackage,
            lastEvent = lastEvent ?: cur.lastEvent,
            lastClass = lastClass ?: cur.lastClass,
            jeenyDetected = jeenyDetected ?: cur.jeenyDetected,
            acceptButtonFound = acceptButtonFound ?: cur.acceptButtonFound,
            rawVisibleText = rawVisibleText ?: cur.rawVisibleText,
            detectionReason = detectionReason ?: cur.detectionReason,
            acceptClickResult = acceptClickResult ?: cur.acceptClickResult
        )
    }
}
