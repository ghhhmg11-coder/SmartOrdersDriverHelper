package com.smartorders.driverhelper.utils

import com.smartorders.driverhelper.model.DetectedTrip
import android.util.Log

object TripParser {

    private const val TAG = "TripParser"

    private val priceRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:SAR|ريال|﷼|SR)""", RegexOption.IGNORE_CASE)
    private val pickupDistanceRegex = Regex("""(?:pickup|استلام|التقاط|وقت الاستلام)[^\d]*(\d+(?:\.\d+)?)\s*(?:km|كم|k)""", RegexOption.IGNORE_CASE)
    private val tripDistanceRegex = Regex("""(?:trip|رحلة|المسافة|distance)[^\d]*(\d+(?:\.\d+)?)\s*(?:km|كم|k)""", RegexOption.IGNORE_CASE)
    private val generalDistanceRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:km|كم)""", RegexOption.IGNORE_CASE)
    private val locationRegex = Regex("""(?:from|من|إلى|to)[:\s]+([^\n,]+)""", RegexOption.IGNORE_CASE)

    fun parse(sourceApp: String, rawText: String): DetectedTrip? {
        if (rawText.isBlank()) return null

        val price = extractPrice(rawText)
        if (price <= 0.0) {
            Log.d(TAG, "No valid price found in text from $sourceApp")
            return null
        }

        val distances = extractDistances(rawText)
        val locations = extractLocations(rawText)

        return DetectedTrip(
            sourceApp = sourceApp,
            rawText = rawText,
            price = price,
            pickupDistance = distances.first,
            tripDistance = distances.second,
            pickupLocation = locations.first,
            destinationLocation = locations.second
        )
    }

    private fun extractPrice(text: String): Double {
        val match = priceRegex.find(text)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun extractDistances(text: String): Pair<Double, Double> {
        val pickupMatch = pickupDistanceRegex.find(text)
        val tripMatch = tripDistanceRegex.find(text)
        val allDistances = generalDistanceRegex.findAll(text).map {
            it.groupValues[1].toDoubleOrNull() ?: 0.0
        }.toList()

        val pickupDist = pickupMatch?.groupValues?.get(1)?.toDoubleOrNull()
            ?: allDistances.firstOrNull() ?: 0.0
        val tripDist = tripMatch?.groupValues?.get(1)?.toDoubleOrNull()
            ?: allDistances.getOrNull(1) ?: 0.0

        return Pair(pickupDist, tripDist)
    }

    private fun extractLocations(text: String): Pair<String, String> {
        val matches = locationRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
        return Pair(
            matches.getOrNull(0) ?: "",
            matches.getOrNull(1) ?: ""
        )
    }

    fun isAcceptButtonText(text: String): Boolean {
        val normalised = text.trim()
        val acceptVariants = listOf("قبول", "اقبل", "قبول الطلب", "Accept", "ACCEPT")
        return acceptVariants.any { it.equals(normalised, ignoreCase = true) }
    }
}
