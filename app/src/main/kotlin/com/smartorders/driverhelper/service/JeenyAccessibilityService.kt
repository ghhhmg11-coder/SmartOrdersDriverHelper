package com.smartorders.driverhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.smartorders.driverhelper.data.AppState
import com.smartorders.driverhelper.data.LogType
import com.smartorders.driverhelper.data.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class JeenyAccessibilityService : AccessibilityService() {

    private val TAG = "JeenyService"
    private lateinit var prefs: PrefsManager

    // Cooldown to avoid spam-clicking
    private var lastAcceptTime = 0L
    private val ACCEPT_COOLDOWN_MS = 3000L

    companion object {
        val JEENY_PACKAGES = setOf("com.jeeny.driver", "com.jeeny.drivers")
        // Arabic markers present on Jeeny trip request screen
        val JEENY_MARKERS = listOf("قبول العرض", "يبعد", "مشوار داخل المدينة", "استريح", "﷼")
        const val ACCEPT_BUTTON_TEXT = "قبول العرض"
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
        Log.w(TAG, "Service unbound")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: "unknown"
        val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
        val className = event.className?.toString() ?: "-"

        // Update debug state for every event
        AppState.totalEvents.value++
        AppState.lastPackage.value = pkg
        AppState.lastEvent.value = eventType
        AppState.lastClass.value = className

        Log.d(TAG, "Event: pkg=$pkg type=$eventType class=$className")

        // Read ALL windows regardless of the event's source package
        scanAllWindows()
    }

    private fun scanAllWindows() {
        if (!AppState.isAutoAcceptEnabled.value) return

        val allWindows: List<AccessibilityWindowInfo> = try {
            windows ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get windows: ${e.message}")
            emptyList()
        }

        val allTexts = StringBuilder()
        val packagesSeen = mutableSetOf<String>()
        var jeenyRootNode: AccessibilityNodeInfo? = null
        var jeenyWindowPkg = ""

        for (window in allWindows) {
            val root = try { window.root } catch (e: Exception) { null } ?: continue
            val winPkg = root.packageName?.toString() ?: "unknown"
            packagesSeen.add(winPkg)

            val texts = collectAllText(root)
            if (texts.isNotEmpty()) {
                allTexts.append("[$winPkg]: ${texts.joinToString(" | ")}\n")
            }

            // Check if this window belongs to Jeeny (by package OR by text markers)
            val isJeenyPkg = winPkg in JEENY_PACKAGES
            val hasJeenyMarkers = texts.any { text ->
                JEENY_MARKERS.any { marker -> text.contains(marker) }
            }

            if (isJeenyPkg || hasJeenyMarkers) {
                jeenyRootNode = root
                jeenyWindowPkg = winPkg
            }
        }

        // Update debug raw text
        AppState.rawWindowsText.value = allTexts.toString()
        Log.d(TAG, "Packages seen: $packagesSeen")

        if (jeenyRootNode != null) {
            processJeenyWindow(jeenyRootNode, jeenyWindowPkg)
        } else {
            if (AppState.isJeenyDetected.value) {
                // Was detected before, now gone — reset
                AppState.isJeenyDetected.value = false
                AppState.isAcceptButtonFound.value = false
                AppState.detectionReason.value = "Jeeny screen gone"
            }
        }
    }

    private fun processJeenyWindow(root: AccessibilityNodeInfo, pkg: String) {
        val allTexts = collectAllText(root)
        val fullText = allTexts.joinToString(" ")

        // Must have accept button text to be a trip request screen
        val hasAcceptButton = fullText.contains(ACCEPT_BUTTON_TEXT)
        if (!hasAcceptButton) {
            AppState.isJeenyDetected.value = false
            AppState.detectionReason.value = "Jeeny window found but no accept button text"
            return
        }

        AppState.isJeenyDetected.value = true
        AppState.detectedTrips.value++

        // Parse trip data
        val price = parsePrice(fullText)
        val minutes = parseMinutes(fullText)
        val distance = parseDistance(fullText)

        val reason = buildString {
            append("pkg=$pkg | price=$price | min=$minutes | dist=$distance km")
        }
        AppState.detectionReason.value = reason
        Log.i(TAG, "Jeeny trip detected: $reason")

        // Apply rules
        val minPrice = prefs.minPrice
        val maxPrice = prefs.maxPrice
        val minMinutes = prefs.minMinutes
        val maxMinutes = prefs.maxMinutes
        val maxDistance = prefs.maxDistance

        val priceOk = price == null || (price >= minPrice && price <= maxPrice)
        val minutesOk = minutes == null || (minutes >= minMinutes && minutes <= maxMinutes)
        val distanceOk = distance == null || distance <= maxDistance

        if (!priceOk) {
            AppState.rejectedTrips.value++
            AppState.acceptClickResult.value = "Rejected: price $price outside [$minPrice,$maxPrice]"
            AppState.addLog("⛔ Rejected: price $price (rule: $minPrice-$maxPrice)", LogType.WARNING)
            return
        }
        if (!minutesOk) {
            AppState.rejectedTrips.value++
            AppState.acceptClickResult.value = "Rejected: minutes $minutes outside [$minMinutes,$maxMinutes]"
            AppState.addLog("⛔ Rejected: minutes $minutes (rule: $minMinutes-$maxMinutes)", LogType.WARNING)
            return
        }
        if (!distanceOk) {
            AppState.rejectedTrips.value++
            AppState.acceptClickResult.value = "Rejected: distance $distance > $maxDistance"
            AppState.addLog("⛔ Rejected: distance $distance > $maxDistance", LogType.WARNING)
            return
        }

        // Rules pass — attempt to click accept button
        val now = System.currentTimeMillis()
        if (now - lastAcceptTime < ACCEPT_COOLDOWN_MS) return
        lastAcceptTime = now

        clickAcceptButton(root, price)
    }

    private fun clickAcceptButton(root: AccessibilityNodeInfo, price: Float?) {
        // Strategy 1: find node by exact text
        val acceptNodes = findNodesByText(root, ACCEPT_BUTTON_TEXT)
        AppState.isAcceptButtonFound.value = acceptNodes.isNotEmpty()

        if (acceptNodes.isNotEmpty()) {
            val node = acceptNodes.first()
            Log.i(TAG, "Found accept node: clickable=${node.isClickable} text=${node.text}")

            // Try clicking the node directly
            if (tryClickNode(node)) {
                onAccepted(price)
                return
            }

            // Try parent nodes up the tree
            var parent = node.parent
            var depth = 0
            while (parent != null && depth < 5) {
                if (tryClickNode(parent)) {
                    onAccepted(price)
                    return
                }
                parent = parent.parent
                depth++
            }
        }

        // Strategy 2: GestureDescription fallback — tap bottom-center of screen
        Log.w(TAG, "Node click failed, trying gesture fallback")
        performGestureTap(onDone = { success ->
            if (success) onAccepted(price)
            else {
                AppState.acceptClickResult.value = "Gesture failed"
                AppState.addLog("❌ Accept gesture failed", LogType.ERROR)
            }
        })
    }

    private fun tryClickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        return try {
            if (node.isClickable) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Click result=$result on node '${node.text}'")
                result
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Click exception: ${e.message}")
            false
        }
    }

    fun performGestureTap(x: Float = -1f, y: Float = -1f, onDone: ((Boolean) -> Unit)? = null) {
        // Default position: bottom-center of screen (where Jeeny's accept button is)
        val metrics = resources.displayMetrics
        val tapX = if (x > 0) x else metrics.widthPixels / 2f
        val tapY = if (y > 0) y else metrics.heightPixels * 0.88f

        val path = Path()
        path.moveTo(tapX, tapY)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        Log.i(TAG, "Gesture tap at ($tapX, $tapY)")

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                Log.i(TAG, "Gesture completed")
                AppState.acceptClickResult.value = "Gesture OK at (${tapX.toInt()},${tapY.toInt()})"
                onDone?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                Log.w(TAG, "Gesture cancelled")
                onDone?.invoke(false)
            }
        }, null)
    }

    private fun onAccepted(price: Float?) {
        AppState.acceptedTrips.value++
        if (price != null) AppState.totalSAR.value += price
        AppState.acceptClickResult.value = "✅ ACCEPTED price=$price"
        AppState.addLog("✅ Trip accepted! price=$price SAR", LogType.SUCCESS)
        Log.i(TAG, "Trip accepted, price=$price")
    }

    // Recursively collect all non-empty text from the accessibility tree
    private fun collectAllText(node: AccessibilityNodeInfo?, out: MutableList<String> = mutableListOf()): List<String> {
        if (node == null) return out
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) out.add(text)
        if (!desc.isNullOrEmpty() && desc != text) out.add(desc)
        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), out)
        }
        return out
    }

    // Find all nodes whose text contains the target string
    private fun findNodesByText(root: AccessibilityNodeInfo?, target: String, out: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (root == null) return out
        val text = root.text?.toString() ?: ""
        val desc = root.contentDescription?.toString() ?: ""
        if (text.contains(target) || desc.contains(target)) out.add(root)
        for (i in 0 until root.childCount) {
            findNodesByText(root.getChild(i), target, out)
        }
        return out
    }

    // Parse SAR price from Arabic-formatted strings like "6.39 ﷼" or "﷼ 6.39"
    private fun parsePrice(text: String): Float? {
        // Match number adjacent to ﷼ or SAR
        val pattern = Pattern.compile("""[\d٠-٩]+[.,]?[\d٠-٩]*""")
        val nearRial = Regex("""([\d.]+)\s*﷼|﷼\s*([\d.]+)""")
        val m = nearRial.find(text)
        if (m != null) {
            val v = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toFloatOrNull()
            if (v != null) return v
        }
        return null
    }

    // Parse pickup minutes from "يبعد N دقائق"
    private fun parseMinutes(text: String): Float? {
        val regex = Regex("""يبعد\s+([\d٠-٩]+(?:\.\d+)?)\s+دقيق""")
        val m = regex.find(text)
        if (m != null) {
            return convertArabicNumerals(m.groupValues[1]).toFloatOrNull()
        }
        return null
    }

    // Parse pickup distance from "يبعد N.N كم" or "يبعد N م"
    private fun parseDistance(text: String): Float? {
        // km
        val kmRegex = Regex("""يبعد\s+([\d٠-٩]+(?:[.,][\d٠-٩]+)?)\s+كم""")
        val mRegex  = Regex("""يبعد\s+([\d٠-٩]+(?:[.,][\d٠-٩]+)?)\s+م\b""")
        val kmMatch = kmRegex.find(text)
        if (kmMatch != null) {
            return convertArabicNumerals(kmMatch.groupValues[1]).toFloatOrNull()
        }
        val mMatch = mRegex.find(text)
        if (mMatch != null) {
            val meters = convertArabicNumerals(mMatch.groupValues[1]).toFloatOrNull()
            return if (meters != null) meters / 1000f else null
        }
        return null
    }

    // Convert Arabic-Indic numerals (٠١٢٣٤٥٦٧٨٩) to Western numerals
    private fun convertArabicNumerals(s: String): String {
        return s.map { c ->
            if (c in '٠'..'٩') ('0' + (c - '٠')) else c
        }.joinToString("")
    }
}
