package com.tellmeindia.iaccept.logic

import android.util.Log

data class RideInfo(
    val fares: List<Int>,
    val pickupDistance: Double,
    val dropDistance: Double,
    val pickupAddress: String,
    val dropAddress: String,
    val rawText: String,
    val fingerprint: String = "",
    val isParcel: Boolean = false
) {
    val totalFare: Int get() = fares.sum()
    val totalDistance: Double get() = pickupDistance + dropDistance
}

data class MatchResult(
    val isMatch: Boolean,
    val reason: String
)

class RideFilterEngine {

    companion object {
        private const val TAG = "RideFilterEngine"
        private val fareRegex = Regex("₹\\s?(\\d+(?:\\.\\d+)?)")
        private val distanceRegex = Regex("(\\d+(?:\\.\\d+)?)\\s?(?:km|mi)", RegexOption.IGNORE_CASE)
    }

    fun parseNotification(text: String): RideInfo? {
        val lowerCombined = text.lowercase()
        
        // 1. Distance Extraction
        val distanceMatches = distanceRegex.findAll(text).toList()
        if (distanceMatches.isEmpty() && !lowerCombined.contains("parcel")) return null
        val allDistances = distanceMatches.map { it.groupValues[1].toDoubleOrNull() ?: 0.0 }

        // 2. Fare Extraction (Summing all parts including added/extra)
        val primaryFares = mutableListOf<Int>()
        val seenFareValues = mutableSetOf<Int>()
        
        text.split('|', '\n').forEach { line ->
            val lower = line.lowercase()
            // Ignore system meta-info but ALLOW "added" and "extra" to catch bonuses
            if (!lower.contains("waiting") && !lower.contains("status")) {
                fareRegex.findAll(line).forEach { match ->
                    val v = match.groupValues[1].toDoubleOrNull()?.toInt() ?: 0
                    // Accept any reasonable fare part
                    if (v in 1..9999) {
                        primaryFares.add(v)
                        // Note: Not using seenFareValues set here to allow identical parts like ₹21 + ₹21
                    }
                }
            }
        }
        
        // Handle the case where no ₹ symbol is used but there is a + pattern (e.g. 21 + 1)
        if (primaryFares.size < 2 && text.contains("+")) {
            val plusPattern = Regex("(\\d+)\\s?\\+\\s?(\\d+)")
            plusPattern.find(text)?.let { match ->
                val v1 = match.groupValues[1].toIntOrNull() ?: 0
                val v2 = match.groupValues[2].toIntOrNull() ?: 0
                if (primaryFares.isEmpty()) {
                    primaryFares.add(v1)
                    primaryFares.add(v2)
                } else if (primaryFares.size == 1 && primaryFares[0] == v1) {
                    primaryFares.add(v2)
                }
            }
        }
        
        if (primaryFares.isEmpty()) return null
        val totalFare = primaryFares.sum()

        // 3. Distance Allocation
        var pickupDist = 0.0
        var dropDist = 0.0
        if (allDistances.size >= 2) {
            pickupDist = allDistances[0]
            dropDist = allDistances[1]
        } else if (allDistances.size == 1) {
            val d = allDistances[0]
            if (d > 4.0 || lowerCombined.contains("total")) dropDist = d else pickupDist = d
        }

        // 4. Elite Address Discovery (Strict Filtering)
        val lines = text.split('|', '\n').map { it.trim() }.filter { it.isNotBlank() }
        val addressCandidates = lines.filter { 
            it.length > 12 && 
            !it.contains('₹') && 
            !it.lowercase().contains("km") &&
            !it.lowercase().contains("mi") &&
            !it.lowercase().contains("accept") && 
            !it.lowercase().contains("captain") &&
            !it.lowercase().contains("uber") &&
            !it.lowercase().contains("rapido") &&
            !it.lowercase().contains("notification") &&
            !it.lowercase().contains("control") &&
            !it.lowercase().contains("center") &&
            !it.lowercase().contains("status") &&
            !it.lowercase().contains("waiting") &&
            !it.lowercase().contains("battery") &&
            !it.lowercase().contains("signal") &&
            !it.lowercase().contains("system")
        }

        var pickupAddr = "Location Discovery Failed"
        var dropAddr = "Destination Discovery Failed"
        
        if (addressCandidates.size >= 2) {
            pickupAddr = addressCandidates[0]
            dropAddr = addressCandidates[1]
        } else if (addressCandidates.size == 1) {
            pickupAddr = addressCandidates[0]
        }

        // Smart Fallback for "to" format
        if (dropAddr.contains("Failed") && lowerCombined.contains(" to ")) {
            val toIdx = lines.indexOfFirst { it.lowercase() == "to" }
            if (toIdx != -1 && toIdx + 1 < lines.size) {
                dropAddr = lines[toIdx + 1]
            }
        }

        val fingerprint = "${totalFare}_${pickupDist}_${dropDist}_${pickupAddr.take(5)}"
        return RideInfo(
            fares = primaryFares,
            pickupDistance = pickupDist,
            dropDistance = dropDist,
            pickupAddress = pickupAddr,
            dropAddress = dropAddr,
            rawText = text,
            fingerprint = fingerprint,
            isParcel = lowerCombined.contains("parcel") || lowerCombined.contains("package") || lowerCombined.contains("delivery")
        )
    }

    fun checkMatch(info: RideInfo, minFare: Int, maxDistance: Double, allowParcels: Boolean = true): MatchResult {
        if (!allowParcels && info.isParcel) {
            return MatchResult(false, "IGNORE: Parcel ride (Disabled)")
        }

        if (info.totalFare < 10) {
            return MatchResult(false, "Fare too low (Safety floor)")
        }
        
        if (info.totalFare < minFare) {
            return MatchResult(false, "Fare ₹${info.totalFare} < Min ₹$minFare")
        }
        
        if (info.totalDistance > maxDistance) {
            return MatchResult(false, "Distance ${info.totalDistance}km > Max ${maxDistance}km")
        }

        return MatchResult(true, "Match Success: ₹${info.totalFare} | ${info.totalDistance}km")
    }
}
