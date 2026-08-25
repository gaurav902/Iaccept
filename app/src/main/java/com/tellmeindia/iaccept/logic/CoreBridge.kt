package com.tellmeindia.iaccept.logic

/**
 * CORE-LEVEL SHARED STATE
 * Used to "Prime" the scanner from the notification listener.
 * This bridge allows the app to decide "YES" in the background before the card is even rendered.
 */
object CoreBridge {
    @Volatile
    var lastPrimedFingerprint: String? = null
    
    @Volatile
    var lastPrimedMatch: Boolean = false

    fun prime(fingerprint: String, isMatch: Boolean) {
        lastPrimedFingerprint = fingerprint
        lastPrimedMatch = isMatch
    }
}
