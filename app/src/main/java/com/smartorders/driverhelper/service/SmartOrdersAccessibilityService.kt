package com.smartorders.driverhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.smartorders.driverhelper.data.local.AppDatabase
import com.smartorders.driverhelper.data.local.entity.TripLogEntity
import com.smartorders.driverhelper.data.local.entity.ZoneEntity
import com.smartorders.driverhelper.data.preferences.AppPreferences
import com.smartorders.driverhelper.model.DetectedTrip
import com.smartorders.driverhelper.model.Zone
import com.smartorders.driverhelper.utils.TripParser
import com.smartorders.driverhelper.utils.ZoneChecker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SmartOrdersAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SmartOrdersAS"
        var isRunning = false

        val SUPPORTED_PACKAGES = mapOf(
            "jeeny" to "com.jeeny.driver",
            "uber" to "com.ubercab.driver",
            "careem" to "com.careem.captain",
            "bolt" to "ee.mtakso.driver"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastProcessedText = ""
    private var lastProcessedTime = 0L
    private val debounceMs = 2000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "Accessibility Service connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        serviceScope.launch {
            val prefs = AppPreferences(applicationContext)
            val autoAccept = prefs.autoAcceptEnabled.first()
            if (!autoAccept) return@launch

            val targetApp = prefs.targetApp.first()
            val isTargeted = targetApp == "all" ||
                    SUPPORTED_PACKAGES[targetApp] == packageName ||
                    SUPPORTED_PACKAGES.values.contains(packageName)
            if (!isTargeted) return@launch

            val rootNode = rootInActiveWindow ?: return@launch
            val allText = StringBuilder()
            collectText(rootNode, allText)
            val rawText = allText.toString()

            val now = System.currentTimeMillis()
            if (rawText == lastProcessedText && now - lastProcessedTime < debounceMs) return@launch
            lastProcessedText = rawText
            lastProcessedTime = now

            val appName = SUPPORTED_PACKAGES.entries
                .find { it.value == packageName }?.key ?: packageName

            val trip = TripParser.parse(appName, rawText) ?: return@launch
            Log.d(TAG, "Detected trip from $appName: price=${trip.price}, pickup=${trip.pickupDistance}km")

            val rules = loadRules(prefs)
            val zones = loadZones()

            val (shouldAccept, reason) = evaluateRules(trip, rules, zones)
            val db = AppDatabase.getInstance(applicationContext)

            val logEntity = TripLogEntity(
                timestamp = System.currentTimeMillis(),
                sourceApp = appName,
                rawScreenText = rawText,
                price = trip.price,
                pickupDistance = trip.pickupDistance,
                tripDistance = trip.tripDistance,
                pickupLocation = trip.pickupLocation,
                destinationLocation = trip.destinationLocation,
                accepted = shouldAccept,
                rejectionReason = reason
            )
            db.tripLogDao().insert(logEntity)

            if (shouldAccept) {
                val delay = prefs.delayMs.first()
                delay(delay)
                withContext(Dispatchers.Main) {
                    clickAcceptButton(rootNode, prefs)
                }
            } else {
                Log.d(TAG, "Trip rejected: $reason")
            }
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        node ?: return
        if (!node.text.isNullOrBlank()) sb.append(node.text).append("\n")
        if (!node.contentDescription.isNullOrBlank()) sb.append(node.contentDescription).append("\n")
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb)
        }
    }

    private suspend fun loadRules(prefs: AppPreferences) = object {
        val minPrice = prefs.minPrice.first()
        val maxPrice = prefs.maxPrice.first()
        val maxPickupDist = prefs.maxPickupDistance.first()
        val maxTripDist = prefs.maxTripDistance.first()
    }

    private suspend fun loadZones(): List<Zone> {
        val db = AppDatabase.getInstance(applicationContext)
        return db.zoneDao().getAllZones().first().map {
            Zone(it.id, it.name, it.latitude, it.longitude, it.radiusMeters, it.isGreen)
        }
    }

    private fun evaluateRules(trip: DetectedTrip, rules: Any, zones: List<Zone>): Pair<Boolean, String> {
        val r = rules as? Any ?: return Pair(false, "Rules error")
        val minP = (r::class.java.getField("minPrice").get(r) as Double)
        val maxP = (r::class.java.getField("maxPrice").get(r) as Double)
        val maxPkp = (r::class.java.getField("maxPickupDist").get(r) as Double)
        val maxTrip = (r::class.java.getField("maxTripDist").get(r) as Double)

        if (trip.price < minP) return Pair(false, "السعر أقل من الحد الأدنى: ${trip.price} < $minP SAR")
        if (trip.price > maxP) return Pair(false, "السعر أعلى من الحد الأقصى: ${trip.price} > $maxP SAR")
        if (trip.pickupDistance > maxPkp && trip.pickupDistance > 0) {
            return Pair(false, "مسافة الاستلام تجاوزت الحد: ${trip.pickupDistance} > $maxPkp km")
        }
        if (trip.tripDistance > maxTrip && trip.tripDistance > 0) {
            return Pair(false, "مسافة الرحلة تجاوزت الحد: ${trip.tripDistance} > $maxTrip km")
        }
        if (zones.isNotEmpty()) {
            val zoneResult = ZoneChecker.checkZones(0.0, 0.0, 0.0, 0.0, zones)
            if (zoneResult.blocked) return Pair(false, zoneResult.reason)
        }
        return Pair(true, "")
    }

    private fun clickAcceptButton(root: AccessibilityNodeInfo, prefs: AppPreferences) {
        val found = findAndClickAccept(root)
        if (found) {
            Log.i(TAG, "Accept button clicked successfully")
            serviceScope.launch {
                val vibrate = prefs.vibrationEnabled.first()
                if (vibrate) {
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } else {
            Log.w(TAG, "Accept button not found")
        }
    }

    private fun findAndClickAccept(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        val text = node.text?.toString() ?: ""
        if (TripParser.isAcceptButtonText(text)) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            } else {
                var parent = node.parent
                repeat(3) {
                    if (parent?.isClickable == true) {
                        parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    parent = parent?.parent
                }
            }
        }
        for (i in 0 until node.childCount) {
            if (findAndClickAccept(node.getChild(i))) return true
        }
        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        Log.i(TAG, "Accessibility Service destroyed")
    }
}
