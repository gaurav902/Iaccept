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
                if (sbn != null) service.triggerAcceptAction(sbn)
            }
        }
    }

    private lateinit var preferenceManager: PreferenceManager
    private val nativeEngine = NativeRideEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        preferenceManager = PreferenceManager(this)
        instance = this
        createNotificationChannel()
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

        serviceScope.launch {
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
                    
                    preferenceManager.addLog("CORE PRIME: ₹${rideInfo.totalFare} (${if(isMatch) "MATCH" else "IGNORE"})")
                    handleRideRequest(rideInfo, sbn)
                } else {
                    // Match the user's expected log style from screenshot
                    preferenceManager.addLog("CORE DETECT: $title\n(Parsing failed)")
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
        if (info.totalFare < minFare) return "Fare"
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
            
            val logPrefix = if (isMatch) "NOTIF MATCH" else "NOTIF IGNORE"
            preferenceManager.addLog("$logPrefix: ₹${rideInfo.totalFare} ($reason)")

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

                if (autoAccept) triggerAcceptAction(sbn)
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
            .setContentText("Fare: ₹${info.totalFare} | ${statusText}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("💰 FARE: ₹${info.totalFare}\n📍 FROM: ${info.pickupAddress}\n🏁 TO: ${info.dropAddress}\n💬 REASON: $reason"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, notification)
    }

    private fun triggerAcceptAction(sbn: StatusBarNotification) {
        val actions = sbn.notification.actions ?: return
        val keywords = listOf("accept", "go", "confirm", "yes", "pick")
        for (nAction in actions) {
            val title = nAction.title.toString().lowercase()
            if (keywords.any { title.contains(it) }) {
                try {
                    nAction.actionIntent.send()
                    return
                } catch (e: Exception) { }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
