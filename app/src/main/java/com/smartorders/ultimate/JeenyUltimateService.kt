package com.smartorders.ultimate

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.location.Location
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JeenyUltimateService : AccessibilityService() {

    companion object {
        var isAutoAcceptEnabled = false
        var minPriceThreshold = 6.0
        val blacklistedZones = mutableListOf<BlackZone>()

        var countDetected = 0
        var countAccepted = 0
        var countRejected = 0

        // Listeners to notify UI of stats changes
        var onStatsUpdated: (() -> Unit)? = null
    }

    data class BlackZone(
        val lat: Double,
        val lng: Double,
        val radius: Float,
        val label: String = ""
    )

    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("jeeny_prefs", MODE_PRIVATE)
        minPriceThreshold = prefs.getFloat("min_price", 6.0f).toDouble()
        isAutoAcceptEnabled = prefs.getBoolean("auto_accept", false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isAutoAcceptEnabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val rootNode = rootInActiveWindow ?: return
        processOrderScreen(rootNode)
    }

    private fun processOrderScreen(rootNode: AccessibilityNodeInfo) {
        val acceptNodes = rootNode.findAccessibilityNodeInfosByText("قبول")
        val acceptEnNodes = rootNode.findAccessibilityNodeInfosByText("Accept")
        val targetNodes = if (acceptNodes.isNotEmpty()) acceptNodes else acceptEnNodes

        if (targetNodes.isEmpty()) return

        countDetected++
        val acceptButton = targetNodes[0]
        val currentOrderLocation = extractLocationFromScreen(rootNode)

        if (isPriceAboveThreshold(rootNode) && isLocationSafe(currentOrderLocation)) {
            executeAutoClick(acceptButton)
            countAccepted++
        } else {
            countRejected++
        }

        prefs.edit()
            .putInt("count_detected", countDetected)
            .putInt("count_accepted", countAccepted)
            .putInt("count_rejected", countRejected)
            .apply()

        onStatsUpdated?.invoke()
    }

    private fun isPriceAboveThreshold(root: AccessibilityNodeInfo): Boolean {
        val priceKeywords = listOf("KD", "ر.ك", "SAR", "ر.س", "AED")
        for (keyword in priceKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                val text = node.text?.toString() ?: continue
                val price = extractNumber(text)
                if (price != null && price < minPriceThreshold) return false
            }
        }
        return true
    }

    private fun extractNumber(text: String): Double? {
        val regex = Regex("[0-9]+\\.?[0-9]*")
        val match = regex.find(text) ?: return null
        return match.value.toDoubleOrNull()
    }

    private fun isLocationSafe(orderLoc: Location?): Boolean {
        if (orderLoc == null) return true
        for (zone in blacklistedZones) {
            val zoneLoc = Location("").apply {
                latitude = zone.lat
                longitude = zone.lng
            }
            if (orderLoc.distanceTo(zoneLoc) <= zone.radius) return false
        }
        return true
    }

    private fun extractLocationFromScreen(root: AccessibilityNodeInfo): Location? {
        return null
    }

    private fun executeAutoClick(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            var parentNode = node.parent
            while (parentNode != null) {
                if (parentNode.isClickable) {
                    parentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    break
                }
                parentNode = parentNode.parent
            }
        }
    }

    override fun onInterrupt() {}
}
