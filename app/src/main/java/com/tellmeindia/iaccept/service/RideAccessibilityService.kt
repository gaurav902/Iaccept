package com.tellmeindia.iaccept.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
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
import java.util.Date
import java.util.Locale
import java.util.Stack
import java.text.SimpleDateFormat

class RideAccessibilityService : AccessibilityService() {

    companion object {
        const val CHANNEL_ID = "RideAccessibilityChannel"
        const val SUCCESS_CHANNEL_ID = "RideSuccessChannel"
        const val IGNORE_CHANNEL_ID = "RideIgnoreChannel"
        const val NOTIF_ID = 2
        const val SUCCESS_NOTIF_ID = 3
        const val IGNORE_NOTIF_ID = 4
        
        var isServiceRunning = false
            private set
    }

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var supabaseManager: SupabaseManager
    private var isSubscribed = true
    private val filterEngine = RideFilterEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastScanTime = 0L

    private var cachedMinFare = 0
    private var cachedMaxDistance = 100.0
    private var cachedAutoAccept = false
    private var cachedAllowParcels = true
    private var cachedAutomationEnabled = true
    private var cachedUpiSafeMode = false
    private var cachedScreenInteraction = true
    private var cachedRapidoEnabled = true
    private var cachedUberEnabled = true

    private val acceptedRides = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        createNotificationChannels()
        refreshForegroundNotification()
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
        } catch (e: Exception) { }
    }


    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val watchdogChannel = NotificationChannel(CHANNEL_ID, "Accessibility Service", NotificationManager.IMPORTANCE_LOW)
            val successChannel = NotificationChannel(SUCCESS_CHANNEL_ID, "Ride Success Alerts", NotificationManager.IMPORTANCE_HIGH)
            val ignoreChannel = NotificationChannel(IGNORE_CHANNEL_ID, "Ride Ignore Logs", NotificationManager.IMPORTANCE_HIGH)
            
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
                    if (it) {
                        disableSelf()
                    }
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
        if (!cachedAutomationEnabled || cachedUpiSafeMode || !isSubscribed) return

        // 1. STRICT PACKAGE FILTER (Only Uber and Rapido)
        val eventPkg = event.packageName?.toString() ?: ""
        val isRapidoEvent = eventPkg.contains("rapido", true) || eventPkg.contains("captain", true)
        val isUberEvent = eventPkg.contains("uber", true)

        // Completely ignore if not from target apps (Prevents scanning system UI/notifications)
        if (!isRapidoEvent && !isUberEvent) return

        // 2. User preference toggle check
        if (isRapidoEvent && !cachedRapidoEnabled) return
        if (isUberEvent && !cachedUberEnabled) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < 50) return 
        lastScanTime = currentTime

        serviceScope.launch(Dispatchers.Default) {
            processEventOptimized(event)
        }
    }

    private fun processEventOptimized(event: AccessibilityEvent) {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        
        // SPEED OPTIMIZATION: Only use event.source if available. 
        // windows.forEach is extremely slow and causes "late" notifications on Chinese phones.
        event.source?.let { roots.add(it) }
        
        if (roots.isEmpty()) {
            // Fallback only if necessary - windows call is expensive!
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
                    val match = filterEngine.checkMatch(rideInfo, cachedMinFare, cachedMaxDistance, cachedAllowParcels)
                    
                    if (match.isMatch) {
                        if (cachedAutoAccept && cachedScreenInteraction) {
                            if (performRobustClick(acceptNode)) {
                                acceptedRides[rideInfo.fingerprint] = System.currentTimeMillis()
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
                            acceptedRides[rideInfo.fingerprint] = System.currentTimeMillis()
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
        val stack = Stack<AccessibilityNodeInfo>()
        stack.push(node)
        
        while (stack.isNotEmpty()) {
            val current = stack.pop()
            val text = current.text
            if (text != null) {
                sb.append(text).append("|")
            }
            val desc = current.contentDescription
            if (desc != null) {
                sb.append(desc).append("|")
            }
            
            for (i in 0 until current.childCount) {
                val child = current.getChild(i)
                if (child != null) {
                    stack.push(child)
                }
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
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        
        var p = node.parent
        var depth = 0
        while (p != null && depth < 4) {
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            p = p.parent
            depth++
        }
        
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val x = rect.centerX().toFloat()
        val y = rect.centerY().toFloat()
        
        val path = android.graphics.Path()
        path.moveTo(x, y)
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findRideCardContainer(button: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = button
        var depth = 0
        while (current != null && depth < 6) {
            if (current.childCount > 3) return current
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
        startService(overlayIntent)
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
                         "📍 FROM: ${rideInfo.pickupAddress}\n" +
                         "🏁 TO: ${rideInfo.dropAddress}\n\n" +
                         "🚫 REASON: $reason"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(IGNORE_NOTIF_ID, notification)
    }

    private fun handleSuccessfulAccept(rideInfo: RideInfo) {
        triggerVisualAlert(rideInfo)
        playCatSound()
        showSuccessNotification(rideInfo)
    }

    private fun playCatSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.cat_meow)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
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
                         "🛣️ TOTAL: ${rideInfo.totalDistance} km\n\n" +
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
        manager.notify(SUCCESS_NOTIF_ID, notification)
    }



    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }

    override fun onInterrupt() {
        isServiceRunning = false
    }
}
