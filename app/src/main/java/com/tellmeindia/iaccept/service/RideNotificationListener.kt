package com.tellmeindia.iaccept.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tellmeindia.iaccept.R
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.tellmeindia.iaccept.data.PreferenceManager
import com.tellmeindia.iaccept.logic.RideInfo
import com.tellmeindia.iaccept.logic.CoreBridge
import com.tellmeindia.iaccept.logic.NativeRideEngine
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class RideNotificationListener : NotificationListenerService() {

    companion object {
        const val TAG = "RideNotificationListener"
        const val DETECT_CHANNEL_ID = "RideDetectChannel"
        private var instance: RideNotificationListener? = null
        
        fun acceptRide(notificationKey: String) {
            instance?.let { service ->
                val sbns = service.activeNotifications
                val sbn = sbns.find { it.key == notificationKey }
                if (sbn != null) service.triggerAcceptAction(sbn, notificationKey)
            }
        }
    }

    private lateinit var preferenceManager: PreferenceManager
    private val nativeEngine = NativeRideEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        preferenceManager = PreferenceManager(this)
        instance = this
        createNotificationChannel()

        // 1. Force Android OS into ACTIVE Standby Bucket (0ms Delay)
        try {
            val notification = NotificationCompat.Builder(this, DETECT_CHANNEL_ID)
                .setContentTitle("IAccept Kernel Engine")
                .setContentText("Listening for high-speed notifications...")
                .setSmallIcon(R.drawable.app_logo)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
            startForeground(101, notification)
        } catch (e: Exception) { }

        // 2. High-Priority CPU WakeLock (Prevents Doze Mode Notification Delay)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "IAccept::ZeroDelayNotifWakeLock").apply {
                acquire(10 * 60 * 1000L) // Holds CPU awake at top priority
            }
        } catch (e: Exception) { }
        
        // 3. Hardware Speed: Bind thread to Prime CPU Cores + Lock RAM pages
        try {
            nativeEngine.optimizeHardwareSpeed(this)
        } catch (e: Exception) { }

        // 4. Cellular Speed: Start 5G Modem Radio Pre-warmer
        com.tellmeindia.iaccept.logic.RadioPrewarmer.startPrewarming(serviceScope)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(DETECT_CHANNEL_ID, "Ride Detection", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // CORE PRIORITY: Set thread to urgent display for Blink-Speed response
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        
        val packageName = sbn.packageName
        if (packageName == this.packageName) return
        
        // 1. NATIVE-SPEED PACKAGE CHECK
        val isRapido = packageName.contains("rapido", true) || packageName.contains("captain", true)
        val isUber = packageName.contains("uber", true) || packageName.contains("driver", true)
        if (!isRapido && !isUber) return

        serviceScope.launch(Dispatchers.Default) {
            // 2. ELITE PLATFORM TOGGLES
            val rapidoEnabled = preferenceManager.rapidoEnabled.first()
            val uberEnabled = preferenceManager.uberEnabled.first()
            if (isRapido && !rapidoEnabled) return@launch
            if (isUber && !uberEnabled) return@launch

            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            
            val rideInfo = nativeEngine.parseRide(title, "$title $text $bigText")
            if (rideInfo != null) {
                // PRE-DECISION: Prime the core bridge
                val minFare = preferenceManager.minFare.first()
                val maxDistance = preferenceManager.maxDistance.first()
                val allowParcels = preferenceManager.parcelFilter.first()
                
                val isMatch = checkMatch(rideInfo, minFare, maxDistance, allowParcels)
                CoreBridge.prime(rideInfo.fingerprint, isMatch)
                
                handleRideRequest(rideInfo, sbn)
            }
        }
    }

    private fun checkMatch(info: RideInfo, minFare: Int, maxDistance: Double, allowParcels: Boolean): Boolean {
        if (!allowParcels && info.isParcel) return false
        if (info.totalFare < minFare) return false
        if (info.totalDistance > maxDistance) return false
        return true
    }

    private fun getIgnoreReason(info: RideInfo, minFare: Int, maxDistance: Double, allowParcels: Boolean): String {
        if (!allowParcels && info.isParcel) return "Parcel"
        if (info.totalFare < minFare) return "Fare ₹${info.totalFare} < Min ₹$minFare"
        if (info.totalDistance > maxDistance) return "Distance"
        return "Unknown"
    }

    private fun handleRideRequest(rideInfo: RideInfo, sbn: StatusBarNotification) {
        serviceScope.launch {
            val masterEnabled = preferenceManager.automationMaster.first()
            if (!masterEnabled) return@launch

            val minFare = preferenceManager.minFare.first()
            val maxDistance = preferenceManager.maxDistance.first()
            val autoAccept = preferenceManager.autoAcceptEnabled.first()
            val allowParcels = preferenceManager.parcelFilter.first()

            val isMatch = checkMatch(rideInfo, minFare, maxDistance, allowParcels)
            val reason = getIgnoreReason(rideInfo, minFare, maxDistance, allowParcels)
            
            showDetailedDetectNotification(rideInfo, isMatch, reason)

            if (isMatch) {
                val overlayIntent = Intent(this@RideNotificationListener, RideOverlayService::class.java).apply {
                    action = RideOverlayService.ACTION_SHOW_OVERLAY
                    putExtra(RideOverlayService.EXTRA_FARE, rideInfo.totalFare)
                    putExtra(RideOverlayService.EXTRA_PICKUP_DIST, rideInfo.pickupDistance)
                    putExtra(RideOverlayService.EXTRA_DROP_DIST, rideInfo.dropDistance)
                    putExtra(RideOverlayService.EXTRA_PICKUP_ADDR, rideInfo.pickupAddress)
                    putExtra(RideOverlayService.EXTRA_DROP_ADDR, rideInfo.dropAddress)
                    putExtra(RideOverlayService.EXTRA_TEXT, rideInfo.rawText)
                    putExtra(RideOverlayService.EXTRA_NOTIFICATION_KEY, sbn.key)
                    putExtra(RideOverlayService.EXTRA_IS_MATCH, true)
                }
                startService(overlayIntent)

                if (autoAccept) {
                    val accepted = triggerAcceptAction(sbn, rideInfo.fingerprint)
                    if (accepted) {
                        try {
                            cancelNotification(sbn.key)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cancelling sbn: ${e.message}")
                        }
                        Log.d(TAG, "ZERO-UI RIDE ACCEPTED INSTANTLY & SILENTLY CANCELLED FROM TRAY")
                    }
                }
            }
        }
    }

    private fun showDetailedDetectNotification(info: RideInfo, isMatch: Boolean, reason: String) {
        val statusText = if (isMatch) "MATCHED ✅" else "IGNORED 🛡️"
        val channelId = if (isMatch) RideAccessibilityService.SUCCESS_CHANNEL_ID else RideAccessibilityService.IGNORE_CHANNEL_ID
        val notifId = if (isMatch) RideAccessibilityService.SUCCESS_NOTIF_ID else RideAccessibilityService.IGNORE_NOTIF_ID

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("Ride Detected: $statusText")
            .setContentText("Fare: ₹${info.totalFare} | Dist: ${info.totalDistance} km")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "💰 FARE: ₹${info.totalFare} (${info.totalFare} Total)\n" +
                "🛣️ DISTANCE: ${info.pickupDistance} km (P) + ${info.dropDistance} km (D)\n" +
                "📍 FROM: ${info.pickupAddress}\n" +
                "🏁 TO: ${info.dropAddress}\n" +
                "💬 REASON: $reason"
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setTimeoutAfter(if (isMatch) 15000L else 8000L)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { manager.cancelAll() } catch (e: Exception) { }
        manager.notify(notifId, notification)
    }

    private fun triggerAcceptAction(sbn: StatusBarNotification, fingerprint: String? = null): Boolean {
        var actionExecuted = false

        // 1. Inspect Notification Action Buttons
        val actions = sbn.notification.actions
        if (actions != null && actions.isNotEmpty()) {
            for (nAction in actions) {
                try {
                    nAction.actionIntent.send()
                    actionExecuted = true
                    Log.d(TAG, "ZERO-UI: Executed direct action intent: ${nAction.title}")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "ZERO-UI Action Intent Error: ${e.message}")
                }
            }
        }

        // 2. High-Speed Fallback: Notification ContentIntent
        if (!actionExecuted && sbn.notification.contentIntent != null) {
            try {
                sbn.notification.contentIntent.send()
                actionExecuted = true
                Log.d(TAG, "ZERO-UI: Fired contentIntent fallback for zero-delay accept")
            } catch (e: Exception) {
                Log.e(TAG, "ZERO-UI Content Intent Error: ${e.message}")
            }
        }

        // 3. Ultra Fallback: Full Screen Intent
        if (!actionExecuted && sbn.notification.fullScreenIntent != null) {
            try {
                sbn.notification.fullScreenIntent.send()
                actionExecuted = true
                Log.d(TAG, "ZERO-UI: Fired fullScreenIntent fallback")
            } catch (e: Exception) { }
        }

        if (actionExecuted && !fingerprint.isNullOrBlank()) {
            RideAccessibilityService.markRideAccepted(fingerprint)
        }

        return actionExecuted
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) { }
        instance = null
        super.onDestroy()
    }
}
