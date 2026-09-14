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
    
    val isSubscribed = supabaseManager.subscriptionActive
    val cloudProfile = supabaseManager.profileFlow

    val minFare = preferenceManager.minFare
    val maxDistance = preferenceManager.maxDistance
    val autoAcceptEnabled = preferenceManager.autoAcceptEnabled
    val automationMaster = preferenceManager.automationMaster
    val upiSafeMode = preferenceManager.upiSafeMode
    val screenInteraction = preferenceManager.screenInteraction
    val serviceEnabled = preferenceManager.serviceEnabled
    val parcelFilter = preferenceManager.parcelFilter
    val rapidoEnabled = preferenceManager.rapidoEnabled
    val uberEnabled = preferenceManager.uberEnabled
    val disclosureAccepted = preferenceManager.disclosureAccepted
    val themeMode = preferenceManager.themeMode

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
                        cloudId = cp.id,
                        username = cp.username ?: lp.username,
                        phone = cp.phone ?: lp.phone,
                        homeAddress = cp.homeAddress ?: lp.homeAddress,
                        referralCode = cp.cloudReferralCode ?: lp.referralCode,
                        subscriptionUntil = cp.cloudSubUntil ?: lp.subscriptionUntil,
                        vehicleType = cp.vehicleType ?: lp.vehicleType,
                        vehicleTypeUpdatedAt = cp.vehicleUpdatedAt ?: lp.vehicleTypeUpdatedAt
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

    fun updateMainScanner(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.updateAutomationMaster(enabled)
            preferenceManager.updateScreenInteraction(enabled)
            preferenceManager.updateServiceEnabled(enabled)
        }
    }

    fun updateUpiSafeMode(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.updateUpiSafeMode(enabled) }
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

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch { preferenceManager.updateThemeMode(mode) }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            supabaseManager.refreshProfile()
            
            // Smart Retry: If sub is not active, retry up to 3 times with 2s delay
            // to account for Webhook server processing delay when returning from payment browser
            var retries = 0
            while (!supabaseManager.subscriptionActive.value && retries < 3) {
                delay(2000)
                supabaseManager.refreshProfile()
                retries++
            }
            
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    val latestAnnouncement = supabaseManager.latestAnnouncement
    val availableUpdate = MutableStateFlow<AppUpdate?>(null)

    fun dismissAnnouncement() {
        supabaseManager.latestAnnouncement.value = null
    }

    fun checkAppUpdate() {
        viewModelScope.launch {
            val update = supabaseManager.checkForUpdate()
            if (update != null) {
                availableUpdate.value = update
            }
        }
    }

    fun dismissUpdate() {
        availableUpdate.value = null
    }

    fun getSupabaseManager() = supabaseManager
}

data class MainUiState(
    val currentTab: Int = 0,
    val subScreen: String? = null,
    val isSyncing: Boolean = false
)
