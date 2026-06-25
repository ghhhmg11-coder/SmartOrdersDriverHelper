package com.smartorders.driverhelper.utils

import com.smartorders.driverhelper.model.DetectedTrip
import android.util.Log

object TripParser {

    private const val TAG = "TripParser"

    // ── Accept button texts ───────────────────────────────────────────────────
    private val ACCEPT_BUTTON_TEXTS = setOf(
        "قبول العرض",   // Jeeny — exact text from screenshot
        "قبول الطلب",
        "قبول",
        "اقبل",
        "Accept",
        "ACCEPT",
        "Accept Offer",
        "Accept Trip",
        "قبل",
        "موافق"
    )

    // ── Jeeny-specific markers visible in screenshot ──────────────────────────
    // "مشوار داخل المدينة" = trip type label (pill at top)
    // "يبعد" = "away" (pickup ETA prefix)
    // "دقائق/دقيقة" = minutes
    // "ستريح" = "you'll earn" (profit label in purple box)
    // "قبول العرض" = accept button
    private val JEENY_MARKERS  = listOf("قبول العرض", "ستريح", "مشوار", "يبعد")
    private val UBER_MARKERS   = listOf("Accept", "Accept Trip", "New Trip", "Pickup")
    private val CAREEM_MARKERS = listOf("Accept", "New Booking", "قبول")
    private val BOLT_MARKERS   = listOf("Accept", "New order", "قبول")

    // ── Price regexes ─────────────────────────────────────────────────────────
    // Handles both "6.67 ﷼" and "﷼ 6.67" (Jeeny shows ﷼ BEFORE the number)
    private val priceAfterCurrencyRegex = Regex(
        """(?:﷼|ريال|SAR|SR|ر\.س)\s*(\d{1,4}(?:[.,]\d{1,2})?)"""
    )
    private val priceBeforeCurrencyRegex = Regex(
        """(\d{1,4}(?:[.,]\d{1,2})?)\s*(?:﷼|ريال|SAR|SR|ر\.س)"""
    )
    // Bare decimal — e.g. standalone "6.67" not near دقيقة/km
    private val barePriceRegex = Regex(
        """(?<![.\d])(\d{1,4}\.\d{1,2})(?!\s*(?:دقيقة|دقائق|min|km|كم|%|:))"""
    )
    // Numbers marked as minutes — exclude from price detection
    private val minutesRegex = Regex("""(\d+)\s*(?:دقيقة|دقائق|min)""", RegexOption.IGNORE_CASE)

    // ── Distance regexes ──────────────────────────────────────────────────────
    private val pickupDistRegex = Regex(
        """(?:pickup|استلام|التقاط|الوصول إليك)[^\d]*(\d+(?:\.\d+)?)\s*(?:km|كم|k)""",
        RegexOption.IGNORE_CASE
    )
    private val tripDistRegex = Regex(
        """(?:trip|رحلة|المسافة|distance)[^\d]*(\d+(?:\.\d+)?)\s*(?:km|كم|k)""",
        RegexOption.IGNORE_CASE
    )
    private val generalDistRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:km|كم)""", RegexOption.IGNORE_CASE)

    // ─────────────────────────────────────────────────────────────────────────

    fun isRequestScreen(sourceApp: String, rawText: String): Boolean {
        if (rawText.isBlank()) return false
        val markers = when (sourceApp) {
            "jeeny"  -> JEENY_MARKERS
            "uber"   -> UBER_MARKERS
            "careem" -> CAREEM_MARKERS
            "bolt"   -> BOLT_MARKERS
            else     -> JEENY_MARKERS + UBER_MARKERS
        }
        val matchedMarker = markers.firstOrNull { rawText.contains(it) }
        return if (matchedMarker != null) {
            Log.i(TAG, "[$sourceApp] Request screen detected via marker: '$matchedMarker'")
            true
        } else {
            Log.v(TAG, "[$sourceApp] No request marker found. Checked: $markers")
            false
        }
    }

    fun parse(sourceApp: String, rawText: String): DetectedTrip? {
        if (rawText.isBlank()) return null
        if (!isRequestScreen(sourceApp, rawText)) return null

        val price = extractPrice(sourceApp, rawText)
        // If price is 0 we still return a trip so it gets counted as "detected"
        // Rules will decide whether to accept or log it

        val (pickupDist, tripDist) = extractDistances(rawText)
        val (pickupLoc, destLoc)   = extractLocations(rawText)

        Log.i(TAG, "[$sourceApp] Trip parsed — price=$price SAR, pickup=${pickupDist}km, trip=${tripDist}km")
        return DetectedTrip(
            sourceApp           = sourceApp,
            rawText             = rawText,
            price               = price,
            pickupDistance      = pickupDist,
            tripDistance        = tripDist,
            pickupLocation      = pickupLoc,
            destinationLocation = destLoc
        )
    }

    private fun extractPrice(sourceApp: String, text: String): Double {
        // 1. ﷼/SAR BEFORE the number  (e.g., "﷼ 6.67" — Jeeny layout)
        priceAfterCurrencyRegex.find(text)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()
            ?.also { Log.d(TAG, "[$sourceApp] Price via currency-before: $it"); return it }

        // 2. Number BEFORE currency symbol (e.g., "6.67 SAR")
        priceBeforeCurrencyRegex.find(text)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()
            ?.also { Log.d(TAG, "[$sourceApp] Price via currency-after: $it"); return it }

        // 3. Bare decimal not near minute/km tokens
        val minuteNums = minutesRegex.findAll(text).map { it.groupValues[1] }.toSet()
        barePriceRegex.findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in minuteNums }
            .mapNotNull { it.toDoubleOrNull() }
            .filter { it in 1.0..9999.0 }
            .firstOrNull()
            ?.also { Log.d(TAG, "[$sourceApp] Price via bare decimal: $it"); return it }

        // 4. Integer fallback (5–999 SAR range, not a minute number)
        Regex("""(?<!\d)(\d{1,4})(?!\d)""").findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in minuteNums }
            .mapNotNull { it.toIntOrNull() }
            .filter { it in 5..999 }
            .firstOrNull()
            ?.toDouble()
            ?.also { Log.d(TAG, "[$sourceApp] Price via integer fallback: $it"); return it }

        Log.w(TAG, "[$sourceApp] Could not extract price. Text: ${text.take(200)}")
        return 0.0
    }

    private fun extractDistances(text: String): Pair<Double, Double> {
        val pkp = pickupDistRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val trp = tripDistRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val all = generalDistRegex.findAll(text)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it > 0 }.toList()
        return Pair(
            pkp ?: all.firstOrNull() ?: 0.0,
            trp ?: all.getOrNull(1) ?: 0.0
        )
    }

    private fun extractLocations(text: String): Pair<String, String> {
        val locRegex = Regex("""(?:from|من|إلى|to|pickup|dropoff)[:\s]+([^\n,]{3,60})""", RegexOption.IGNORE_CASE)
        val matches  = locRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
        return Pair(matches.getOrNull(0) ?: "", matches.getOrNull(1) ?: "")
    }

    /** Exact match against full button text */
    fun isAcceptButtonText(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && ACCEPT_BUTTON_TEXTS.any { it.equals(t, ignoreCase = true) }
    }

    /** Substring match — handles buttons that wrap or add icons around text */
    fun containsAcceptButtonText(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && ACCEPT_BUTTON_TEXTS.any { t.contains(it, ignoreCase = true) }
    }
}
