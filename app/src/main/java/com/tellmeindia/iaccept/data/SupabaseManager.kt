package com.tellmeindia.iaccept.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.tellmeindia.iaccept.MainActivity
import com.tellmeindia.iaccept.R
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.PostgrestUpdate
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject

class SupabaseManager(private val context: Context) {

    private val supabaseUrl = "http://72.60.223.74:8000" 
    private val supabaseKey = "sb_publishable_lCmGJsNQx8eg1S-YORUyOp_gKfglsNH"
    private val apiBaseUrl = "https://iaccept.tellmeindia.com/api/payments"

    val client: SupabaseClient = createSupabaseClient(supabaseUrl, supabaseKey) {
        httpEngine = OkHttp.create {
            config {
                cache(null)
            }
        }
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                cache(null)
            }
        }
    }

    private val _subscriptionActive = MutableStateFlow(false)
    val subscriptionActive: StateFlow<Boolean> = _subscriptionActive

    val latestAnnouncement = MutableStateFlow<AdminNotification?>(null)

    private val _profileFlow = MutableStateFlow<ProfileRow?>(null)
    val profileFlow: StateFlow<ProfileRow?> get() = _profileFlow

    private val seenNotifIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            fallbackLocalSubCheck()
            monitorProfileRealtime()
        }
    }

    private var subExpiryJob: Job? = null

    private fun applySubExpiry(expiry: Instant) {
        val now = Clock.System.now()
        val isActive = expiry > now
        _subscriptionActive.value = isActive

        subExpiryJob?.cancel()
        if (isActive) {
            val remainingMs = (expiry - now).inWholeMilliseconds
            if (remainingMs > 0) {
                subExpiryJob = scope.launch {
                    delay(remainingMs)
                    _subscriptionActive.value = false
                }
            }
        }
    }

    private fun fallbackLocalSubCheck() {
        try {
            val localDb = IAcceptDatabase.getDatabase(context)
            val localProfile = localDb.dao().getProfileSync()
            if (localProfile != null && !localProfile.subscriptionUntil.isNullOrBlank()) {
                val rawDate = localProfile.subscriptionUntil
                val isoString = if (!rawDate.contains("T")) rawDate.replace(" ", "T") else rawDate
                val cleanIso = isoString.split("+")[0].split("Z")[0]
                val finalIso = if (cleanIso.endsWith("Z")) cleanIso else "${cleanIso}Z"
                val expiry = try {
                    Instant.parse(finalIso)
                } catch (e: Exception) {
                    Instant.parse("${finalIso.take(10)}T23:59:59Z")
                }
                applySubExpiry(expiry)
            }
        } catch (e: Exception) {
            // Silent fallback, no logging/storage bloat
        }
    }

    private suspend fun monitorProfileRealtime() {
        var attempts = 0
        while (client.auth.currentUserOrNull() == null && attempts < 3) {
            try {
                client.auth.retrieveUserForCurrentSession()
            } catch (e: Exception) { }
            if (client.auth.currentUserOrNull() == null) {
                delay(2000)
                attempts++
            }
        }
        
        val user = client.auth.currentUserOrNull()
        
        // Initial Refresh (will fallback to Room DB if offline/session expired)
        refreshProfile()

        if (user == null) return

        scope.launch {
            try {
                val channel = client.realtime.channel("profile_sync")
                val changeFlow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "profiles"
                }

                changeFlow
                    .onEach { action ->
                        if (action.record["id"]?.toString()?.replace("\"", "") == user.id) {
                            refreshProfile()
                        }
                    }.launchIn(this)

                channel.subscribe()
            } catch (e: Exception) {
                // Silent failure for offline
            }
        }

        scope.launch {
            try {
                val notifChannel = client.realtime.channel("admin_notifs")
                val notifChangeFlow = notifChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "admin_notifications"
                }

                notifChangeFlow
                    .onEach { action ->
                        try {
                            val record = action.record
                            val notifId = record["id"]?.toString()?.replace("\"", "") ?: ""
                            if (notifId.isNotBlank() && seenNotifIds.contains(notifId)) return@onEach
                            if (notifId.isNotBlank()) seenNotifIds.add(notifId)

                            val targetUserId = record["user_id"]?.toString()?.replace("\"", "")
                            val title = record["title"]?.toString()?.replace("\"", "") ?: "IAccept Announcement"
                            val message = record["message"]?.toString()?.replace("\"", "") ?: ""
                            val targetScreen = record["target_screen"]?.toString()?.replace("\"", "") ?: "home"

                            val currentUserId = client.auth.currentUserOrNull()?.id

                            if (targetUserId.isNullOrBlank() || targetUserId == "null" || targetUserId == "NULL" || targetUserId == currentUserId) {
                                val notifObj = AdminNotification(
                                    id = notifId,
                                    userId = targetUserId,
                                    title = title,
                                    message = message,
                                    targetScreen = targetScreen
                                )
                                latestAnnouncement.value = notifObj
                                showAdminSystemNotification(title, message, targetScreen, notifId)
                            }
                        } catch (e: Exception) { }
                    }.launchIn(this)

                notifChannel.subscribe()
            } catch (e: Exception) {
                // Silent failure for offline
            }
        }
    }

    private fun showAdminSystemNotification(title: String, message: String, targetScreen: String, notifId: String?) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "AdminNotifChannel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Admin Announcements",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Important updates and offers from IAccept Admin"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (targetScreen != "home") {
                    putExtra("subScreen", targetScreen)
                }
            }

            val uniqueNotifId = notifId?.hashCode() ?: (title + message).hashCode()
            val pendingIntent = PendingIntent.getActivity(
                context,
                Math.abs(uniqueNotifId % 10000),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val actionText = when (targetScreen.lowercase()) {
                "subscription" -> "Opens SUBSCRIPTION Screen ➔"
                "referral" -> "Opens REFERRAL Screen ➔"
                else -> "Opens HOME Screen ➔"
            }

            val displayTitle = if (title.trim().startsWith("⚡")) title else "⚡ $title"

            val customView = RemoteViews(context.packageName, R.layout.notification_admin_custom).apply {
                setTextViewText(R.id.notif_app_name, "iAccept Driver")
                setTextViewText(R.id.notif_time, "• Just now")
                setTextViewText(R.id.notif_title, displayTitle)
                setTextViewText(R.id.notif_body, message)
                setTextViewText(R.id.notif_action_text, actionText)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.app_logo)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(Math.abs(uniqueNotifId), notification)
        } catch (e: Exception) {
            // Fail silently
        }
    }

    private fun updateSubStatus(profile: ProfileRow?) {
        if (profile != null && profile.cloudSubUntil != null) {
            try {
                // High-precision date parsing for Supabase
                val rawDate = profile.cloudSubUntil!!
                val isoString = if (!rawDate.contains("T")) {
                    rawDate.replace(" ", "T")
                } else rawDate
                
                // Remove potential timezone offset part if it's not standard Instant
                val cleanIso = isoString.split("+")[0].split("Z")[0]
                val finalIso = if (cleanIso.endsWith("Z")) cleanIso else "${cleanIso}Z"
                
                val expiry = try { 
                    Instant.parse(finalIso) 
                } catch (e: Exception) {
                    // Fallback for simple date
                    Instant.parse("${finalIso.take(10)}T23:59:59Z")
                }
                
                applySubExpiry(expiry)
            } catch (e: Exception) {
                fallbackLocalSubCheck()
            }
        } else {
            fallbackLocalSubCheck()
        }
    }

    suspend fun signUp(user: UserProfile): AuthResult<ProfileRow> {
        return try {
            val emailToUse = if (user.gmail.trim().contains("@")) {
                user.gmail.trim().lowercase()
            } else if (user.phone.trim().contains("@")) {
                user.phone.trim().lowercase()
            } else {
                "u${user.phone.filter { it.isDigit() }}@iaccept.com"
            }
            
            Log.d("SupabaseManager", "SIGNUP START: $emailToUse")

            client.auth.signUpWith(Email) {
                this.email = emailToUse
                this.password = user.password
            }
            
            delay(5000)
            val newUser = client.auth.currentUserOrNull() 
            if (newUser == null) return AuthResult.Error("Signup succeeded but session failed. Please Login.")
            
            // 1. Prepare Full Profile Data
            val referralCode = "IA" + (1000..9999).random().toString() + user.username.filter { it.isLetter() }.take(4).uppercase().ifEmpty { "USER" }
            val newProfile = ProfileRow(
                id = newUser.id,
                username = user.username,
                phone = user.phone,
                gmail = emailToUse,
                homeAddress = user.homeAddress,
                cloudSubUntil = Clock.System.now().toString(),
                cloudReferralCode = referralCode,
                vehicleType = user.vehicleType
            )

            // 2. Forced Upsert (The Ground Truth)
            client.postgrest["profiles"].upsert(newProfile)

            // 3. Register Referral after upsert
            if (user.referredBy.isNotBlank()) {
                try {
                    val code = user.referredBy.trim().uppercase()
                    Log.d("SupabaseManager", "REFERRAL CHECK: Searching for code $code...")
                    
                    val referrer = client.postgrest["profiles"].select {
                        filter { eq("referral_code", code) }
                    }.decodeSingleOrNull<ProfileRow>()

                    if (referrer != null) {
                        Log.d("SupabaseManager", "REFERRER FOUND: ${referrer.id}")
                        val refRecord = buildJsonObject {
                            put("referrer_id", referrer.id)
                            put("referred_user_id", newUser.id)
                            put("status", "signed_up")
                        }
                        client.postgrest["referrals"].insert(refRecord)
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseManager", "Referral Registration failed", e)
                }
            }
            
            _profileFlow.value = newProfile
            updateSubStatus(newProfile)
            AuthResult.Success(newProfile)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Signup Exception", e)
            AuthResult.Error(e.localizedMessage ?: "Network Error")
        }
    }

    suspend fun login(loginId: String, pass: String): AuthResult<ProfileRow> {
        return try {
            val cleanId = loginId.trim().lowercase()
            val email = if (cleanId.contains("@")) cleanId else "u${cleanId.filter { it.isDigit() }}@iaccept.com"
            
            Log.d("SupabaseManager", "LOGIN: Authenticating $email...")
            client.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            
            val user = client.auth.currentUserOrNull() ?: return AuthResult.Error("Login failed: Session error")
            Log.d("SupabaseManager", "LOGIN: Session created for ${user.id}. Fetching profile...")

            val profile = client.postgrest["profiles"].select {
                filter { eq("id", user.id) }
            }.decodeSingleOrNull<ProfileRow>()
            
            if (profile == null) {
                Log.e("SupabaseManager", "LOGIN: Profile NOT FOUND for UID ${user.id}")
                return AuthResult.Error("Profile not found in VPS.")
            }
            
            Log.d("SupabaseManager", "LOGIN: Profile Fetched -> Name=${profile.username} Code=${profile.cloudReferralCode}")
            
            _profileFlow.value = profile
            updateSubStatus(profile)
            AuthResult.Success(profile)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Login error: ${e.localizedMessage}")
            AuthResult.Error(e.localizedMessage ?: "Invalid Credentials")
        }
    }

    suspend fun updateProfile(username: String, phone: String, homeAddress: String, vehicleType: String? = null): Result<Boolean> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return Result.failure(Exception("Not logged in"))
            Log.d("SupabaseManager", "UPDATING PROFILE: User=${user.id} Name=$username Phone=$phone Addr=$homeAddress Vehicle=$vehicleType")
            
            val updates = buildJsonObject {
                put("username", username)
                put("phone", phone)
                put("home_address", homeAddress)
                vehicleType?.let { put("vehicle_type", it) }
            }
            
            client.postgrest["profiles"].update(updates) {
                filter { eq("id", user.id) }
            }
            // Update the local timestamp for the 30-day check
            if (vehicleType != null) {
                val updatesTs = buildJsonObject {
                    put("vehicle_updated_at", Clock.System.now().toString())
                }
                client.postgrest["profiles"].update(updatesTs) {
                    filter { eq("id", user.id) }
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Profile Update Failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateLocation(lat: Double, lng: Double): Result<Boolean> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return Result.failure(Exception("Not logged in"))
            Log.d("SupabaseManager", "UPDATING LOCATION: Lat=$lat Lng=$lng")
            
            val updates = buildJsonObject {
                put("live_lat", lat)
                put("live_lng", lng)
            }
            
            client.postgrest["profiles"].update(updates) {
                filter { eq("id", user.id) }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Location Update Failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getActivePlans(vehicleType: String? = null): List<Plan> {
        return try {
            client.postgrest["plans"].select {
                filter { 
                    eq("is_active", true) 
                    vehicleType?.let { eq("vehicle_type", it) }
                }
            }.decodeList<Plan>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun applyReferralCode(code: String): Boolean {
        return try {
            val user = client.auth.currentUserOrNull() ?: return false
            Log.d("SupabaseManager", "APPLYING REFERRAL: Code=$code User=${user.id}")
            
            // 1. Find the referrer
            val referrer = client.postgrest["profiles"].select {
                filter { eq("referral_code", code.trim().uppercase()) }
            }.decodeSingleOrNull<ProfileRow>()

            if (referrer != null && referrer.id != user.id) {
                // 2. Insert into referrals table (Trigger on backend will add the days)
                val refRecord = buildJsonObject {
                    put("referrer_id", referrer.id)
                    put("referred_user_id", user.id)
                    put("status", "signed_up")
                }
                client.postgrest["referrals"].insert(refRecord)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Referral Apply Failed", e)
            false
        }
    }

    suspend fun getSystemSettings(): Map<String, String> {
        return try {
            val list = client.postgrest["system_settings"].select().decodeList<SystemSettings>()
            list.associate { it.key to it.value }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun createPaymentRequest(plan: Plan): Result<Boolean> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return Result.failure(Exception("Not logged in"))
            val request = PaymentRequest(
                userId = user.id,
                planId = plan.id,
                amountPaid = plan.price
            )
            client.postgrest["payments"].insert(request)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyReferrals(): List<ReferralInfo> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return emptyList()
            // Joined selection to get referred user's full profile
            val response = client.postgrest["referrals"].select(Columns.raw("*, referred_profile:referred_user_id(*)")) {
                filter { eq("referrer_id", user.id) }
            }
            Log.d("SupabaseManager", "Referral Raw: ${response.data}")
            response.decodeList<ReferralInfo>()
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Referral fetch error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getReferralRewards(): List<ReferralReward> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return emptyList()
            client.postgrest["referral_rewards"].select {
                filter { eq("user_id", user.id) }
            }.decodeList<ReferralReward>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createRazorpayOrder(plan: Plan): String? {
        return try {
            val user = client.auth.currentUserOrNull() ?: return null
            val response = httpClient.post("$apiBaseUrl/create-order") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("userId", user.id)
                    put("planId", plan.id)
                    put("amount", plan.price)
                })
            }
            val body = response.bodyAsText()
            Log.d("SupabaseManager", "Order Created: $body")
            
            // Extract order_id from JSON string
            val json = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
            json["id"]?.toString()?.replace("\"", "")
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Order Creation Failed", e)
            null
        }
    }

    suspend fun refreshProfile(): Result<ProfileRow> {
        return try {
            if (client.auth.currentUserOrNull() == null) {
                try {
                    client.auth.retrieveUserForCurrentSession()
                } catch (e: Exception) { }
            }

            val user = client.auth.currentUserOrNull()
            val profile = if (user != null) {
                client.postgrest["profiles"].select {
                    filter { eq("id", user.id) }
                }.decodeSingleOrNull<ProfileRow>()
            } else null
            
            if (profile != null) {
                _profileFlow.value = profile
                updateSubStatus(profile)
                fetchLatestNotification()
                Result.success(profile)
            } else {
                fallbackLocalSubCheck()
                Result.failure(Exception("Offline or session expired"))
            }
        } catch (e: Exception) {
            fallbackLocalSubCheck()
            Result.failure(e)
        }
    }

    suspend fun fetchLatestNotification() {
        try {
            val list = client.postgrest["admin_notifications"].select {
                order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(1)
            }.decodeList<AdminNotification>()

            if (list.isNotEmpty()) {
                val notif = list[0]
                val currentUserId = client.auth.currentUserOrNull()?.id
                val notifId = notif.id ?: "${notif.title}_${notif.message}"

                if (!seenNotifIds.contains(notifId)) {
                    val targetUserId = notif.userId
                    if (targetUserId.isNullOrBlank() || targetUserId == "null" || targetUserId == "NULL" || targetUserId == currentUserId) {
                        seenNotifIds.add(notifId)
                        latestAnnouncement.value = notif
                        showAdminSystemNotification(notif.title, notif.message, notif.targetScreen ?: "home", notifId)
                    }
                }
            }
        } catch (e: Exception) {
            // Fail silently
        }
    }

    suspend fun checkForUpdate(): AppUpdate? {
        return try {
            val list = client.postgrest["app_updates"].select {
                order("version_code", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(1)
            }.decodeList<AppUpdate>()

            if (list.isNotEmpty()) {
                val latest = list[0]
                val currentVersionCode = com.tellmeindia.iaccept.BuildConfig.VERSION_CODE
                if (latest.versionCode > currentVersionCode) {
                    latest
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
