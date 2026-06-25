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
        private const val TAG     = "SmartOrdersAS"
        private const val TAG_DBG = "SmartOrders_DEBUG"

        var isRunning = false

        val SUPPORTED_PACKAGES = mapOf(
            "jeeny"  to "com.jeeny.driver",
            "uber"   to "com.ubercab.driver",
            "careem" to "com.careem.captain",
            "bolt"   to "ee.mtakso.driver"
        )

        private val PACKAGE_ALIASES = mapOf(
            "com.jeeny.drivers"    to "jeeny",
            "sa.com.jeeny.driver"  to "jeeny",
            "com.jeeny.driverapp"  to "jeeny",
            "com.careem.acma"      to "careem",
            "com.bolt.driver"      to "bolt"
        )

        // All text variants that mean "accept the trip" — across all apps
        val ACCEPT_TEXTS = setOf(
            "قبول العرض",   // Jeeny — confirmed from screenshot
            "قبول الطلب",
            "قبول",
            "اقبل",
            "قبل",
            "موافق",
            "Accept",
            "ACCEPT",
            "Accept Offer",
            "Accept Trip"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Prevent re-clicking the same trip within 3 seconds
    private var lastClickTime = 0L
    private val clickCooldownMs = 3000L

    // Prevent concurrent accept-click routines
    private val inFlight = mutableSetOf<String>()

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        val info = AccessibilityServiceInfo().apply {
            eventTypes   = AccessibilityEvent.TYPES_ALL_MASK   // every event type
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = null                                 // null = all packages
            flags        = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                           AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                           AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
        }
        serviceInfo = info

        val eff = serviceInfo
        Log.i(TAG_DBG, "══════════════════════════════════════════════")
        Log.i(TAG_DBG, " SmartOrders Accessibility Service CONNECTED")
        Log.i(TAG_DBG, " effectiveEventTypes = ${eff?.eventTypes}")
        Log.i(TAG_DBG, " effectivePackages   = ${eff?.packageNames?.toList() ?: "ALL"}")
        Log.i(TAG_DBG, " effectiveFlags      = ${eff?.flags}")
        Log.i(TAG_DBG, " watching            = ${SUPPORTED_PACKAGES.values}")
        Log.i(TAG_DBG, "══════════════════════════════════════════════")
    }

    // ── Event entry — runs on MAIN thread ─────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ── RAW DEBUG LOG — before any filtering ─────────────────────────────
        val rawPkg  = event?.packageName?.toString() ?: "(null)"
        val rawType = event?.eventType ?: -1
        val rawCls  = event?.className?.toString() ?: "(null)"
        Log.d(TAG_DBG, "EVENT pkg=$rawPkg type=$rawType cls=$rawCls")
        // ─────────────────────────────────────────────────────────────────────

        event ?: return
        val packageName = event.packageName?.toString() ?: return

        val appName = SUPPORTED_PACKAGES.entries.find { it.value == packageName }?.key
            ?: PACKAGE_ALIASES[packageName]
            ?: return   // Not a supported app — ignore

        // ── STEP 1: Scan ALL accessibility windows RIGHT NOW (main thread) ───
        // The Jeeny trip request is a BottomSheet dialog that lives in a SEPARATE
        // AccessibilityWindow from the map. rootInActiveWindow only returns the
        // map window. We must iterate windows to find the BottomSheet.
        val allRoots = getAllWindowRoots(appName)

        // ── STEP 2: PRIMARY — find "قبول العرض" (or any accept text) node ────
        // We do this before any text parsing. Button presence = trip detected.
        val acceptNode = findAcceptNode(allRoots)

        if (acceptNode != null) {
            Log.i(TAG_DBG, "[$appName] ✅ ACCEPT BUTTON FOUND: text='${acceptNode.text}' class='${acceptNode.className}'")

            // Cooldown guard — don't re-click same trip
            val now = System.currentTimeMillis()
            if (now - lastClickTime < clickCooldownMs) {
                Log.d(TAG_DBG, "[$appName] Cooldown active — skipping duplicate click")
                return
            }
            lastClickTime = now

            // Collect screen text for logging / rules check (from all roots)
            val screenText = buildCombinedText(allRoots)

            serviceScope.launch {
                handleTripDetected(appName, acceptNode, screenText)
            }
        } else {
            // STEP 3: Is this a Jeeny screen at all? Check for known markers.
            val screenText = buildCombinedText(allRoots)
            val hasJeenyMarkers = listOf("يبعد", "دقائق", "دقيقة", "كم", "مشوار").any { screenText.contains(it) }

            if (hasJeenyMarkers) {
                Log.w(TAG_DBG, "[$appName] ⚠️ Jeeny markers found but NO ACCEPT BUTTON detected!")
                Log.w(TAG_DBG, "[$appName] Screen text: ${screenText.take(500)}")
                Log.w(TAG_DBG, "[$appName] Dumping all window trees:")
                allRoots.forEachIndexed { i, root ->
                    Log.w(TAG_DBG, "[$appName] ─── Window #$i (pkg=${root.packageName}) ───")
                    dumpTree(appName, root, 0)
                }
            }
        }
    }

    // ── Gather roots from ALL visible windows ─────────────────────────────────

    private fun getAllWindowRoots(appName: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        // Always try rootInActiveWindow first
        rootInActiveWindow?.let { root ->
            result.add(root)
            Log.v(TAG_DBG, "[$appName] rootInActiveWindow: pkg=${root.packageName}")
        }

        // Iterate all windows — captures dialogs, overlays, bottom sheets
        try {
            val wins = windows ?: emptyList()
            Log.d(TAG_DBG, "[$appName] Total windows available: ${wins.size}")
            for (win in wins) {
                try {
                    val root = win.root ?: continue
                    if (result.none { it.windowId == root.windowId }) {
                        result.add(root)
                        Log.v(TAG_DBG, "[$appName] Window id=${win.id} type=${win.type} pkg=${root.packageName}")
                    }
                } catch (e: Exception) {
                    Log.v(TAG_DBG, "[$appName] Window error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.v(TAG_DBG, "[$appName] windows list error: ${e.message}")
        }

        Log.d(TAG_DBG, "[$appName] Scanning ${result.size} window root(s)")
        return result
    }

    // ── PRIMARY: find any node containing accept text ─────────────────────────

    private fun findAcceptNode(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (root in roots) {
            val found = findAcceptInTree(root, 0)
            if (found != null) return found
        }
        return null
    }

    private fun findAcceptInTree(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        node ?: return null
        if (depth > 30) return null

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        val isAccept = ACCEPT_TEXTS.any { accepted ->
            text.equals(accepted, ignoreCase = true) ||
            text.contains(accepted, ignoreCase = true) ||
            desc.equals(accepted, ignoreCase = true) ||
            desc.contains(accepted, ignoreCase = true)
        }

        if (isAccept) {
            Log.i(TAG_DBG, "Found accept node: text='$text' desc='$desc' cls=${node.className} clickable=${node.isClickable}")
            return node
        }

        for (i in 0 until node.childCount) {
            val found = findAcceptInTree(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
    }

    // ── Handle detected trip ──────────────────────────────────────────────────

    private suspend fun handleTripDetected(
        appName: String,
        acceptNode: AccessibilityNodeInfo,
        screenText: String
    ) {
        if (inFlight.contains(appName)) {
            Log.d(TAG_DBG, "[$appName] Already handling a trip — skip")
            return
        }
        inFlight.add(appName)

        try {
            val prefs = AppPreferences(applicationContext)

            if (!prefs.autoAcceptEnabled.first()) {
                Log.i(TAG_DBG, "[$appName] Auto-accept is OFF — not clicking")
                return
            }
            val targetApp = prefs.targetApp.first()
            if (targetApp != "all" && targetApp != appName) {
                Log.i(TAG_DBG, "[$appName] App not targeted (target=$targetApp)")
                return
            }

            // Parse trip details for logging / rules evaluation
            val trip = TripParser.parse(appName, screenText)
            Log.i(TAG_DBG, "[$appName] Trip: price=${trip?.price} pickup=${trip?.pickupDistance}km trip=${trip?.tripDistance}km")

            // Evaluate rules
            val rules = EvaluatedRules(
                minPrice      = prefs.minPrice.first(),
                maxPrice      = prefs.maxPrice.first(),
                maxPickupDist = prefs.maxPickupDistance.first(),
                maxTripDist   = prefs.maxTripDistance.first()
            )

            val tripPrice    = trip?.price ?: 0.0
            val tripPickup   = trip?.pickupDistance ?: 0.0
            val tripDistance = trip?.tripDistance ?: 0.0
            val zones        = loadZones()

            val (shouldAccept, reason) = evaluateRules(tripPrice, tripPickup, tripDistance, rules, zones)
            Log.i(TAG_DBG, "[$appName] Decision: accept=$shouldAccept reason='$reason'")

            // Save to DB regardless (counts as detected)
            AppDatabase.getInstance(applicationContext).tripLogDao().insert(
                TripLogEntity(
                    timestamp           = System.currentTimeMillis(),
                    sourceApp           = appName,
                    rawScreenText       = screenText,
                    price               = tripPrice,
                    pickupDistance      = tripPickup,
                    tripDistance        = tripDistance,
                    pickupLocation      = trip?.pickupLocation ?: "",
                    destinationLocation = trip?.destinationLocation ?: "",
                    accepted            = shouldAccept,
                    rejectionReason     = reason
                )
            )

            if (!shouldAccept) {
                Log.i(TAG_DBG, "[$appName] ❌ Rejected: $reason")
                return
            }

            val delayMs = prefs.delayMs.first()
            Log.i(TAG_DBG, "[$appName] ⏱ Clicking in ${delayMs}ms...")
            delay(delayMs)

            // Click on main thread with fresh window scan
            withContext(Dispatchers.Main) {
                val freshRoots = getAllWindowRoots(appName)
                val freshNode  = findAcceptNode(freshRoots) ?: acceptNode
                val clicked    = performClick(appName, freshNode)

                Log.i(TAG_DBG, "[$appName] Click result: $clicked")

                if (!clicked) {
                    Log.w(TAG_DBG, "[$appName] Click failed — dumping fresh tree:")
                    freshRoots.forEachIndexed { i, r ->
                        Log.w(TAG_DBG, "[$appName] Window #$i: ${r.packageName}")
                        dumpTree(appName, r, 0)
                    }
                } else {
                    serviceScope.launch {
                        if (prefs.vibrationEnabled.first()) {
                            @Suppress("DEPRECATION")
                            val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            vib?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }
            }
        } finally {
            inFlight.remove(appName)
        }
    }

    // ── Click the node — 4 strategies ────────────────────────────────────────

    private fun performClick(appName: String, node: AccessibilityNodeInfo): Boolean {
        // 1. Direct click on the node
        if (node.isEnabled) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG_DBG, "[$appName] ✓ Direct click")
                return true
            }
        }

        // 2. Walk up parents (up to 8 levels) looking for clickable ancestor
        var parent = node.parent
        var level  = 0
        while (parent != null && level < 8) {
            if (parent.isClickable && parent.isEnabled) {
                if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG_DBG, "[$appName] ✓ Parent click level=$level cls=${parent.className?.toString()?.substringAfterLast('.')}")
                    return true
                }
            }
            parent = parent.parent
            level++
        }

        // 3. Force click — ignore clickable flag
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG_DBG, "[$appName] ✓ Force click")
            return true
        }

        // 4. Find the first sibling/child that IS clickable and has matching text
        val root = rootInActiveWindow
        if (root != null) {
            val clickable = findFirstClickableWithText(root, node.text?.toString() ?: "قبول العرض")
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG_DBG, "[$appName] ✓ Sibling/child clickable click")
                return true
            }
        }

        return false
    }

    private fun findFirstClickableWithText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        node ?: return null
        val nodeText = node.text?.toString()?.trim() ?: ""
        if (node.isClickable && node.isEnabled && nodeText.contains(text, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = findFirstClickableWithText(node.getChild(i), text)
            if (found != null) return found
        }
        return null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildCombinedText(roots: List<AccessibilityNodeInfo>): String {
        val sb = StringBuilder()
        roots.forEach { collectText(it, sb) }
        return sb.toString()
    }

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

    private fun evaluateRules(
        price: Double,
        pickupDist: Double,
        tripDist: Double,
        rules: EvaluatedRules,
        zones: List<Zone>
    ): Pair<Boolean, String> {
        if (price > 0 && price < rules.minPrice)
            return Pair(false, "السعر $price أقل من الحد ${rules.minPrice} SAR")
        if (price > 0 && price > rules.maxPrice)
            return Pair(false, "السعر $price أعلى من الحد ${rules.maxPrice} SAR")
        if (pickupDist > rules.maxPickupDist && pickupDist > 0)
            return Pair(false, "مسافة الاستلام $pickupDist > ${rules.maxPickupDist} km")
        if (tripDist > rules.maxTripDist && tripDist > 0)
            return Pair(false, "مسافة الرحلة $tripDist > ${rules.maxTripDist} km")
        if (zones.isNotEmpty()) {
            val zr = ZoneChecker.checkZones(0.0, 0.0, 0.0, 0.0, zones)
            if (zr.blocked) return Pair(false, zr.reason)
        }
        return Pair(true, "")
    }

    /** Dump accessibility tree to logcat for debugging */
    private fun dumpTree(appName: String, node: AccessibilityNodeInfo?, depth: Int) {
        node ?: return
        if (depth > 20) { Log.w(TAG_DBG, "[$appName]${"  ".repeat(depth)}[…truncated]"); return }
        val indent = "  ".repeat(depth)
        val cls    = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val text   = node.text?.toString()?.take(80)?.replace('\n', '↵') ?: ""
        val desc   = node.contentDescription?.toString()?.take(60) ?: ""
        val id     = node.viewIdResourceName?.substringAfterLast('/') ?: ""
        val flags  = "${if (node.isClickable) "CLICK " else ""}${if (node.isEnabled) "EN " else ""}${if (node.isFocusable) "FOCUS" else ""}"
        Log.w(TAG_DBG, "[$appName]$indent[$cls] \"$text\" desc=\"$desc\" id=$id [$flags]")
        for (i in 0 until node.childCount) dumpTree(appName, node.getChild(i), depth + 1)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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
