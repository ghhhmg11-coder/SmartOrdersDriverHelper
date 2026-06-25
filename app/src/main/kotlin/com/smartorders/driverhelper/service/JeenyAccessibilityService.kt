package com.smartorders.driverhelper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.smartorders.driverhelper.AppState
import com.smartorders.driverhelper.PreferencesManager
import kotlinx.coroutines.*
import java.util.regex.Pattern

class JeenyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JeenyA11y"
        const val ACTION_TOGGLE_AUTO_ACCEPT = "com.smartorders.driverhelper.TOGGLE_AUTO_ACCEPT"
        const val ACTION_FORCE_ACCEPT = "com.smartorders.driverhelper.FORCE_ACCEPT"
        const val EXTRA_ENABLED = "enabled"

        val JEENY_PACKAGES = setOf("com.jeeny.driver", "com.jeeny.drivers")

        val ARABIC_MARKERS = listOf(
            "قبول العرض",
            "يبعد",
            "مشوار داخل المدينة",
            "استريح",
            "﷼",
            "ريال"
        )

        val PRICE_PATTERNS = listOf(
            Pattern.compile("""([\d.,٠-٩]+)\s*﷼"""),
            Pattern.compile("""﷼\s*([\d.,٠-٩]+)"""),
            Pattern.compile("""([\d.,٠-٩]+)\s*ريال"""),
            Pattern.compile("""ريال\s*([\d.,٠-٩]+)"""),
            Pattern.compile("""([\d.]+)\s*SAR""", Pattern.CASE_INSENSITIVE)
        )

        val MINUTES_PATTERNS = listOf(
            Pattern.compile("""يبعد\s*([\d٠-٩]+)\s*دقيق"""),
            Pattern.compile("""يبعد\s*([\d٠-٩]+)\s*دق"""),
            Pattern.compile("""([\d٠-٩]+)\s*دقيق"""),
            Pattern.compile("""([\d٠-٩]+)\s*min""", Pattern.CASE_INSENSITIVE)
        )

        val DISTANCE_PATTERNS = listOf(
            Pattern.compile("""يبعد\s*([\d.,٠-٩]+)\s*كم"""),
            Pattern.compile("""يبعد\s*([\d.,٠-٩]+)\s*km""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([\d.,٠-٩]+)\s*كم"""),
            Pattern.compile("""([\d.,٠-٩]+)\s*km""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""يبعد\s*([\d.,٠-٩]+)\s*م""")
        )

        private var instance: JeenyAccessibilityService? = null

        fun getServiceInstance(): JeenyAccessibilityService? = instance

        fun forceAccept() {
            instance?.performForceAccept()
        }
    }

    private var totalEvents = 0
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastProcessTime = 0L
    private val PROCESS_INTERVAL_MS = 300L

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE_AUTO_ACCEPT -> {
                    val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
                    PreferencesManager.setAutoAcceptEnabled(context, enabled)
                    AppState.setAutoAcceptEnabled(enabled)
                    val msg = if (enabled) "Auto-accept ENABLED ▶" else "Auto-accept DISABLED ⏸"
                    AppState.addEventLog(msg)
                    Log.i(TAG, msg)
                }
                ACTION_FORCE_ACCEPT -> {
                    performForceAccept()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppState.setServiceConnected(true)
        AppState.addEventLog("Accessibility service started ✅")
        Log.i(TAG, "Service connected")

        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_AUTO_ACCEPT)
            addAction(ACTION_FORCE_ACCEPT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        val enabled = PreferencesManager.isAutoAcceptEnabled(this)
        AppState.setAutoAcceptEnabled(enabled)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        totalEvents++

        val pkg = event.packageName?.toString() ?: ""
        val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
        val className = event.className?.toString() ?: ""

        AppState.updateDebug(
            totalEvents = totalEvents,
            lastPackage = pkg,
            lastEvent = eventType,
            lastClass = className
        )

        val now = System.currentTimeMillis()
        if (now - lastProcessTime < PROCESS_INTERVAL_MS) return
        lastProcessTime = now

        serviceScope.launch {
            scanAllWindows()
        }
    }

    private fun scanAllWindows() {
        val autoAcceptEnabled = PreferencesManager.isAutoAcceptEnabled(this)

        try {
            val allWindows: List<AccessibilityWindowInfo> = windows ?: emptyList()
            val allTexts = StringBuilder()
            var jeenyWindowFound = false

            for (window in allWindows) {
                try {
                    val root = window.root ?: continue
                    val texts = collectAllText(root)
                    if (texts.isNotEmpty()) {
                        allTexts.append(texts).append("\n---\n")
                    }
                    val winPkg = root.packageName?.toString() ?: ""
                    if (winPkg in JEENY_PACKAGES) {
                        jeenyWindowFound = true
                    }
                    root.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Window scan error: ${e.message}")
                }
            }

            val combinedText = allTexts.toString()
            Log.d(TAG, "All window text (${combinedText.length} chars): ${combinedText.take(500)}")

            AppState.updateDebug(rawVisibleText = combinedText.take(3000))

            val hasArabicMarker = ARABIC_MARKERS.any { marker -> combinedText.contains(marker) }
            val hasAcceptButton = combinedText.contains("قبول العرض")
            val isJeenyScreen = hasAcceptButton && hasArabicMarker

            AppState.updateDebug(jeenyDetected = isJeenyScreen)

            if (!isJeenyScreen) {
                AppState.updateDebug(
                    jeenyDetected = false,
                    acceptButtonFound = false,
                    detectionReason = "No Jeeny markers found in any window"
                )
                return
            }

            val price = extractPrice(combinedText)
            val minutes = extractMinutes(combinedText)
            val distanceKm = extractDistance(combinedText)

            val reason = buildString {
                append("Jeeny detected! price=$price SAR, minutes=$minutes, dist=${distanceKm}km")
            }
            AppState.updateDebug(detectionReason = reason)
            Log.i(TAG, reason)

            PreferencesManager.incrementDetected(this)
            AppState.incrementDetected()

            if (!autoAcceptEnabled) {
                AppState.updateDebug(acceptClickResult = "Auto-accept is OFF")
                return
            }

            val minPrice = PreferencesManager.getMinPrice(this)
            val maxPrice = PreferencesManager.getMaxPrice(this)
            val minMinutes = PreferencesManager.getMinMinutes(this)
            val maxMinutes = PreferencesManager.getMaxMinutes(this)
            val maxDistance = PreferencesManager.getMaxDistance(this)

            val priceOk = price == null || (price >= minPrice && price <= maxPrice)
            val minutesOk = minutes == null || (minutes >= minMinutes && minutes <= maxMinutes)
            val distanceOk = distanceKm == null || distanceKm <= maxDistance

            if (!priceOk || !minutesOk || !distanceOk) {
                val rejectReason = buildString {
                    if (!priceOk) append("price $price not in [$minPrice..$maxPrice] ")
                    if (!minutesOk) append("minutes $minutes not in [$minMinutes..$maxMinutes] ")
                    if (!distanceOk) append("dist $distanceKm > $maxDistance ")
                }
                AppState.updateDebug(
                    acceptButtonFound = false,
                    acceptClickResult = "Rejected: $rejectReason"
                )
                AppState.addEventLog("Rejected: $rejectReason")
                PreferencesManager.incrementRejected(this)
                AppState.incrementRejected()
                return
            }

            findAndClickAcceptButton(price ?: 0f)

        } catch (e: Exception) {
            Log.e(TAG, "scanAllWindows error: ${e.message}", e)
        }
    }

    private fun findAndClickAcceptButton(price: Float) {
        val allWindows: List<AccessibilityWindowInfo> = windows ?: emptyList()

        for (window in allWindows) {
            try {
                val root = window.root ?: continue
                val acceptNode = findAcceptNode(root)
                if (acceptNode != null) {
                    AppState.updateDebug(acceptButtonFound = true)
                    val clicked = tryClickNode(acceptNode)
                    if (clicked) {
                        val msg = "✅ Accepted! Price: $price SAR"
                        AppState.updateDebug(acceptClickResult = msg)
                        AppState.addEventLog(msg)
                        PreferencesManager.incrementAccepted(this, price)
                        AppState.incrementAccepted(price)
                        root.recycle()
                        return
                    } else {
                        val gestureResult = tryGestureFallback(acceptNode)
                        val msg = if (gestureResult) "✅ Accepted via gesture! Price: $price SAR"
                                  else "❌ Click failed (node + gesture)"
                        AppState.updateDebug(acceptClickResult = msg)
                        AppState.addEventLog(msg)
                        if (gestureResult) {
                            PreferencesManager.incrementAccepted(this, price)
                            AppState.incrementAccepted(price)
                        }
                        root.recycle()
                        return
                    }
                }
                root.recycle()
            } catch (e: Exception) {
                Log.w(TAG, "findAndClick error: ${e.message}")
            }
        }

        AppState.updateDebug(
            acceptButtonFound = false,
            acceptClickResult = "Accept node not found in any window"
        )
    }

    private fun findAcceptNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byText = root.findAccessibilityNodeInfosByText("قبول العرض")
        if (byText != null && byText.isNotEmpty()) {
            return byText[0]
        }
        return searchNodeRecursive(root, "قبول العرض")
    }

    private fun searchNodeRecursive(node: AccessibilityNodeInfo, targetText: String): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (text.contains(targetText) || contentDesc.contains(targetText)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchNodeRecursive(child, targetText)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun tryClickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            val grandParent = parent.parent
            parent.recycle()
            parent = grandParent
            depth++
        }
        return false
    }

    private fun tryGestureFallback(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val x = if (rect.width() > 0) rect.centerX().toFloat() else 400f
        val y = if (rect.height() > 0) rect.centerY().toFloat() else 1800f
        return performTap(x, y)
    }

    private fun performTap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        var result = false
        val latch = java.util.concurrent.CountDownLatch(1)
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                result = true
                latch.countDown()
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                result = false
                latch.countDown()
            }
        }, null)
        latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        return result
    }

    fun performForceAccept() {
        serviceScope.launch {
            AppState.addEventLog("🔴 FORCE ACCEPT TEST triggered")
            val allWindows: List<AccessibilityWindowInfo> = windows ?: emptyList()
            val allTexts = StringBuilder()
            for (window in allWindows) {
                try {
                    val root = window.root ?: continue
                    allTexts.append(collectAllText(root)).append("\n")
                    val acceptNode = findAcceptNode(root)
                    if (acceptNode != null) {
                        AppState.addEventLog("Force: accept node found, tapping...")
                        val clickOk = tryClickNode(acceptNode)
                        if (!clickOk) tryGestureFallback(acceptNode)
                        AppState.addEventLog("Force: tap done")
                        root.recycle()
                        return@launch
                    }
                    root.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Force accept error: ${e.message}")
                }
            }
            AppState.addEventLog("Force: accept button NOT found. Trying bottom gesture tap")
            performTap(540f, 1900f)
            AppState.addEventLog("Force: gesture tap at (540, 1900) done")
        }
    }

    private fun collectAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        collectTextRecursive(node, sb, 0)
        return sb.toString()
    }

    private fun collectTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 30) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) sb.append(text).append(" | ")
        else if (!desc.isNullOrEmpty()) sb.append(desc).append(" | ")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextRecursive(child, sb, depth + 1)
            child.recycle()
        }
    }

    private fun extractPrice(text: String): Float? {
        for (pattern in PRICE_PATTERNS) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val raw = matcher.group(1) ?: continue
                val normalized = normalizeArabicNumbers(raw).replace(",", ".")
                return normalized.toFloatOrNull()
            }
        }
        return null
    }

    private fun extractMinutes(text: String): Float? {
        for (pattern in MINUTES_PATTERNS) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val raw = matcher.group(1) ?: continue
                val normalized = normalizeArabicNumbers(raw)
                return normalized.toFloatOrNull()
            }
        }
        return null
    }

    private fun extractDistance(text: String): Float? {
        for (pattern in DISTANCE_PATTERNS) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val raw = matcher.group(1) ?: continue
                val normalized = normalizeArabicNumbers(raw).replace(",", ".")
                return normalized.toFloatOrNull()
            }
        }
        return null
    }

    private fun normalizeArabicNumbers(input: String): String {
        val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
        val sb = StringBuilder()
        for (c in input) {
            val arabicIdx = arabicDigits.indexOf(c)
            if (arabicIdx >= 0) sb.append(arabicIdx)
            else sb.append(c)
        }
        return sb.toString()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        AppState.setServiceConnected(false)
        AppState.addEventLog("Accessibility service destroyed ❌")
        serviceScope.cancel()
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
    }
}
