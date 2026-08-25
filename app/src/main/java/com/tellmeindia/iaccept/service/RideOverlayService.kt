import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.content.pm.ServiceInfo
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tellmeindia.iaccept.R
import com.tellmeindia.iaccept.logic.RideInfo
import com.tellmeindia.iaccept.ui.RideAlertOverlay
import com.tellmeindia.iaccept.service.RideNotificationListener

class RideOverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null

    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val CHANNEL_ID = "RideAlertChannel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_SHOW_OVERLAY = "com.tellmeindia.iaccept.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.tellmeindia.iaccept.HIDE_OVERLAY"
        
        const val EXTRA_FARE = "extra_fare"
        const val EXTRA_PICKUP_DIST = "extra_pickup_dist"
        const val EXTRA_DROP_DIST = "extra_drop_dist"
        const val EXTRA_PICKUP_ADDR = "extra_pickup_addr"
        const val EXTRA_DROP_ADDR = "extra_drop_addr"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_NOTIFICATION_KEY = "extra_notification_key"
        const val EXTRA_IS_MATCH = "extra_is_match"
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            // Log or handle
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                val fare = intent.getIntExtra(EXTRA_FARE, 0)
                val pDist = intent.getDoubleExtra(EXTRA_PICKUP_DIST, 0.0)
                val dDist = intent.getDoubleExtra(EXTRA_DROP_DIST, 0.0)
                val pAddr = intent.getStringExtra(EXTRA_PICKUP_ADDR) ?: ""
                val dAddr = intent.getStringExtra(EXTRA_DROP_ADDR) ?: ""
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                val key = intent.getStringExtra(EXTRA_NOTIFICATION_KEY) ?: ""
                val isMatch = intent.getBooleanExtra(EXTRA_IS_MATCH, false)
                
                val rideInfo = RideInfo(listOf(fare), pDist, dDist, pAddr, dAddr, text)
                showOverlay(rideInfo, isMatch, key)
            }
            ACTION_HIDE_OVERLAY -> hideOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay(rideInfo: RideInfo, isMatch: Boolean, notificationKey: String) {
        if (composeView != null) hideOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or 
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RideOverlayService)
            setViewTreeViewModelStoreOwner(this@RideOverlayService)
            setViewTreeSavedStateRegistryOwner(this@RideOverlayService)
            
            setContent {
                RideAlertOverlay(
                    rideInfo = rideInfo,
                    isMatch = isMatch,
                    onAccept = {
                        RideNotificationListener.acceptRide(notificationKey)
                        hideOverlay()
                    },
                    onDismiss = { hideOverlay() }
                )
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun hideOverlay() {
        composeView?.run {
            windowManager.removeView(this)
            composeView = null
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.channel_desc))
            .setSmallIcon(R.drawable.app_logo)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}
