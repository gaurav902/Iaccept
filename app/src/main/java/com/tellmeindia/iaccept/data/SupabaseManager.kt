package com.tellmeindia.iaccept.data

import android.content.Context
import android.util.Log
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
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import io.github.jan.supabase.gotrue.providers.builtin.Email

class SupabaseManager(private val context: Context) {

    private val supabaseUrl = "http://72.60.223.74:8000" 
    private val supabaseKey = "sb_publishable_lCmGJsNQx8eg1S-YORUyOp_gKfglsNH"

    val client: SupabaseClient = createSupabaseClient(supabaseUrl, supabaseKey) {
        httpEngine = OkHttp.create()
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    private val _subscriptionActive = MutableStateFlow(false)
    val subscriptionActive: StateFlow<Boolean> = _subscriptionActive

    private val _profileFlow = MutableStateFlow<ProfileRow?>(null)
    val profileFlow: StateFlow<ProfileRow?> get() = _profileFlow

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            monitorProfileRealtime()
        }
    }

    private suspend fun monitorProfileRealtime() {
        // Wait for auth to be ready
        while (client.auth.currentUserOrNull() == null) {
            delay(3000)
        }
        
        val user = client.auth.currentUserOrNull() ?: return
        Log.d("SupabaseManager", "REALTIME: Starting monitor for ${user.id}")
        
        // 1. Initial Force Refresh
        refreshProfile()

        // 2. Realtime Listener
        val channel = client.realtime.channel("profile_sync")
        val changeFlow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "profiles"
        }

        changeFlow
            .onEach { action ->
                if (action.record["id"]?.toString()?.replace("\"", "") == user.id) {
                    refreshProfile()
                    Log.d("SupabaseManager", "REALTIME: Profile Update Received & Refreshed")
                }
            }.launchIn(scope)

        channel.subscribe()
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
                
                val now = Clock.System.now()
                _subscriptionActive.value = expiry > now
                Log.d("SupabaseManager", "Sub Status: ${expiry > now} | Until: $expiry")
            } catch (e: Exception) {
                Log.e("SupabaseManager", "CRITICAL Parse failed: ${profile.cloudSubUntil} | ${e.message}")
                _subscriptionActive.value = false
            }
        } else {
            _subscriptionActive.value = false
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
                cloudReferralCode = referralCode
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
                        val refRecord = mapOf(
                            "referrer_id" to referrer.id,
                            "referred_user_id" to newUser.id,
                            "status" to "signed_up"
                        )
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

    suspend fun updateProfile(username: String, phone: String, homeAddress: String): Result<Boolean> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return Result.failure(Exception("Not logged in"))
            Log.d("SupabaseManager", "UPDATING PROFILE: User=${user.id} Name=$username Phone=$phone Addr=$homeAddress")
            
            // Fix: Map directly to DB columns to avoid SerialName mismatches
            val updates = mapOf(
                "username" to username,
                "phone" to phone,
                "home_address" to homeAddress
            )
            
            client.postgrest["profiles"].update(updates) {
                filter { eq("id", user.id) }
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
            
            val updates = mapOf(
                "live_lat" to lat,
                "live_lng" to lng
            )
            
            client.postgrest["profiles"].update(updates) {
                filter { eq("id", user.id) }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Location Update Failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getActivePlans(): List<Plan> {
        return try {
            client.postgrest["plans"].select {
                filter { eq("is_active", true) }
            }.decodeList<Plan>()
        } catch (e: Exception) {
            emptyList()
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

    suspend fun getCloudRideHistory(): List<CloudRideHistory> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return emptyList()
            client.postgrest["ride_history"].select {
                filter { eq("user_id", user.id) }
            }.decodeList<CloudRideHistory>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveRideToCloud(fare: Int, pickup: String, drop: String, dist: Double, timestamp: Long): Result<Boolean> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return Result.failure(Exception("Not logged in"))
            val ride = CloudRideHistory(
                userId = user.id,
                fare = fare,
                pickupAddr = pickup,
                dropAddr = drop,
                totalDist = dist,
                timestamp = timestamp
            )
            client.postgrest["ride_history"].insert(ride)
            Log.d("SupabaseManager", "Ride Saved to Cloud: $fare at $pickup")
            Result.success(true)
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Cloud Ride Sync Failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun refreshProfile(): Result<ProfileRow> {
        return try {
            val user = client.auth.currentUserOrNull() ?: return Result.failure(Exception("Not logged in"))
            val profile = client.postgrest["profiles"].select {
                filter { eq("id", user.id) }
            }.decodeSingleOrNull<ProfileRow>()
            
            if (profile != null) {
                _profileFlow.value = profile
                updateSubStatus(profile)
                Result.success(profile)
            } else {
                Result.failure(Exception("Profile not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
