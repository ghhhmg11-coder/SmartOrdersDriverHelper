package com.smartorders.ultimate

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JeenyUltimateService : AccessibilityService() {

    companion object {
        private const val TAG = "JeenyUltimate"

        val JEENY_PACKAGES = setOf(
            "com.jeeny.driver",
            "com.jeeny.drivers"
        )

        var isAutoAcceptEnabled = false
        var minPriceThreshold = 6.0
        val blacklistedZones = mutableListOf<BlackZone>()

        var countDetected = 0
        var countAccepted = 0
        var countRejected = 0

        // Debug state — read from DashboardFragment
        var debugLastPackage = "---"
        var debugVisibleTexts = "---"
        var debugAcceptFound = false
        var debugClickResult = "---"

        var onStatsUpdated: (() -> Unit)? = null
    }

    data class BlackZone(
        val lat: Double,
        val lng: Double,
        val radius: Float,
        val label: String = ""
    )

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastProcessTime = 0L
    private val COOLDOWN_MS = 2500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("jeeny_prefs", MODE_PRIVATE)
        minPriceThreshold = prefs.getFloat("min_price", 6.0f).toDouble()
        isAutoAcceptEnabled = prefs.getBoolean("auto_accept", false)
        countDetected = prefs.getInt("count_detected", 0)
        countAccepted = prefs.getInt("count_accepted", 0)
        countRejected = prefs.getInt("count_rejected", 0)
        Log.i(TAG, "✅ AccessibilityService connected. autoAccept=$isAutoAcceptEnabled")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return

        // ── Log EVERY window-state change from any package ──────────────────
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "📱 WINDOW pkg=$pkg cls=${event.className}")
            debugLastPackage = pkg
            mainHandler.post { onStatsUpdated?.invoke() }
        }

        // ── Only continue if auto-accept is ON ───────────────────────────────
        if (!isAutoAcceptEnabled) return

        // ── Only process window events ───────────────────────────────────────
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // ── Must be a Jeeny package ──────────────────────────────────────────
        if (!JEENY_PACKAGES.contains(pkg)) return

        Log.i(TAG, "🚕 JEENY event: type=${event.eventType} pkg=$pkg")

        // ── Throttle ─────────────────────────────────────────────────────────
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < COOLDOWN_MS) return
        lastProcessTime = now

        val root = rootInActiveWindow ?: return
        processOrderScreen(root)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main processing
    // ─────────────────────────────────────────────────────────────────────────

    private fun processOrderScreen(root: AccessibilityNodeInfo) {
        // Collect and log the entire node tree
        val allTexts = mutableListOf<String>()
        dumpNodeTree(root, allTexts, 0)
        val textsJoined = allTexts.joinToString(" | ")
        debugVisibleTexts = if (textsJoined.length > 400) textsJoined.take(400) + "…" else textsJoined
        Log.i(TAG, "📋 ALL TEXTS: $textsJoined")

        // ── Strategy 1: "قبول العرض" ────────────────────────────────────────
        var target = findByText(root, "قبول العرض")
        // ── Strategy 2: "قبول" ──────────────────────────────────────────────
        if (target == null) target = findByText(root, "قبول")
        // ── Strategy 3: "Accept" (English) ──────────────────────────────────
        if (target == null) target = findByText(root, "Accept")
        // ── Strategy 4: contentDescription contains "قبول" ──────────────────
        if (target == null) target = findByContentDesc(root, "قبول")
        // ── Strategy 5: first large clickable node in bottom 40% of screen ──
        if (target == null) target = findBottomClickable(root)

        if (target != null) {
            Log.i(TAG, "✅ Accept node found: text='${target.text}' desc='${target.contentDescription}'")
            debugAcceptFound = true
            countDetected++

            val clicked = performClick(target)
            if (clicked) {
                debugClickResult = "SUCCESS"
                countAccepted++
                Log.i(TAG, "✅ Click SUCCESS")
            } else {
                // Strategy 6: coordinate tap at bottom-centre
                Log.w(TAG, "⚠️ Node click failed — trying coordinate tap")
                tapBottomCentre()
                debugClickResult = "COORD TAP SENT"
                countAccepted++
            }
        } else {
            // Strategy 6 (last resort): coordinate tap
            Log.w(TAG, "❌ No accept button found — sending coord tap as fallback")
            debugAcceptFound = false
            debugClickResult = "COORD TAP (no btn)"
            countDetected++
            tapBottomCentre()
            countAccepted++
        }

        Log.i(TAG, "📊 detected=$countDetected accepted=$countAccepted rejected=$countRejected")
        saveStats()
        mainHandler.post { onStatsUpdated?.invoke() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Node helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun dumpNodeTree(node: AccessibilityNodeInfo?, texts: MutableList<String>, depth: Int) {
        if (node == null || depth > 25) return
        val txt = node.text?.toString()
        val dsc = node.contentDescription?.toString()
        if (!txt.isNullOrBlank()) texts.add(txt)
        if (!dsc.isNullOrBlank() && dsc != txt) texts.add("[d:$dsc]")
        val indent = "  ".repeat(depth)
        Log.v(TAG, "${indent}• cls=${node.className} txt='$txt' dsc='$dsc' clickable=${node.isClickable}")
        for (i in 0 until node.childCount) dumpNodeTree(node.getChild(i), texts, depth + 1)
    }

    private fun findByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val results = root.findAccessibilityNodeInfosByText(text)
        if (results.isNotEmpty()) {
            Log.d(TAG, "🔍 Found '${text}' → ${results.size} node(s)")
            return results[0]
        }
        return null
    }

    private fun findByContentDesc(root: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? =
        findByDescRecursive(root, keyword)

    private fun findByDescRecursive(node: AccessibilityNodeInfo?, kw: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains(kw, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val r = findByDescRecursive(node.getChild(i), kw)
            if (r != null) return r
        }
        return null
    }

    /** Find the first clickable node whose top edge is in the bottom 40 % of screen. */
    private fun findBottomClickable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenH = resources.displayMetrics.heightPixels
        val threshold = (screenH * 0.60).toInt()
        return findBottomClickableRecursive(root, threshold, screenH)
    }

    private fun findBottomClickableRecursive(
        node: AccessibilityNodeInfo?, threshold: Int, screenH: Int
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isClickable) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.top >= threshold && r.bottom <= screenH && r.width() > 80) {
                Log.d(TAG, "🔍 Bottom-clickable found at rect=$r text='${node.text}'")
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val res = findBottomClickableRecursive(node.getChild(i), threshold, screenH)
            if (res != null) return res
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Click helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        // Try the node itself
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // Walk up to clickable parent (max 6 levels)
        var p = node.parent
        var d = 0
        while (p != null && d < 6) {
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            p = p.parent
            d++
        }
        return false
    }

    /** Tap at 85 % of screen height, horizontal centre — targets Jeeny's purple Accept button. */
    private fun tapBottomCentre() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y = dm.heightPixels * 0.85f
        Log.i(TAG, "🖱️ Tapping coords ($x, $y)")
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 100L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Persistence
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveStats() {
        prefs.edit()
            .putInt("count_detected", countDetected)
            .putInt("count_accepted", countAccepted)
            .putInt("count_rejected", countRejected)
            .apply()
    }

    private fun isLocationSafe(loc: Location?): Boolean {
        if (loc == null) return true
        for (zone in blacklistedZones) {
            val zl = Location("").apply { latitude = zone.lat; longitude = zone.lng }
            if (loc.distanceTo(zl) <= zone.radius) return false
        }
        return true
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Service interrupted")
    }
}
