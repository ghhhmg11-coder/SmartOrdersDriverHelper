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

        val SUPPORTED_PACKAGES = mapOf(
            "jeeny"  to "com.jeeny.driver",
            "uber"   to "com.ubercab.driver",
            "careem" to "com.careem.captain",
            "bolt"   to "ee.mtakso.driver"
        )

        // Alternative package names that some Jeeny builds use
        private val PACKAGE_ALIASES = mapOf(
            "com.jeeny.drivers"    to "jeeny",
            "sa.com.jeeny.driver"  to "jeeny",
            "com.jeeny.driverapp"  to "jeeny",
            "com.careem.acma"      to "careem",
            "com.bolt.driver"      to "bolt"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Debounce per app: last (combinedText, timestamp)
    private val lastDebounce = mutableMapOf<String, Pair<String, Long>>()
    private val debounceMs = 1500L

    // Set of app names currently processing — prevent double-fires
    private val inFlight = mutableSetOf<String>()

    // ─── Service lifecycle ────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "══════════════════════════════════════════")
        Log.i(TAG, " Smart Orders Accessibility Service STARTED")
        Log.i(TAG, " Monitoring: ${SUPPORTED_PACKAGES.values}")
        Log.i(TAG, "══════════════════════════════════════════")

        val info = AccessibilityServiceInfo().apply {
            // Receive ALL event types
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // Monitor ALL packages — we filter in code
            packageNames = null
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
        }
        serviceInfo = info
    }

    // ─── Event entry point — runs on MAIN thread ──────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // Resolve to our internal app name
        val appName = SUPPORTED_PACKAGES.entries.find { it.value == packageName }?.key
            ?: PACKAGE_ALIASES[packageName]
            ?: return  // Not a watched package

        Log.v(TAG, "[$appName] Event type=${event.eventType} from $packageName")

        // Collect roots from ALL visible windows while still on the MAIN thread.
        // This is critical for bottom-sheet detection — the bottom sheet lives in
        // a separate AccessibilityWindowInfo, not in rootInActiveWindow.
        val roots = collectAllWindowRoots(appName)
        if (roots.isEmpty()) {
            Log.v(TAG, "[$appName] No window roots found")
            return
        }

        // Build combined text from all roots
        val combinedText = buildCombinedText(roots)

        // Hand off to IO for everything after this point
        serviceScope.launch {
            try {
                processScreen(appName, combinedText, roots)
            } catch (e: Exception) {
                Log.e(TAG, "[$appName] Exception: ${e.message}", e)
            }
        }
    }

    // Gathers root nodes from all currently visible accessibility windows.
    // We check EVERY window — we don't filter by package here because a
    // Dialog/BottomSheet might report the parent app's package or a system package.
    private fun collectAllWindowRoots(triggerApp: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        // rootInActiveWindow first (always try it)
        rootInActiveWindow?.let { result.add(it) }

        // All other windows
        try {
            val allWindows = windows ?: return result
            for (window in allWindows) {
                try {
                    val root = window.root ?: continue
                    // Avoid duplicating the active window we already added
                    if (result.none { it.windowId == root.windowId }) {
                        result.add(root)
                        Log.v(TAG, "[$triggerApp] Window: id=${window.id} type=${window.type} pkg=${root.packageName}")
                    }
                } catch (e: Exception) {
                    Log.v(TAG, "[$triggerApp] Window read error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "[$triggerApp] windows list error: ${e.message}")
        }

        Log.d(TAG, "[$triggerApp] Total window roots collected: ${result.size}")
        return result
    }

    private fun buildCombinedText(roots: List<AccessibilityNodeInfo>): String {
        val sb = StringBuilder()
        roots.forEach { root -> collectText(root, sb) }
        return sb.toString()
    }

    // ─── Screen processing — runs on IO thread ────────────────────────────────

    private suspend fun processScreen(
        appName: String,
        combinedText: String,
        roots: List<AccessibilityNodeInfo>
    ) {
        val prefs = AppPreferences(applicationContext)

        if (!prefs.autoAcceptEnabled.first()) {
            Log.v(TAG, "[$appName] Auto-accept OFF — skipping")
            return
        }

        val targetApp = prefs.targetApp.first()
        if (targetApp != "all" && targetApp != appName) {
            Log.v(TAG, "[$appName] Not targeted (target=$targetApp)")
            return
        }

        if (combinedText.isBlank()) {
            Log.v(TAG, "[$appName] Empty combined text")
            return
        }

        // Debounce
        val now = System.currentTimeMillis()
        val prev = lastDebounce[appName]
        if (prev != null && prev.first == combinedText && now - prev.second < debounceMs) {
            Log.v(TAG, "[$appName] Debounce — same content within ${debounceMs}ms")
            return
        }
        lastDebounce[appName] = Pair(combinedText, now)

        // Prevent concurrent processing for same app
        if (inFlight.contains(appName)) {
            Log.v(TAG, "[$appName] Already processing — skipping")
            return
        }
        inFlight.add(appName)

        try {
            Log.d(TAG, "[$appName] ─── Evaluating screen ─────────────────────")
            Log.d(TAG, "[$appName] Combined text (${combinedText.length} chars): ${combinedText.take(400)}")

            val trip = TripParser.parse(appName, combinedText)
            if (trip == null) {
                Log.d(TAG, "[$appName] Not a trip request screen")
                return
            }

            Log.i(TAG, "[$appName] ✅ TRIP DETECTED — price=${trip.price} SAR, pickup=${trip.pickupDistance}km")

            val rules = EvaluatedRules(
                minPrice      = prefs.minPrice.first(),
                maxPrice      = prefs.maxPrice.first(),
                maxPickupDist = prefs.maxPickupDistance.first(),
                maxTripDist   = prefs.maxTripDistance.first()
            )

            val zones = loadZones()
            val (shouldAccept, reason) = evaluateRules(trip, rules, zones)

            // Save to database (increments detected/accepted counters via ViewModel query)
            val db = AppDatabase.getInstance(applicationContext)
            db.tripLogDao().insert(TripLogEntity(
                timestamp           = System.currentTimeMillis(),
                sourceApp           = appName,
                rawScreenText       = combinedText,
                price               = trip.price,
                pickupDistance      = trip.pickupDistance,
                tripDistance        = trip.tripDistance,
                pickupLocation      = trip.pickupLocation,
                destinationLocation = trip.destinationLocation,
                accepted            = shouldAccept,
                rejectionReason     = reason
            ))
            Log.d(TAG, "[$appName] Trip log saved (accepted=$shouldAccept)")

            if (shouldAccept) {
                val delayMs = prefs.delayMs.first()
                Log.i(TAG, "[$appName] ⏱ Accepting in ${delayMs}ms...")
                delay(delayMs)

                // Click on main thread — pass roots captured earlier
                withContext(Dispatchers.Main) {
                    // Re-fetch fresh roots at click time for the most current tree
                    val freshRoots = collectAllWindowRoots(appName)
                    performAcceptClick(appName, freshRoots, prefs)
                }
            } else {
                Log.i(TAG, "[$appName] ❌ REJECTED — $reason")
            }
        } finally {
            inFlight.remove(appName)
        }
    }

    // ─── Accept click — all strategies ───────────────────────────────────────

    private fun performAcceptClick(
        appName: String,
        roots: List<AccessibilityNodeInfo>,
        prefs: AppPreferences
    ) {
        Log.i(TAG, "[$appName] 🔍 Searching for accept button across ${roots.size} window(s)...")

        for ((idx, root) in roots.withIndex()) {
            Log.d(TAG, "[$appName] Searching window #$idx (${root.packageName})")

            val clicked = tryClickByText(appName, root)
                || tryClickByViewId(appName, root)
                || tryClickByDescription(appName, root)

            if (clicked) {
                Log.i(TAG, "[$appName] ✅ Accept button CLICKED in window #$idx!")
                serviceScope.launch {
                    if (prefs.vibrationEnabled.first()) {
                        @Suppress("DEPRECATION")
                        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        vib?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
                return
            }
        }

        Log.w(TAG, "[$appName] ⚠️ Accept button NOT FOUND in any window — dumping node trees:")
        roots.forEachIndexed { idx, root ->
            Log.w(TAG, "[$appName] ── Window #$idx (${root.packageName}) tree ──")
            dumpNodeTree(appName, root, 0)
        }
    }

    /** Strategy 1: match node text/contentDescription */
    private fun tryClickByText(appName: String, root: AccessibilityNodeInfo): Boolean {
        return findAndClick(appName, root, "text") { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            TripParser.isAcceptButtonText(text)
                || TripParser.containsAcceptButtonText(text)
                || TripParser.isAcceptButtonText(desc)
                || TripParser.containsAcceptButtonText(desc)
        }
    }

    /** Strategy 2: resource-id contains "accept"/"confirm" */
    private fun tryClickByViewId(appName: String, root: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("accept", "confirm", "approve", "btn_accept", "action_accept")
        return findAndClick(appName, root, "viewId") { node ->
            val id = node.viewIdResourceName?.lowercase() ?: ""
            keywords.any { id.contains(it) }
        }
    }

    /** Strategy 3: content description substring */
    private fun tryClickByDescription(appName: String, root: AccessibilityNodeInfo): Boolean {
        return findAndClick(appName, root, "description") { node ->
            val desc = node.contentDescription?.toString() ?: ""
            TripParser.containsAcceptButtonText(desc)
        }
    }

    private fun findAndClick(
        appName: String,
        node: AccessibilityNodeInfo?,
        strategy: String,
        depth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        node ?: return false
        if (depth > 25) return false

        if (predicate(node)) {
            Log.d(TAG, "[$appName][$strategy] Candidate: text='${node.text}' class='${node.className?.toString()?.substringAfterLast('.')}' clickable=${node.isClickable} id='${node.viewIdResourceName}'")
            if (performNodeClick(appName, node)) return true
        }

        for (i in 0 until node.childCount) {
            if (findAndClick(appName, node.getChild(i), strategy, depth + 1, predicate)) return true
        }
        return false
    }

    private fun performNodeClick(appName: String, node: AccessibilityNodeInfo): Boolean {
        // Direct click
        if (node.isClickable && node.isEnabled) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "[$appName] ✓ Direct click succeeded")
                return true
            }
        }
        // Walk up parents (up to 6 levels)
        var parent = node.parent
        var level = 0
        while (parent != null && level < 6) {
            if (parent.isClickable && parent.isEnabled) {
                if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "[$appName] ✓ Parent click (level=$level, class=${parent.className?.toString()?.substringAfterLast('.')}) succeeded")
                    return true
                }
            }
            parent = parent.parent
            level++
        }
        // Force-click the original node (ignore clickable flag)
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "[$appName] ✓ Force-click succeeded")
            return true
        }
        return false
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        node ?: return
        if (!node.text.isNullOrBlank()) sb.append(node.text).append("\n")
        if (!node.contentDescription.isNullOrBlank()) sb.append(node.contentDescription).append("\n")
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb)
    }

    private suspend fun loadZones(): List<Zone> {
        val db = AppDatabase.getInstance(applicationContext)
        return db.zoneDao().getAllZones().first().map {
            Zone(it.id, it.name, it.latitude, it.longitude, it.radiusMeters, it.isGreen)
        }
    }

    private fun evaluateRules(trip: DetectedTrip, rules: EvaluatedRules, zones: List<Zone>): Pair<Boolean, String> {
        if (trip.price > 0 && trip.price < rules.minPrice)
            return Pair(false, "السعر ${trip.price} أقل من الحد ${rules.minPrice} SAR")
        if (trip.price > rules.maxPrice)
            return Pair(false, "السعر ${trip.price} أعلى من الحد ${rules.maxPrice} SAR")
        if (trip.pickupDistance > rules.maxPickupDist && trip.pickupDistance > 0)
            return Pair(false, "مسافة الاستلام ${trip.pickupDistance} > ${rules.maxPickupDist} km")
        if (trip.tripDistance > rules.maxTripDist && trip.tripDistance > 0)
            return Pair(false, "مسافة الرحلة ${trip.tripDistance} > ${rules.maxTripDist} km")
        if (zones.isNotEmpty()) {
            val zr = ZoneChecker.checkZones(0.0, 0.0, 0.0, 0.0, zones)
            if (zr.blocked) return Pair(false, zr.reason)
        }
        return Pair(true, "")
    }

    /** Dump accessibility node tree to logcat for debugging */
    private fun dumpNodeTree(appName: String, node: AccessibilityNodeInfo?, depth: Int) {
        node ?: return
        if (depth > 18) { Log.d(TAG, "[$appName]${"  ".repeat(depth)}[…]"); return }
        val indent = "  ".repeat(depth)
        val cls    = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val text   = node.text?.toString()?.take(80) ?: ""
        val desc   = node.contentDescription?.toString()?.take(80) ?: ""
        val id     = node.viewIdResourceName?.substringAfterLast('/') ?: ""
        val flags  = buildString {
            if (node.isClickable) append("CLICK ")
            if (node.isEnabled)   append("EN ")
            if (node.isFocusable) append("FOCUS ")
        }
        Log.d(TAG, "[$appName]$indent[$cls] \"$text\" desc=\"$desc\" id=$id $flags")
        for (i in 0 until node.childCount) dumpNodeTree(appName, node.getChild(i), depth + 1)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

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
