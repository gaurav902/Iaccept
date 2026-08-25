package com.tellmeindia.iaccept.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tellmeindia.iaccept.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceManager = PreferenceManager(application)
    private val supabaseManager = SupabaseManager(application)
    private val db = IAcceptDatabase.getDatabase(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val localProfile = db.dao().getProfile()
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val rideHistory = localProfile.map { it?.gmail ?: "" }.flatMapLatest { gmail ->
        if (gmail.isBlank()) flowOf(emptyList())
        else db.dao().getRidesForUser(gmail)
    }
    val isSubscribed = supabaseManager.subscriptionActive
    val cloudProfile = supabaseManager.profileFlow

    val minFare = preferenceManager.minFare
    val maxDistance = preferenceManager.maxDistance
    val autoAcceptEnabled = preferenceManager.autoAcceptEnabled
    val autoAcceptMode = preferenceManager.autoAcceptMode
    val lastNotification = preferenceManager.lastNotification
    val automationMaster = preferenceManager.automationMaster
    val upiSafeMode = preferenceManager.upiSafeMode
    val screenInteraction = preferenceManager.screenInteraction
    val serviceEnabled = preferenceManager.serviceEnabled
    val parcelFilter = preferenceManager.parcelFilter
    val rapidoEnabled = preferenceManager.rapidoEnabled
    val uberEnabled = preferenceManager.uberEnabled
    val disclosureAccepted = preferenceManager.disclosureAccepted

    init {
        viewModelScope.launch {
            while (true) {
                supabaseManager.refreshProfile()
                delay(45000)
            }
        }

        viewModelScope.launch {
            combine(cloudProfile, localProfile) { cp, lp -> cp to lp }.collect { (cp, lp) ->
                if (cp != null && lp != null) {
                    val updated = lp.copy(
                        username = cp.username ?: lp.username,
                        phone = cp.phone ?: lp.phone,
                        homeAddress = cp.homeAddress ?: lp.homeAddress,
                        referralCode = cp.cloudReferralCode ?: lp.referralCode,
                        subscriptionUntil = cp.cloudSubUntil ?: lp.subscriptionUntil
                    )
                    if (updated != lp) {
                        withContext(Dispatchers.IO) { db.dao().saveProfile(updated) }
                    }
                }
            }
        }
    }

    fun setTab(tab: Int) {
        _uiState.update { it.copy(currentTab = tab, subScreen = null) }
    }

    fun setSubScreen(screen: String?) {
        _uiState.update { it.copy(subScreen = screen) }
    }

    fun updateMinFare(fare: Int) {
        viewModelScope.launch { preferenceManager.updateMinFare(fare) }
    }

    fun updateMaxDistance(distance: Double) {
        viewModelScope.launch { preferenceManager.updateMaxDistance(distance) }
    }

    fun updateAutoAccept(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateAutoAccept(enabled) }
    }

    fun updateAutoAcceptMode(mode: Int) {
        viewModelScope.launch { preferenceManager.updateAutoAcceptMode(mode) }
    }

    fun updateAutomationMaster(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateAutomationMaster(enabled) }
    }

    fun updateUpiSafeMode(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateUpiSafeMode(enabled) }
    }

    fun updateScreenInteraction(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateScreenInteraction(enabled) }
    }

    fun updateServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateServiceEnabled(enabled) }
    }

    fun updateParcelFilter(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateParcelFilter(enabled) }
    }

    fun updateRapidoEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateRapidoEnabled(enabled) }
    }

    fun updateUberEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateUberEnabled(enabled) }
    }

    fun updateDisclosureAccepted(accepted: Boolean) {
        viewModelScope.launch { preferenceManager.updateDisclosureAccepted(accepted) }
    }

    fun refreshProfile() {
        viewModelScope.launch { supabaseManager.refreshProfile() }
    }

    fun performSync(gmail: String) {
        if (gmail.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val cloudRides = supabaseManager.getCloudRideHistory()
                val localRides = db.dao().getRidesForUserSync(gmail)
                cloudRides.forEach { cr ->
                    val isRideExisting = localRides.any { Math.abs(it.timestamp - cr.timestamp) < 5000 }
                    if (!isRideExisting) {
                        db.dao().insertRide(
                            RideRecord(0, gmail, cr.fare, cr.totalDist ?: 0.0, 0.0, 0.0, 
                                cr.pickupAddr, cr.dropAddr, "Cloud Sync", "", cr.timestamp, true, true)
                        )
                    }
                }
            } catch (e: Exception) {
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun getSupabaseManager() = supabaseManager
}

data class MainUiState(
    val currentTab: Int = 0,
    val subScreen: String? = null,
    val isSyncing: Boolean = false
)
