package com.tellmeindia.iaccept.logic

import android.util.Log

class NativeRideEngine {
    companion object {
        var isLibraryLoaded = false
            private set

        init {
            try {
                System.loadLibrary("iaccept_core")
                isLibraryLoaded = true
            } catch (e: Throwable) {
                Log.e("NativeRideEngine", "Failed to load native library: ${e.message}")
                isLibraryLoaded = false
            }
        }
    }

    external fun optimizeHardwareSpeedNative(isHighEnd: Boolean)
    external fun fastExtractFaresBuffer(directBuffer: java.nio.ByteBuffer, length: Int): IntArray
    external fun extractFaresNative(text: String): IntArray
    external fun extractDistancesNative(text: String): DoubleArray

    fun optimizeHardwareSpeed(context: android.content.Context) {
        if (isLibraryLoaded) {
            try {
                val isHighEnd = HardwareDetector.getHardwareTier(context) == HardwareTier.HIGH_END
                optimizeHardwareSpeedNative(isHighEnd)
            } catch (e: Throwable) { }
        }
    }

    fun extractFares(text: String): IntArray {
        return if (isLibraryLoaded) {
            try {
                extractFaresNative(text)
            } catch (e: Throwable) {
                intArrayOf()
            }
        } else {
            intArrayOf()
        }
    }

    fun extractDistances(text: String): DoubleArray {
        return if (isLibraryLoaded) {
            try {
                extractDistancesNative(text)
            } catch (e: Throwable) {
                doubleArrayOf()
            }
        } else {
            doubleArrayOf()
        }
    }

    fun parseRide(source: String, text: String): RideInfo? {
        val cleanedText = text.replace("|", " ").replace("\n", " ")
        
        val fares = extractFares(cleanedText).toList()
        val finalFares = if (fares.isEmpty()) {
            val regex = Regex("(?:₹|Rs\\.?|INR|\\$)\\s?(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
            val allMatches = regex.findAll(cleanedText).map { it.groupValues[1].toIntOrNull() ?: 0 }.filter { it in 10..9999 }.toList()
            if (cleanedText.contains("+") && allMatches.size >= 2) allMatches.take(2) else if (allMatches.isNotEmpty()) listOf(allMatches.maxOrNull() ?: 0) else emptyList()
        } else fares

        if (finalFares.isEmpty()) return null
        
        val distances = extractDistances(cleanedText)
        var pDist = 0.0
        var dDist = 0.0
        
        if (distances.size >= 2) {
            pDist = distances[0]
            dDist = distances[1]
        } else if (distances.size == 1) {
            val d = distances[0]
            if (cleanedText.lowercase().contains("total") || d > 10.0) dDist = d else pDist = d
        }

        // Smart Address Discovery with Uniqueness
        val segments = cleanedText.split(Regex("\\s{2,}")).map { it.trim() }.filter { 
            it.length > 5 && !it.contains("₹") && !it.lowercase().contains("km") 
        }
        
        var pAddr = "Detecting..."
        var dAddr = "Detecting..."
        
        if (segments.size >= 2) {
            pAddr = segments[0]
            dAddr = segments[1]
        } else if (segments.size == 1) {
            pAddr = segments[0]
        }
        
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
