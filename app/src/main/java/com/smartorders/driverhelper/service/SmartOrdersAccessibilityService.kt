package com.smartorders.driverhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.smartorders.driverhelper.data.local.AppDatabase
import com.smartorders.driverhelper.data.local.entity.TripLogEntity
import com.smartorders.driverhelper.data.preferences.AppPreferences
import com.smartorders.driverhelper.model.DetectedTrip
import com.smartorders.driverhelper.model.Zone
import com.smartorders.driverhelper.utils.TripParser
import com.smartorders.driverhelper.utils.ZoneChecker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private data class EvaluatedRules(
    val minPrice: Double,
    val maxPrice: Double,
    val maxPickupDist: Double,
    val maxTripDist: Double
)

class SmartOrdersAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SmartOrdersAS"
        var isRunning = false

        // Known Jeeny package names (try both variants)
        val SUPPORTED_PACKAGES = mapOf(
            "jeeny"  to "com.jeeny.driver",
            "uber"   to "com.ubercab.driver",
            "careem" to "com.careem.captain",
            "bolt"   to "ee.mtakso.driver"
        )

        // Additional known package aliases
        private val PACKAGE_ALIASES = mapOf(
            "com.jeeny.drivers"   to "jeeny",
            "sa.com.jeeny.driver" to "jeeny",
            "com.careem.acma"     to "careem",
            "com.bolt.driver"     to "bolt"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Debounce per-package: track last processed text per app
    private val lastProcessed = mutableMapOf<String, Pair<String, Long>>()
    private val debounceMs = 1500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "════════════════════════════════════")
        Log.i(TAG, "Smart Orders Accessibility Service CONNECTED")
        Log.i(TAG, "Monitoring packages: ${SUPPORTED_PACKAGES.values}")
        Log.i(TAG, "════════════════════════════════════")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
            packageNames = null  // monitor ALL packages — filter in code
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // Resolve app name — check primary map and aliases
        val appName = SUPPORTED_PACKAGES.entries.find { it.value == packageName }?.key
            ?: PACKAGE_ALIASES[packageName]
            ?: return  // Not a supported app — ignore silently

        // Capture root node on the MAIN thread immediately (before coroutine launch)
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.v(TAG, "[$appName] rootInActiveWindow is null — skipping event")
            return
        }

        serviceScope.launch {
            try {
                processEvent(appName, packageName, rootNode)
            } catch (e: Exception) {
                Log.e(TAG, "[$appName] Exception in processEvent: ${e.message}", e)
            }
        }
    }

    private suspend fun processEvent(appName: String, packageName: String, rootNode: AccessibilityNodeInfo) {
        val prefs = AppPreferences(applicationContext)

        val autoAccept = prefs.autoAcceptEnabled.first()
        val targetApp  = prefs.targetApp.first()

        Log.v(TAG, "[$appName] Event received. autoAccept=$autoAccept, targetApp=$targetApp")

        if (!autoAccept) {
            Log.v(TAG, "[$appName] Auto-accept is OFF — skipping")
            return
        }

        val isTargeted = targetApp == "all" || targetApp == appName
        if (!isTargeted) {
            Log.v(TAG, "[$appName] Not targeted (targetApp=$targetApp) — skipping")
            return
        }

        // Collect all visible text from the node tree
        val allText = StringBuilder()
        collectText(rootNode, allText)
        val rawText = allText.toString()

        if (rawText.isBlank()) {
            Log.v(TAG, "[$appName] Empty screen text — skipping")
            return
        }

        // Debounce: skip if same content within debounce window
        val now = System.currentTimeMillis()
        val prev = lastProcessed[appName]
        if (prev != null && prev.first == rawText && now - prev.second < debounceMs) {
            Log.v(TAG, "[$appName] Debounce — same text within ${debounceMs}ms, skipping")
            return
        }
        lastProcessed[appName] = Pair(rawText, now)

        Log.d(TAG, "[$appName] ── Processing screen ──────────────────")
        Log.d(TAG, "[$appName] Screen text (first 300 chars): ${rawText.take(300)}")

        // Parse the trip from screen text
        val trip = TripParser.parse(appName, rawText)
        if (trip == null) {
            Log.d(TAG, "[$appName] Not a trip request screen — no action")
            return
        }

        Log.i(TAG, "[$appName] ✅ TRIP DETECTED: price=${trip.price} SAR, pickup=${trip.pickupDistance}km, trip=${trip.tripDistance}km")

        val rules = EvaluatedRules(
            minPrice     = prefs.minPrice.first(),
            maxPrice     = prefs.maxPrice.first(),
            maxPickupDist = prefs.maxPickupDistance.first(),
            maxTripDist  = prefs.maxTripDistance.first()
        )
        Log.d(TAG, "[$appName] Rules: minPrice=${rules.minPrice}, maxPrice=${rules.maxPrice}, maxPickup=${rules.maxPickupDist}km, maxTrip=${rules.maxTripDist}km")

        val zones = loadZones()
        val (shouldAccept, reason) = evaluateRules(trip, rules, zones)

        Log.i(TAG, "[$appName] Decision: shouldAccept=$shouldAccept, reason='$reason'")

        // Persist trip log to DB
        val db = AppDatabase.getInstance(applicationContext)
        val logEntity = TripLogEntity(
            timestamp           = System.currentTimeMillis(),
            sourceApp           = appName,
            rawScreenText       = rawText,
            price               = trip.price,
            pickupDistance      = trip.pickupDistance,
            tripDistance        = trip.tripDistance,
            pickupLocation      = trip.pickupLocation,
            destinationLocation = trip.destinationLocation,
            accepted            = shouldAccept,
            rejectionReason     = reason
        )
        db.tripLogDao().insert(logEntity)
        Log.d(TAG, "[$appName] Trip log saved to database")

        if (shouldAccept) {
            val delayMs = prefs.delayMs.first()
            Log.i(TAG, "[$appName] Waiting ${delayMs}ms before clicking accept...")
            delay(delayMs)

            withContext(Dispatchers.Main) {
                performAcceptClick(appName, rootNode, prefs)
            }
        } else {
            Log.i(TAG, "[$appName] ❌ Trip REJECTED: $reason")
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

    private suspend fun loadZones(): List<Zone> {
        val db = AppDatabase.getInstance(applicationContext)
        return db.zoneDao().getAllZones().first().map {
            Zone(it.id, it.name, it.latitude, it.longitude, it.radiusMeters, it.isGreen)
        }
    }

    private fun evaluateRules(trip: DetectedTrip, rules: EvaluatedRules, zones: List<Zone>): Pair<Boolean, String> {
        if (trip.price > 0 && trip.price < rules.minPrice) {
            return Pair(false, "السعر أقل من الحد الأدنى: ${trip.price} < ${rules.minPrice} SAR")
        }
        if (trip.price > rules.maxPrice) {
            return Pair(false, "السعر أعلى من الحد الأقصى: ${trip.price} > ${rules.maxPrice} SAR")
        }
        if (trip.pickupDistance > rules.maxPickupDist && trip.pickupDistance > 0) {
            return Pair(false, "مسافة الاستلام تجاوزت الحد: ${trip.pickupDistance} > ${rules.maxPickupDist} km")
        }
        if (trip.tripDistance > rules.maxTripDist && trip.tripDistance > 0) {
            return Pair(false, "مسافة الرحلة تجاوزت الحد: ${trip.tripDistance} > ${rules.maxTripDist} km")
        }
        if (zones.isNotEmpty()) {
            val zoneResult = ZoneChecker.checkZones(0.0, 0.0, 0.0, 0.0, zones)
            if (zoneResult.blocked) return Pair(false, zoneResult.reason)
        }
        return Pair(true, "")
    }

    // ── Click the Accept button using multiple strategies ──────────────────────

    private fun performAcceptClick(appName: String, root: AccessibilityNodeInfo, prefs: AppPreferences) {
        Log.i(TAG, "[$appName] 🔍 Searching for accept button...")

        // Re-fetch root to get latest node tree at click time
        val freshRoot = rootInActiveWindow ?: root

        val clicked = tryClickByText(appName, freshRoot)
            || tryClickByViewId(appName, freshRoot)
            || tryClickByDescription(appName, freshRoot)

        if (clicked) {
            Log.i(TAG, "[$appName] ✅ Accept button CLICKED successfully!")
            serviceScope.launch {
                val vibrate = prefs.vibrationEnabled.first()
                if (vibrate) {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } else {
            Log.w(TAG, "[$appName] ⚠️ Accept button NOT FOUND — dumping node tree:")
            dumpNodeTree(freshRoot, 0, appName)
        }
    }

    /** Strategy 1: find by node text matching accept button variants */
    private fun tryClickByText(appName: String, root: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "[$appName] Strategy 1: click by text")
        return findAndClickByPredicate(appName, root) { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            TripParser.isAcceptButtonText(text) || TripParser.isAcceptButtonText(desc) ||
                    TripParser.containsAcceptButtonText(text) || TripParser.containsAcceptButtonText(desc)
        }
    }

    /** Strategy 2: find by common resource-id patterns */
    private fun tryClickByViewId(appName: String, root: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "[$appName] Strategy 2: click by view ID")
        val acceptIdKeywords = listOf("accept", "confirm", "btn_accept", "button_accept", "action_accept")
        return findAndClickByPredicate(appName, root) { node ->
            val id = node.viewIdResourceName?.lowercase() ?: ""
            acceptIdKeywords.any { id.contains(it) }
        }
    }

    /** Strategy 3: find by content description */
    private fun tryClickByDescription(appName: String, root: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "[$appName] Strategy 3: click by content description")
        return findAndClickByPredicate(appName, root) { node ->
            val desc = node.contentDescription?.toString() ?: ""
            TripParser.containsAcceptButtonText(desc)
        }
    }

    private fun findAndClickByPredicate(
        appName: String,
        node: AccessibilityNodeInfo?,
        depth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        node ?: return false
        if (depth > 20) return false  // Safety limit

        if (predicate(node)) {
            Log.d(TAG, "[$appName] Found candidate node: text='${node.text}' class='${node.className}' clickable=${node.isClickable} id='${node.viewIdResourceName}'")
            if (performNodeClick(appName, node)) return true
        }

        for (i in 0 until node.childCount) {
            if (findAndClickByPredicate(appName, node.getChild(i), depth + 1, predicate)) return true
        }
        return false
    }

    private fun performNodeClick(appName: String, node: AccessibilityNodeInfo): Boolean {
        // Try the node itself
        if (node.isClickable && node.isEnabled) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "[$appName] Direct click result: $result")
            if (result) return true
        }

        // Walk up to 5 levels to find a clickable parent
        var parent = node.parent
        var level = 0
        while (parent != null && level < 5) {
            if (parent.isClickable && parent.isEnabled) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "[$appName] Parent click (level $level) result: $result, class='${parent.className}'")
                if (result) return true
            }
            parent = parent.parent
            level++
        }

        // Last resort: global ACTION_CLICK on the node regardless of clickable flag
        val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "[$appName] Force click result: $result")
        return result
    }

    /** Dump the accessibility node tree to logcat for debugging */
    private fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int, appName: String) {
        node ?: return
        if (depth > 15) {
            Log.d(TAG, "[$appName] ${"  ".repeat(depth)}[... truncated ...]")
            return
        }
        val indent = "  ".repeat(depth)
        val text    = node.text?.toString()?.take(60) ?: ""
        val desc    = node.contentDescription?.toString()?.take(60) ?: ""
        val cls     = node.className?.toString()?.substringAfterLast('.') ?: ""
        val id      = node.viewIdResourceName ?: ""
        val click   = if (node.isClickable) "CLICKABLE" else ""
        val enabled = if (node.isEnabled) "" else "DISABLED"
        Log.d(TAG, "[$appName] $indent[$cls] text='$text' desc='$desc' id='$id' $click $enabled")
        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1, appName)
        }
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
