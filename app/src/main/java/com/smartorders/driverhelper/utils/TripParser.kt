package com.smartorders.driverhelper.utils

import com.smartorders.driverhelper.model.DetectedTrip
import android.util.Log

object TripParser {

    private const val TAG = "TripParser"

    // Accept button variants — all apps, Arabic + English
    private val ACCEPT_BUTTON_TEXTS = setOf(
        "قبول العرض",
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

    // Jeeny/Careem trip request screen markers
    private val JEENY_REQUEST_MARKERS = listOf("قبول العرض", "يبعد", "دقائق", "دقيقة")
    private val UBER_REQUEST_MARKERS  = listOf("Accept", "Accept Trip", "New Trip", "Pickup")
    private val CAREEM_REQUEST_MARKERS = listOf("Accept", "New Booking", "قبول")
    private val BOLT_REQUEST_MARKERS  = listOf("Accept", "New order", "قبول")

    // Price with explicit currency
    private val priceWithCurrencyRegex = Regex(
        """(\d{1,4}(?:\.\d{1,2})?)\s*(?:SAR|ريال|﷼|SR|ر\.س)""",
        RegexOption.IGNORE_CASE
    )

    // Bare price: a decimal number NOT followed by دقيقة/دقائق/min/km/كم
    // Must be between 1.00 and 9999.99 — looks like a fare
    private val barePriceRegex = Regex(
        """(?<![.\d])(\d{1,4}\.\d{1,2})(?!\s*(?:دقيقة|دقائق|min|km|كم|%))"""
    )

    // Distance variants
    private val pickupDistanceRegex = Regex(
        """(?:pickup|استلام|التقاط|وقت الاستلام|الوصول إليك)[^\d]*(\d+(?:\.\d+)?)\s*(?:km|كم|k)""",
        RegexOption.IGNORE_CASE
    )
    private val tripDistanceRegex = Regex(
        """(?:trip|رحلة|المسافة|distance)[^\d]*(\d+(?:\.\d+)?)\s*(?:km|كم|k)""",
        RegexOption.IGNORE_CASE
    )
    private val generalDistanceRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:km|كم)""", RegexOption.IGNORE_CASE)

    // Minutes — so we don't mistake "1" in "يبعد 1 دقائق" for a price
    private val minutesRegex = Regex("""(\d+)\s*(?:دقيقة|دقائق|min)""", RegexOption.IGNORE_CASE)

    fun isRequestScreen(sourceApp: String, rawText: String): Boolean {
        val markers = when (sourceApp) {
            "jeeny"  -> JEENY_REQUEST_MARKERS
            "uber"   -> UBER_REQUEST_MARKERS
            "careem" -> CAREEM_REQUEST_MARKERS
            "bolt"   -> BOLT_REQUEST_MARKERS
            else     -> JEENY_REQUEST_MARKERS + UBER_REQUEST_MARKERS
        }
        val found = markers.any { rawText.contains(it) }
        Log.d(TAG, "isRequestScreen[$sourceApp] = $found (checked ${markers.size} markers)")
        return found
    }

    fun parse(sourceApp: String, rawText: String): DetectedTrip? {
        if (rawText.isBlank()) {
            Log.d(TAG, "[$sourceApp] Empty screen text — skipping")
            return null
        }

        // Must look like a trip request screen
        if (!isRequestScreen(sourceApp, rawText)) {
            Log.d(TAG, "[$sourceApp] Screen does not match trip request markers — skipping")
            return null
        }

        val price = extractPrice(sourceApp, rawText)
        if (price <= 0.0) {
            Log.w(TAG, "[$sourceApp] Trip screen detected but no price found. Text preview: ${rawText.take(200)}")
            // Return a trip with price=0 so it still gets logged + counted as detected
            // Rules will reject it because price < minPrice (unless minPrice is 0)
            // We still want to count it as "detected" and attempt button click
        }

        val distances = extractDistances(rawText)
        val locations = extractLocations(rawText)

        val trip = DetectedTrip(
            sourceApp = sourceApp,
            rawText = rawText,
            price = price,
            pickupDistance = distances.first,
            tripDistance = distances.second,
            pickupLocation = locations.first,
            destinationLocation = locations.second
        )
        Log.i(TAG, "[$sourceApp] Parsed trip: price=${trip.price} SAR, pickup=${trip.pickupDistance}km, trip=${trip.tripDistance}km")
        return trip
    }

    private fun extractPrice(sourceApp: String, text: String): Double {
        // 1. Try explicit currency first (most reliable)
        val withCurrency = priceWithCurrencyRegex.find(text)
        if (withCurrency != null) {
            val p = withCurrency.groupValues[1].toDoubleOrNull() ?: 0.0
            Log.d(TAG, "[$sourceApp] Price via currency pattern: $p")
            return p
        }

        // 2. Collect all numbers mentioned as minutes — exclude them from price candidates
        val minuteNumbers = minutesRegex.findAll(text).map { it.groupValues[1] }.toSet()
        Log.d(TAG, "[$sourceApp] Minute numbers to exclude: $minuteNumbers")

        // 3. Try bare decimal (e.g., "6.21")
        val candidates = barePriceRegex.findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in minuteNumbers }
            .mapNotNull { it.toDoubleOrNull() }
            .filter { it in 1.0..9999.0 }
            .toList()

        Log.d(TAG, "[$sourceApp] Bare price candidates: $candidates")

        if (candidates.isNotEmpty()) {
            val p = candidates.first()
            Log.d(TAG, "[$sourceApp] Price via bare decimal: $p")
            return p
        }

        // 4. Last resort: any standalone integer in reasonable fare range,
        //    excluding minute numbers
        val intCandidates = Regex("""(?<!\d)(\d{1,4})(?!\d)""").findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in minuteNumbers }
            .mapNotNull { it.toDoubleOrNull() }
            .filter { it in 5.0..999.0 }
            .toList()

        Log.d(TAG, "[$sourceApp] Integer price candidates: $intCandidates")

        return intCandidates.firstOrNull() ?: 0.0
    }

    private fun extractDistances(text: String): Pair<Double, Double> {
        val pickupMatch = pickupDistanceRegex.find(text)
        val tripMatch = tripDistanceRegex.find(text)
        val allDistances = generalDistanceRegex.findAll(text)
            .map { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            .filter { it > 0 }
            .toList()

        val pickupDist = pickupMatch?.groupValues?.get(1)?.toDoubleOrNull()
            ?: allDistances.firstOrNull() ?: 0.0
        val tripDist = tripMatch?.groupValues?.get(1)?.toDoubleOrNull()
            ?: allDistances.getOrNull(1) ?: 0.0

        return Pair(pickupDist, tripDist)
    }

    private fun extractLocations(text: String): Pair<String, String> {
        val locationRegex = Regex("""(?:from|من|إلى|to|pickup|dropoff)[:\s]+([^\n,]{3,60})""", RegexOption.IGNORE_CASE)
        val matches = locationRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
        return Pair(
            matches.getOrNull(0) ?: "",
            matches.getOrNull(1) ?: ""
        )
    }

    fun isAcceptButtonText(text: String): Boolean {
        val normalised = text.trim()
        if (normalised.isEmpty()) return false
        val match = ACCEPT_BUTTON_TEXTS.any { it.equals(normalised, ignoreCase = true) }
        if (match) Log.d(TAG, "isAcceptButtonText matched: '$normalised'")
        return match
    }

    fun containsAcceptButtonText(text: String): Boolean {
        val normalised = text.trim()
        if (normalised.isEmpty()) return false
        return ACCEPT_BUTTON_TEXTS.any { normalised.contains(it, ignoreCase = true) }
    }
}
