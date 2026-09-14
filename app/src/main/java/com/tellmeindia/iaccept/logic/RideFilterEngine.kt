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
        private val fareRegex = Regex("(?:₹|Rs\\.?|INR|\\$)\\s?(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        private val distanceRegex = Regex("(\\d+(?:\\.\\d+)?)\\s?(?:km|mi|miles)", RegexOption.IGNORE_CASE)
        
        private val ignoreKeywords = listOf(
            "accept", "captain", "uber", "rapido", "notification", "control", "center", 
            "status", "waiting", "battery", "signal", "system", "cash", "online",
            "waiting for orders", "km", "mi", "min", "sec", "hrs",
            "home", "orders", "order", "go to", "filter", "refer", "performance", "follow", "cab", "bike", "auto"
        )
    }

    fun parseNotification(text: String): RideInfo? {
        val lowerCombined = text.lowercase()
        
        // 1. Distance Extraction
        val distanceMatches = distanceRegex.findAll(text).toList()
        if (distanceMatches.isEmpty() && !lowerCombined.contains("parcel")) return null
        val allDistances = distanceMatches.map { it.groupValues[1].toDoubleOrNull() ?: 0.0 }

        // 2. Ultra-Elite Fare Extraction
        val lines = text.split('|', '\n').map { it.trim() }.filter { it.isNotBlank() }
        val finalFares = mutableListOf<Int>()
        
        val allSingleFares = fareRegex.findAll(text).map { it.groupValues[1].toDoubleOrNull()?.toInt() ?: 0 }.filter { it in 10..9999 }.toList()
        
        if (text.contains("+") && allSingleFares.size >= 2) {
            // Breakdown line: e.g. ₹38 + ₹16 -> take the two breakdown numbers
            finalFares.addAll(allSingleFares.take(2))
        } else if (allSingleFares.isNotEmpty()) {
            finalFares.add(allSingleFares.maxOrNull() ?: 0)
        } else {
            // Fallback for numbers without explicit currency symbol (excluding pincodes > 9999)
            val numRegex = Regex("(\\d{2,4})")
            val rawNums = numRegex.findAll(text).map { it.groupValues[1].toIntOrNull() ?: 0 }.filter { it in 10..9999 }.toList()
            if (text.contains("+") && rawNums.size >= 2) {
                finalFares.addAll(rawNums.take(2))
            } else if (rawNums.isNotEmpty()) {
                finalFares.add(rawNums.maxOrNull() ?: 0)
            }
        }
        
        if (finalFares.isEmpty() || finalFares.sum() == 0) return null
        val totalFare = finalFares.sum()

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

        // 4. Address Discovery
        val addressCandidates = mutableListOf<String>()
        lines.forEach { line ->
            val lowLine = line.lowercase()
            val hasFare = line.contains('₹') || lowLine.contains("rs") || lowLine.contains("inr")
            val isDistanceOnly = distanceRegex.matches(line) || (lowLine.contains("km") && line.length < 10)
            val containsSystemKeyword = ignoreKeywords.any { 
                lowLine == it || lowLine.startsWith("$it ") || lowLine.endsWith(" $it") || lowLine.contains(" $it ")
            }
            
            if (line.length >= 5 && !hasFare && !isDistanceOnly && !containsSystemKeyword) {
                if (lowLine != "accept" && lowLine != "reject" && !lowLine.contains("waiting for orders") && !lowLine.contains("order")) {
                    addressCandidates.add(line)
                }
            }
        }

        var pickupAddr = "Detecting..."
        var dropAddr = "Detecting..."
        if (addressCandidates.isNotEmpty()) {
            val uniqueCandidates = addressCandidates.distinct()
            if (uniqueCandidates.size >= 2) {
                pickupAddr = uniqueCandidates[0]
                dropAddr = uniqueCandidates[1]
            } else {
                pickupAddr = uniqueCandidates[0]
            }
        }

        val fingerprint = "${totalFare}_${pickupDist}_${dropDist}_${pickupAddr.take(5)}_${System.currentTimeMillis()/30000}"
        
        return RideInfo(
            fares = finalFares,
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
        if (!allowParcels && info.isParcel) return MatchResult(false, "IGNORE: Parcel ride")
        if (info.totalFare < 5) return MatchResult(false, "Fare too low")
        if (minFare > 0 && info.totalFare < minFare) {
            return MatchResult(false, "Fare ₹${info.totalFare} < Min ₹$minFare")
        }
        if (maxDistance > 0 && info.totalDistance > maxDistance) {
            return MatchResult(false, "Distance ${info.totalDistance}km > Max ${maxDistance}km")
        }
        return MatchResult(true, "Match Success: ₹${info.totalFare}")
    }
}
