package com.tellmeindia.iaccept.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.tellmeindia.iaccept.R
import com.tellmeindia.iaccept.data.SupabaseManager
import com.tellmeindia.iaccept.data.IAcceptDatabase
import com.tellmeindia.iaccept.data.PreferenceManager
import com.tellmeindia.iaccept.logic.RideFilterEngine
import com.tellmeindia.iaccept.logic.RideInfo
import com.tellmeindia.iaccept.logic.MatchResult as RideMatchResult
import kotlinx.coroutines.*
import java.util.LinkedList
import java.util.Deque

class RideAccessibilityService : AccessibilityService() {

    companion object {
        const val CHANNEL_ID = "RideAccessibilityChannel"
        const val SUCCESS_CHANNEL_ID = "RideSuccessChannel"
        const val IGNORE_CHANNEL_ID = "RideIgnoreChannel"
        const val NOTIF_ID = 2
        const val RIDE_REPORT_ID = 500
        
        var isServiceRunning = false
            private set

        val acceptedRides = java.util.concurrent.ConcurrentHashMap<String, Long>()

        fun markRideAccepted(fingerprint: String) {
            if (fingerprint.isNotBlank()) {
                acceptedRides[fingerprint] = System.currentTimeMillis()
            }
        }

        fun isRideRecentlyAccepted(fingerprint: String): Boolean {
            val timestamp = acceptedRides[fingerprint] ?: return false
            return (System.currentTimeMillis() - timestamp) < 30000
        }
    }

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var supabaseManager: SupabaseManager
    private var isSubscribed = true
    private val filterEngine = RideFilterEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastScanTime = 0L
    private var lastPruneTime = 0L
    private var activeMediaPlayer: MediaPlayer? = null

    private var cachedMinFare = 0
    private var cachedMaxDistance = 100.0
    private var cachedAutoAccept = false
    private var cachedAllowParcels = true
    private var cachedAutomationEnabled = true
    private var cachedUpiSafeMode = false
    private var cachedScreenInteraction = true
    private var cachedRapidoEnabled = true
    private var cachedUberEnabled = true
    
    private var isHighEndDevice = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        
        // 1. Detect Hardware Tier ONCE (Prevents UI Hangs on Low-End Phones)
        try {
            isHighEndDevice = com.tellmeindia.iaccept.logic.HardwareDetector.getHardwareTier(this) == com.tellmeindia.iaccept.logic.HardwareTier.HIGH_END
        } catch (e: Throwable) {
            isHighEndDevice = false
        }

        try {
            createNotificationChannels()
            refreshForegroundNotification()
        } catch (e: Throwable) { }

        // 2. Hardware Speed: Bind scanning thread to Prime CPU Cores + Lock RAM
        try {
            com.tellmeindia.iaccept.logic.NativeRideEngine().optimizeHardwareSpeed(this)
        } catch (e: Throwable) { }

