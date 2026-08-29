package com.tellmeindia.iaccept.logic

import android.util.Log

class NativeRideEngine {
    companion object {
        init {
            try {
                System.loadLibrary("iaccept_core")
            } catch (e: Exception) {
                Log.e("NativeRideEngine", "Failed to load library", e)
            }
        }
    }

    external fun extractFares(text: String): IntArray
    external fun extractDistances(text: String): DoubleArray

    fun parseRide(source: String, text: String): RideInfo? {
        // 1. CLEAN RAW DATA: Rapido often sends fares with multi-byte symbols and '+'
        val cleanedText = text.replace("|", " ").replace("\n", " ")
        
        val fares = extractFares(cleanedText).toList()
        
        // RAPIDO SPECIFIC: If we see two fares and a '+', the native engine already extracted them.
        // We just need to ensure we don't miss them if symbols are weird.
        val finalFares = if (fares.isEmpty()) {
            // High-speed fallback with deduplication
            val regex = Regex("(\\d{2,5})")
            val allMatches = regex.findAll(cleanedText).map { it.groupValues[1].toIntOrNull() ?: 0 }.filter { it > 10 }.toList()
            
            if (cleanedText.contains("+")) {
                // If there's a breakdown, prioritize the first two numbers (the base + extra)
                allMatches.take(2)
            } else {
                allMatches.distinct()
            }
        } else fares

        if (finalFares.isEmpty()) return null
        
        val distances = extractDistances(cleanedText)
        var pDist = 0.0
        var dDist = 0.0
        
        // Multi-Distance detection (Pickup vs Drop)
        if (distances.size >= 2) {
            // Usually Rapido shows: [Pickup Dist, Drop Dist] or [Total Dist]
            // In your screenshot: 0 km (pickup) and 36.9 km (drop)
            pDist = distances[0]
            dDist = distances[1]
        } else if (distances.size == 1) {
            val d = distances[0]
            if (cleanedText.lowercase().contains("total") || d > 10.0) dDist = d else pDist = d
        }

        // Address Discovery with Meta-Data awareness
        val segments = cleanedText.split("  ").map { it.trim() }.filter { it.length > 5 }
        val pAddr = segments.find { it.length > 15 && !it.contains("₹") } ?: "Detecting..."
        val dAddr = segments.findLast { it.length > 15 && !it.contains("₹") } ?: "Detecting..."
        
        val isParcel = cleanedText.lowercase().contains("parcel") || cleanedText.lowercase().contains("delivery")

        return RideInfo(
            fares = finalFares,
            pickupDistance = pDist,
            dropDistance = dDist,
            pickupAddress = pAddr,
            dropAddress = dAddr,
            rawText = cleanedText,
            fingerprint = "${finalFares.sum()}_${pDist}_${dDist}",
            isParcel = isParcel
        )
    }
}
