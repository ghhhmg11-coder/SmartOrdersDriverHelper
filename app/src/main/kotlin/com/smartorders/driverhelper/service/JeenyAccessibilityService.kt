package com.smartorders.driverhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.LogType
import com.smartorders.driverhelper.data.PrefsManager
import java.util.regex.Pattern

class JeenyAccessibilityService : AccessibilityService() {

    private val TAG = "JeenyService"
    private lateinit var prefs: PrefsManager

    private var lastAcceptTime = 0L
    private val ACCEPT_COOLDOWN_MS = 3000L

    companion object {
        val JEENY_PACKAGES = setOf("com.jeeny.driver", "com.jeeny.drivers")
        val JEENY_MARKERS = listOf("قبول العرض", "يبعد", "مشوار داخل المدينة", "استريح", "﷼")
        const val ACCEPT_PARTIAL = "قبول"
        const val ACCEPT_FULL = "قبول العرض"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PrefsManager(applicationContext)
        AppState.isServiceConnected.value = true
        AppState.addLog("✅ Accessibility service started", LogType.SUCCESS)
        Log.i(TAG, "Service connected")
    }

    override fun onInterrupt() {
        AppState.isServiceConnected.value = false
        AppState.addLog("❌ Accessibility service destroyed", LogType.ERROR)
        Log.w(TAG, "Service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AppState.isServiceConnected.value = false
        AppState.addLog("❌ Service disconnected", LogType.ERROR)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: "unknown"
        val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
        val className = event.className?.toString() ?: "-"

        AppState.totalEvents.value++
        AppState.lastPackage.value = pkg
        AppState.lastEvent.value = eventType
        AppState.lastClass.value = className

        Log.d(TAG, "Event pkg=$pkg type=$eventType class=$className")

        if (!AppState.isAutoAcceptEnabled.value) return

        // ── Strategy A: scan every open window (catches Jeeny even when not foreground) ──
        scanAllWindows()

        // ── Strategy B: also check rootInActiveWindow directly (user-provided approach) ──
        checkActiveWindowForAccept()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strategy A — iterate every AccessibilityWindowInfo
    // ─────────────────────────────────────────────────────────────────────────
    private fun scanAllWindows() {
        val allWindows: List<AccessibilityWindowInfo> = try {
            windows ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "windows error: ${e.message}")
            emptyList()
        }

        val rawSb = StringBuilder()
        val pkgsSeen = mutableSetOf<String>()
        var jeenyRoot: AccessibilityNodeInfo? = null
        var jeenyPkg = ""

        for (window in allWindows) {
            val root = try { window.root } catch (e: Exception) { null } ?: continue
            val winPkg = root.packageName?.toString() ?: "unknown"
            pkgsSeen.add(winPkg)

            val texts = collectAllText(root)
            if (texts.isNotEmpty()) rawSb.append("[$winPkg]: ${texts.joinToString(" | ")}\n")

            val isJeenyPkg = winPkg in JEENY_PACKAGES
            val hasMarkers = texts.any { t -> JEENY_MARKERS.any { m -> t.contains(m) } }

            if (isJeenyPkg || hasMarkers) {
                jeenyRoot = root
                jeenyPkg = winPkg
            }
        }

        AppState.rawWindowsText.value = rawSb.toString()
        Log.d(TAG, "Packages seen: $pkgsSeen")

        if (jeenyRoot != null) {
            processJeenyRoot(jeenyRoot, jeenyPkg)
        } else {
            if (AppState.isJeenyDetected.value) {
                AppState.isJeenyDetected.value = false
                AppState.isAcceptButtonFound.value = false
                AppState.detectionReason.value = "Jeeny screen gone"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Strategy B — rootInActiveWindow + findAccessibilityNodeInfosByText (user's approach)
    // ─────────────────────────────────────────────────────────────────────────
    private fun checkActiveWindowForAccept() {
        val rootNode = rootInActiveWindow ?: return

        // Use Android's built-in text search (partial match) — finds "قبول العرض"
        val acceptNodes = try {
            rootNode.findAccessibilityNodeInfosByText(ACCEPT_PARTIAL) ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "findByText error: ${e.message}")
            emptyList()
        }

        if (acceptNodes.isEmpty()) return

        // Confirm it's a real Jeeny screen by checking other markers
        val allTexts = collectAllText(rootNode).joinToString(" ")
        val hasJeenyMarkers = JEENY_MARKERS.count { allTexts.contains(it) } >= 2
        if (!hasJeenyMarkers) return

        AppState.isJeenyDetected.value = true
        AppState.isAcceptButtonFound.value = true

        val now = System.currentTimeMillis()
        if (now - lastAcceptTime < ACCEPT_COOLDOWN_MS) return
        lastAcceptTime = now

        val price = parsePrice(allTexts)
        val minutes = parseMinutes(allTexts)
        val distance = parseDistance(allTexts)

        AppState.detectionReason.value = "activeWindow | price=$price min=$minutes dist=$distance"

        if (!rulesPass(price, minutes, distance)) return

        // Click via user's approach: node → parent walk → gesture
        val acceptButton = acceptNodes[0]
        Log.i(TAG, "Strategy B: found '${acceptButton.text}' clickable=${acceptButton.isClickable}")

        if (acceptButton.isClickable) {
            if (acceptButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onAccepted(price, "Strategy B direct click")
                return
            }
        }

        var parent = acceptButton.parent
        while (parent != null) {
            if (parent.isClickable) {
                if (parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    onAccepted(price, "Strategy B parent click")
                    return
                }
            }
            parent = parent.parent
        }

        // Final fallback: gesture tap
        performGestureTap { success ->
            if (success) onAccepted(price, "Strategy B gesture")
            else AppState.addLog("❌ All click strategies failed", LogType.ERROR)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Process a Jeeny root found via Strategy A
    // ─────────────────────────────────────────────────────────────────────────
    private fun processJeenyRoot(root: AccessibilityNodeInfo, pkg: String) {
        val allTexts = collectAllText(root)
        val fullText = allTexts.joinToString(" ")

        if (!fullText.contains(ACCEPT_FULL)) {
            AppState.isJeenyDetected.value = false
            AppState.detectionReason.value = "Jeeny window — no accept button text"
            return
        }

        AppState.isJeenyDetected.value = true
        AppState.detectedTrips.value++

        val price = parsePrice(fullText)
        val minutes = parseMinutes(fullText)
        val distance = parseDistance(fullText)

        AppState.detectionReason.value = "allWindows | pkg=$pkg | price=$price min=$minutes dist=$distance"
        Log.i(TAG, "Jeeny detected: price=$price min=$minutes dist=$distance")

        if (!rulesPass(price, minutes, distance)) return

        val now = System.currentTimeMillis()
        if (now - lastAcceptTime < ACCEPT_COOLDOWN_MS) return
        lastAcceptTime = now

        clickAcceptInRoot(root, price)
    }

    private fun clickAcceptInRoot(root: AccessibilityNodeInfo, price: Float?) {
        // Try Android's built-in findByText first (partial, more reliable)
        val byApi = try {
            root.findAccessibilityNodeInfosByText(ACCEPT_PARTIAL) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        AppState.isAcceptButtonFound.value = byApi.isNotEmpty()

        if (byApi.isNotEmpty()) {
            val node = byApi[0]
            Log.i(TAG, "Strategy A findByText: '${node.text}' clickable=${node.isClickable}")

            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onAccepted(price, "A direct"); return
            }

            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    onAccepted(price, "A parent"); return
                }
                parent = parent.parent
            }
        }

        // Manual recursive search as backup
        val manual = findNodesByText(root, ACCEPT_FULL)
        if (manual.isNotEmpty()) {
            val node = manual[0]
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onAccepted(price, "A manual"); return
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    onAccepted(price, "A manual parent"); return
                }
                parent = parent.parent
            }
        }

        // Gesture fallback
        performGestureTap { success ->
            if (success) onAccepted(price, "A gesture")
            else { AppState.acceptClickResult.value = "Gesture failed"; AppState.addLog("❌ Gesture failed", LogType.ERROR) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Force-accept tap (called from floating overlay FORCE button)
    // ─────────────────────────────────────────────────────────────────────────
    fun performGestureTap(x: Float = -1f, y: Float = -1f, onDone: ((Boolean) -> Unit)? = null) {
        val metrics = resources.displayMetrics
        val tapX = if (x > 0) x else metrics.widthPixels / 2f
        val tapY = if (y > 0) y else metrics.heightPixels * 0.88f

        val path = Path().apply { moveTo(tapX, tapY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        Log.i(TAG, "Gesture tap at ($tapX, $tapY)")

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) {
                AppState.acceptClickResult.value = "Gesture OK (${tapX.toInt()},${tapY.toInt()})"
                onDone?.invoke(true)
            }
            override fun onCancelled(g: GestureDescription) { onDone?.invoke(false) }
        }, null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun rulesPass(price: Float?, minutes: Float?, distance: Float?): Boolean {
        val minPrice = prefs.minPrice; val maxPrice = prefs.maxPrice
        val minMin = prefs.minMinutes; val maxMin = prefs.maxMinutes
        val maxDist = prefs.maxDistance

        if (price != null && (price < minPrice || price > maxPrice)) {
            AppState.rejectedTrips.value++
            AppState.acceptClickResult.value = "Rejected: price $price ∉ [$minPrice,$maxPrice]"
            AppState.addLog("⛔ Rejected price $price (rule $minPrice-$maxPrice)", LogType.WARNING)
            return false
        }
        if (minutes != null && (minutes < minMin || minutes > maxMin)) {
            AppState.rejectedTrips.value++
            AppState.acceptClickResult.value = "Rejected: $minutes min ∉ [$minMin,$maxMin]"
            AppState.addLog("⛔ Rejected minutes $minutes (rule $minMin-$maxMin)", LogType.WARNING)
            return false
        }
        if (distance != null && distance > maxDist) {
            AppState.rejectedTrips.value++
            AppState.acceptClickResult.value = "Rejected: dist $distance > $maxDist"
            AppState.addLog("⛔ Rejected distance $distance > $maxDist", LogType.WARNING)
            return false
        }
        return true
    }

    private fun onAccepted(price: Float?, strategy: String) {
        AppState.acceptedTrips.value++
        if (price != null) AppState.totalSAR.value += price
        AppState.acceptClickResult.value = "✅ ACCEPTED [$strategy] price=$price"
        AppState.addLog("✅ Trip accepted! price=$price SAR [$strategy]", LogType.SUCCESS)
        Log.i(TAG, "Accepted via $strategy, price=$price")
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, out: MutableList<String> = mutableListOf()): List<String> {
        if (node == null) return out
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != node.text?.toString() }?.let { out.add(it) }
        for (i in 0 until node.childCount) collectAllText(node.getChild(i), out)
        return out
    }

    private fun findNodesByText(root: AccessibilityNodeInfo?, target: String, out: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (root == null) return out
        if ((root.text?.toString() ?: "").contains(target) || (root.contentDescription?.toString() ?: "").contains(target)) out.add(root)
        for (i in 0 until root.childCount) findNodesByText(root.getChild(i), target, out)
        return out
    }

    private fun parsePrice(text: String): Float? {
        val m = Regex("""([\d.]+)\s*﷼|﷼\s*([\d.]+)""").find(text) ?: return null
        return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toFloatOrNull()
    }

    private fun parseMinutes(text: String): Float? {
        val m = Regex("""يبعد\s+([\d٠-٩]+(?:\.\d+)?)\s+دقيق""").find(text) ?: return null
        return convertArabicNumerals(m.groupValues[1]).toFloatOrNull()
    }

    private fun parseDistance(text: String): Float? {
        Regex("""يبعد\s+([\d٠-٩]+(?:[.,][\d٠-٩]+)?)\s+كم""").find(text)?.let {
            return convertArabicNumerals(it.groupValues[1]).toFloatOrNull()
        }
        Regex("""يبعد\s+([\d٠-٩]+(?:[.,][\d٠-٩]+)?)\s+م\b""").find(text)?.let {
            return convertArabicNumerals(it.groupValues[1]).toFloatOrNull()?.div(1000f)
        }
        return null
    }

    private fun convertArabicNumerals(s: String) = s.map { c ->
        if (c in '٠'..'٩') '0' + (c - '٠') else c
    }.joinToString("")
}