        // 3. Cellular Speed: Start 5G Modem Radio Pre-warmer
        try {
            com.tellmeindia.iaccept.logic.RadioPrewarmer.startPrewarming(serviceScope)
        } catch (e: Throwable) { }
    }

    private fun refreshForegroundNotification() {
        val status = if (cachedUpiSafeMode) "UPI SAFE MODE ACTIVE" 
                    else if (cachedAutomationEnabled) "Elite Scanner Active 🛰️" 
                    else "Automation STOPPED"
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IAccept Status")
            .setContentText(status)
            .setSmallIcon(R.drawable.app_logo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Throwable) {
            try {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIF_ID, notification)
            } catch (ex: Throwable) { }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val watchdogChannel = NotificationChannel(CHANNEL_ID, "Service Status", NotificationManager.IMPORTANCE_LOW)
            val successChannel = NotificationChannel(SUCCESS_CHANNEL_ID, "Ride Success", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(null, null) 
            }
            val ignoreChannel = NotificationChannel(IGNORE_CHANNEL_ID, "Ride Ignored", NotificationManager.IMPORTANCE_LOW)
            
            manager.createNotificationChannel(watchdogChannel)
            manager.createNotificationChannel(successChannel)
            manager.createNotificationChannel(ignoreChannel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferenceManager = PreferenceManager(this)
        supabaseManager = SupabaseManager(this)
        startSettingsCollector()
    }

    private fun startSettingsCollector() {
        serviceScope.launch {
            launch { supabaseManager.subscriptionActive.collect { isSubscribed = it } }
            launch { preferenceManager.minFare.collect { cachedMinFare = it } }
            launch { preferenceManager.maxDistance.collect { cachedMaxDistance = it } }
            launch { preferenceManager.autoAcceptEnabled.collect { cachedAutoAccept = it } }
            launch { preferenceManager.parcelFilter.collect { cachedAllowParcels = it } }
            launch { preferenceManager.rapidoEnabled.collect { cachedRapidoEnabled = it } }
            launch { preferenceManager.uberEnabled.collect { cachedUberEnabled = it } }
            launch { 
                preferenceManager.automationMaster.collect { 
                    cachedAutomationEnabled = it
                    refreshForegroundNotification()
                } 
            }
            launch { 
                preferenceManager.upiSafeMode.collect { 
                    cachedUpiSafeMode = it
                    refreshForegroundNotification()
                    if (it) try { disableSelf() } catch (e: Throwable) { }
                } 
            }
            launch {
                preferenceManager.screenInteraction.collect {
                    cachedScreenInteraction = it
                    refreshForegroundNotification()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            if (!cachedAutomationEnabled || cachedUpiSafeMode || !isSubscribed) return

            val eventPkg = event.packageName?.toString() ?: ""
            val isRapidoEvent = eventPkg.contains("rapido", true) || eventPkg.contains("captain", true)
            val isUberEvent = eventPkg.contains("uber", true)

            if (!isRapidoEvent && !isUberEvent) return
            if (isRapidoEvent && !cachedRapidoEnabled) return
            if (isUberEvent && !cachedUberEnabled) return

            // 1. Adaptive Throttling based on hardware (Prevents low-end phone freezes)
            val throttleInterval = if (isHighEndDevice) 30L else 150L

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastScanTime < throttleInterval) return
            lastScanTime = currentTime

            serviceScope.launch(Dispatchers.Default) {
                try {
                    processEventOptimized(event)
                } catch (e: Throwable) { }
            }
        } catch (e: Throwable) { }
    }

    private fun processEventOptimized(event: AccessibilityEvent) {
        // Periodic Pruning (Every 10 mins) to keep memory at absolute zero
        val now = System.currentTimeMillis()
        if (now - lastPruneTime > 600000) {
            lastPruneTime = now
            acceptedRides.entries.removeIf { (now - it.value) > 3600000 }
        }

        val roots = mutableListOf<AccessibilityNodeInfo>()
        
        event.source?.let { roots.add(it) }
        
        if (roots.isEmpty()) {
            try {
                windows.find { win ->
                    val rootPkg = win.root?.packageName?.toString() ?: ""
                    rootPkg.contains("rapido", true) || rootPkg.contains("captain", true) || rootPkg.contains("uber", true)
                }?.root?.let { roots.add(it) }
            } catch (e: Exception) {}
        }

        if (roots.isEmpty()) return

        for (root in roots) {
            val acceptNodes = findAllAcceptButtons(root)
            
            for (acceptNode in acceptNodes) {
                val targetCard = findRideCardContainer(acceptNode)
                val capturedText = collectAllTextOptimized(targetCard)

                val rideInfo = filterEngine.parseNotification(capturedText)
                if (rideInfo != null) {
                    if (isRideRecentlyAccepted(rideInfo.fingerprint)) {
                        return
                    }
                    val match = filterEngine.checkMatch(rideInfo, cachedMinFare, cachedMaxDistance, cachedAllowParcels)
                    
                    if (match.isMatch) {
                        if (cachedAutoAccept && cachedScreenInteraction) {
                            if (performRobustClick(acceptNode)) {
                                markRideAccepted(rideInfo.fingerprint)
                                serviceScope.launch(Dispatchers.Main) {
                                    handleSuccessfulAccept(rideInfo)
                                }
                                return 
                            }
                        } else {
                            triggerVisualAlert(rideInfo)
                        }
                    } else {
                        if (acceptedRides[rideInfo.fingerprint] == null) {
                            markRideAccepted(rideInfo.fingerprint)
                            serviceScope.launch(Dispatchers.Main) {
                                handleIgnoredRide(rideInfo, match.reason)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun collectAllTextOptimized(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        val queue: Deque<AccessibilityNodeInfo> = LinkedList()
        queue.add(node)
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val text = current.text
            if (text != null) sb.append(text).append("|")
            val desc = current.contentDescription
            if (desc != null) sb.append(desc).append("|")
            
            for (i in 0 until current.childCount) {
                val child = current.getChild(i)
                if (child != null) queue.addLast(child)
            }
            if (current != node) {
                try { current.recycle() } catch (e: Exception) {}
            }
        }
        return sb.toString()
    }

    private fun findAllAcceptButtons(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        if (text.contains("accept") || contentDesc.contains("accept")) {
            list.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) findAllAcceptButtons(child, list)
        }
        return list
    }

    private fun performRobustClick(node: AccessibilityNodeInfo): Boolean {
        var clicked = false
        if (node.isClickable) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        if (!clicked) {
            var p = node.parent
            var depth = 0
            while (p != null && depth < 4) {
                if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    clicked = true
                    break
                }
                p = p.parent
                depth++
            }
        }
        
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()
        
        if (x > 0 && y > 0) {
            val path = android.graphics.Path()
            path.moveTo(x, y)
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 1)) // 1ms Ultra-Fast Tap
                .build()
            val gestureDispatched = dispatchGesture(gesture, null, null)
            if (gestureDispatched) clicked = true
        }

        return clicked
    }

    private fun findRideCardContainer(button: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = button
        var depth = 0
        while (current != null && depth < 8) {
            val text = collectAllTextOptimized(current)
            if (text.contains("km") || text.contains("mi")) {
                return current
            }
            current = current.parent
            depth++
        }
        return button.parent ?: button
    }

    private fun triggerVisualAlert(rideInfo: RideInfo) {
        val overlayIntent = Intent(this, RideOverlayService::class.java).apply {
            action = RideOverlayService.ACTION_SHOW_OVERLAY
            putExtra(RideOverlayService.EXTRA_FARE, rideInfo.totalFare)
            putExtra(RideOverlayService.EXTRA_PICKUP_DIST, rideInfo.pickupDistance)
            putExtra(RideOverlayService.EXTRA_DROP_DIST, rideInfo.dropDistance)
            putExtra(RideOverlayService.EXTRA_PICKUP_ADDR, rideInfo.pickupAddress)
            putExtra(RideOverlayService.EXTRA_DROP_ADDR, rideInfo.dropAddress)
            putExtra(RideOverlayService.EXTRA_TEXT, rideInfo.rawText)
            putExtra(RideOverlayService.EXTRA_IS_MATCH, true)
        }
        try {
            startService(overlayIntent)
        } catch (e: Throwable) { }
    }

    private fun handleIgnoredRide(rideInfo: RideInfo, reason: String) {
        val fareBreakdown = rideInfo.fares.joinToString(" + ") { "₹$it" }
        val totalFareText = "Total ₹${rideInfo.totalFare}"
        
        val notification = NotificationCompat.Builder(this, IGNORE_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("Ride Filtered Out 🛡️")
            .setContentText("$totalFareText | Filtered")
            .setColor(0xFFFF5252.toInt())
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("Ride Details (Filtered)")
                .bigText("💰 FARE: $fareBreakdown ($totalFareText)\n" +
                         "🛣️ DIST: ${rideInfo.pickupDistance} km + ${rideInfo.dropDistance} km\n" +
                         "📍 FROM: ${rideInfo.pickupAddress}\n" +
                         "🏁 TO: ${rideInfo.dropAddress}\n\n" +
                         "🚫 REASON: $reason"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RIDE_REPORT_ID, notification)
    }

    private fun handleSuccessfulAccept(rideInfo: RideInfo) {
        triggerVisualAlert(rideInfo)
        playCatSound()
        showSuccessNotification(rideInfo)
    }

    private fun playCatSound() {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = MediaPlayer.create(this, R.raw.cat_meow)
            activeMediaPlayer?.setOnCompletionListener { 
                it.release()
                if (activeMediaPlayer == it) activeMediaPlayer = null
            }
            activeMediaPlayer?.start()
        } catch (e: Exception) { }
    }

    private fun showSuccessNotification(rideInfo: RideInfo) {
        val fareBreakdown = rideInfo.fares.joinToString(" + ") { "₹$it" }
        val totalFareText = "Total ₹${rideInfo.totalFare}"
        
        val notification = NotificationCompat.Builder(this, SUCCESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("NEW RIDE SECURED! ✅")
            .setContentText("$totalFareText | ${rideInfo.totalDistance} km")
            .setColor(0xFF4CAF50.toInt())
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("NEW RIDE SECURED! ✅")
                .bigText("💰 FARE: $fareBreakdown ($totalFareText)\n" +
                         "🛣️ DIST: ${rideInfo.pickupDistance} km (P) + ${rideInfo.dropDistance} km (D)\n\n" +
                         "📍 FROM: ${rideInfo.pickupAddress}\n" +
                         "🏁 TO: ${rideInfo.dropAddress}\n\n" +
                         "Drive safe, Captain! 🛣️💨"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 400, 100, 400))
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RIDE_REPORT_ID, notification)
    }

    override fun onDestroy() {
        isServiceRunning = false
        try {
            serviceScope.cancel()
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        } catch (e: Throwable) { }
        super.onDestroy()
    }

    override fun onInterrupt() {
        isServiceRunning = false
    }
}
