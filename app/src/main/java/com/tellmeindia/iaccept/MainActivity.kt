package com.tellmeindia.iaccept

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tellmeindia.iaccept.R
import com.tellmeindia.iaccept.data.*
import com.tellmeindia.iaccept.service.RideAccessibilityService
import com.tellmeindia.iaccept.ui.*
import com.tellmeindia.iaccept.ui.components.PremiumCard
import com.tellmeindia.iaccept.ui.theme.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val snackbarHostState = remember { SnackbarHostState() }
            val supabaseManager = viewModel.getSupabaseManager()
            val isSubscribed by viewModel.isSubscribed.collectAsState(initial = false)
            val scope = rememberCoroutineScope()
            
            val localProfile by viewModel.localProfile.collectAsState(initial = null)
            val disclosureAccepted by viewModel.disclosureAccepted.collectAsState(initial = true)
            val themeMode by viewModel.themeMode.collectAsState(initial = 0)
            
            val isDark = when (themeMode) {
                1 -> false // Light
                2 -> true  // Dark
                else -> isSystemInDarkTheme() // Auto
            }

            var isAuthLoading by remember { mutableStateOf(false) }
            var showReferralDialog by remember { mutableStateOf(false) }

            IacceptTheme(darkTheme = isDark) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (!disclosureAccepted) {
                            DisclosureScreen(onAccept = { viewModel.updateDisclosureAccepted(true) })
                        } else {
                            if (localProfile?.isLoggedIn == true) {
                                if (showReferralDialog) {
                                    ReferralOnboardingDialog(
                                        onDismiss = { showReferralDialog = false },
                                        onSubmit = { code ->
                                            scope.launch {
                                                val success = supabaseManager.applyReferralCode(code)
                                                if (success) {
                                                    snackbarHostState.showSnackbar("Referral Applied! Extra days added.")
                                                } else {
                                                    snackbarHostState.showSnackbar("Invalid or expired code.")
                                                }
                                                showReferralDialog = false
                                                viewModel.refreshProfile()
                                            }
                                        }
                                    )
                                }
                                MainScreen(viewModel, localProfile!!, this@MainActivity, isSubscribed, isDark, snackbarHostState)
                            } else {
                                AuthScreen(isLoading = isAuthLoading, onAuthAction = { authUser, isSignup ->
                                    isAuthLoading = true
                                    lifecycleScope.launch {
                                        try {
                                            val result = if (isSignup) {
                                                supabaseManager.signUp(authUser)
                                            } else {
                                                supabaseManager.login(authUser.phone, authUser.password)
                                            }

                                            when (result) {
                                                is AuthResult.Success -> {
                                                    val data = result.data
                                                    val finalUser = UserProfile(
                                                        localId = 1,
                                                        cloudId = data.id,
                                                        phone = data.phone ?: authUser.phone,
                                                        username = data.username ?: authUser.username,
                                                        gmail = data.gmail ?: authUser.gmail,
                                                        password = authUser.password,
                                                        homeAddress = data.homeAddress ?: authUser.homeAddress,
                                                        isLoggedIn = true,
                                                        referralCode = data.cloudReferralCode ?: "",
                                                        subscriptionUntil = data.cloudSubUntil ?: "",
                                                        vehicleType = data.vehicleType ?: authUser.vehicleType,
                                                        vehicleTypeUpdatedAt = data.vehicleUpdatedAt ?: ""
                                                    )
                                                    withContext(Dispatchers.IO) {
                                                        val db = IAcceptDatabase.getDatabase(this@MainActivity)
                                                        db.dao().clearProfile()
                                                        db.dao().saveProfile(finalUser)
                                                    }
                                                    if (isSignup) showReferralDialog = true
                                                    viewModel.refreshProfile()
                                                    isAuthLoading = false
                                                }
                                                is AuthResult.Error -> {
                                                    isAuthLoading = false
                                                    snackbarHostState.showSnackbar(result.message)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            isAuthLoading = false
                                            snackbarHostState.showSnackbar("Error: ${e.localizedMessage}")
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    userProfile: UserProfile,
    activity: MainActivity,
    isSubscribed: Boolean,
    isDark: Boolean,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    
    var isNotificationAccessGranted by remember { mutableStateOf(false) }
    var isOverlayPermissionGranted by remember { mutableStateOf(false) }
    var isBatteryOptimized by remember { mutableStateOf(true) }
    var isAccessibilityGranted by remember { mutableStateOf(false) }
    var isLocationGranted by remember { mutableStateOf(false) }
    var isPostNotifGranted by remember { mutableStateOf(false) }

    fun checkAllPermissions() {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        isAccessibilityGranted = RideAccessibilityService.isServiceRunning || am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC).any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        isNotificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        isLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        isOverlayPermissionGranted = Settings.canDrawOverlays(context)
        isPostNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> 
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAllPermissions()
                viewModel.refreshProfile() 
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> checkAllPermissions() }

    var showAutoAcceptWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        while(true) {
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) checkAllPermissions()
            delay(5000) 
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val themeMode by viewModel.themeMode.collectAsState(initial = 0)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refreshProfile() },
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), CircleShape)
                    ) {
                        val uiState by viewModel.uiState.collectAsState()
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonBlue)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { viewModel.updateThemeMode((themeMode + 1) % 3) },
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = when(themeMode) { 1 -> Icons.Default.LightMode; 2 -> Icons.Default.DarkMode; else -> Icons.Default.AutoMode },
                            contentDescription = "Theme", tint = NeonBlue, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                NavigationBarItem(selected = uiState.currentTab == 0 && uiState.subScreen == null, onClick = { viewModel.setTab(0) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home", fontWeight = FontWeight.Bold) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonBlue, unselectedIconColor = Color.Gray, indicatorColor = NeonBlue.copy(alpha = 0.1f)))
                NavigationBarItem(selected = uiState.currentTab == 2 || uiState.subScreen != null, onClick = { viewModel.setTab(2) }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile", fontWeight = FontWeight.Bold) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonGreen, unselectedIconColor = Color.Gray, indicatorColor = NeonGreen.copy(alpha = 0.1f)))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (uiState.subScreen != null) {
                when (uiState.subScreen) {
                    "subscription" -> SubscriptionScreen(viewModel.getSupabaseManager(), userProfile, onBack = { viewModel.setSubScreen(null) })
                    "referral" -> ReferralScreen(viewModel.getSupabaseManager(), userProfile, onBack = { viewModel.setSubScreen(null) })
                }
            } else {
                when (uiState.currentTab) {
                    0 -> DashboardTab(
                        viewModel, isNotificationAccessGranted, isOverlayPermissionGranted, isAccessibilityGranted, isBatteryOptimized,
                        isLocationGranted, isPostNotifGranted, isSubscribed, isDark, activity, context,
                        onShowWarning = { showAutoAcceptWarning = true },
                        onMessage = { msg -> if (msg == "NAV_SUBSCRIPTION") viewModel.setSubScreen("subscription") else scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    2 -> ProfileTab(userProfile, viewModel, snackbarHostState, { viewModel.setSubScreen("subscription") }, { viewModel.setSubScreen("referral") })
                }
            }
        }
    }

    if (showAutoAcceptWarning) {
        AlertDialog(
            onDismissRequest = { showAutoAcceptWarning = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Caution Required", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Auto-acceptance may be detected by app security systems.", color = Color.Gray) },
            confirmButton = { Button(onClick = { viewModel.updateAutoAccept(true); showAutoAcceptWarning = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("I UNDERSTAND") } },
            dismissButton = { TextButton(onClick = { showAutoAcceptWarning = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun DashboardTab(
    viewModel: MainViewModel,
    isNotificationAccessGranted: Boolean,
    isOverlayPermissionGranted: Boolean,
    isAccessibilityGranted: Boolean,
    isBatteryOptimized: Boolean,
    isLocationGranted: Boolean,
    isPostNotifGranted: Boolean,
    isSubscribed: Boolean,
    isDark: Boolean,
    activity: MainActivity,
    context: Context,
    onShowWarning: () -> Unit,
    onMessage: (String) -> Unit
) {
    var showUpiDisclaimer by remember { mutableStateOf(false) }
    
    val automationMaster by viewModel.automationMaster.collectAsState(initial = true)
    val upiSafeMode by viewModel.upiSafeMode.collectAsState(initial = false)
    val rapidoEnabled by viewModel.rapidoEnabled.collectAsState(initial = true)
    val uberEnabled by viewModel.uberEnabled.collectAsState(initial = true)
    val minFare by viewModel.minFare.collectAsState(initial = 0)
    val maxDistance by viewModel.maxDistance.collectAsState(initial = 100.0)
    val parcelFilter by viewModel.parcelFilter.collectAsState(initial = true)
    val autoAccept by viewModel.autoAcceptEnabled.collectAsState(initial = false)
    
    val userProfile by viewModel.localProfile.collectAsState(initial = null)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Hello, ${userProfile?.username?.ifBlank { "Captain" } ?: "Captain"} 👋", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Text("Drive more. Earn more.", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        // Subscription Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSubscribed) NeonGreen.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(if (isSubscribed) Icons.Default.Verified else Icons.Default.Error, null, tint = if (isSubscribed) NeonGreen else Color.Gray, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isSubscribed) "Premium Active" else "No Active Plan", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text(if (isSubscribed) "Valid till ${userProfile?.subscriptionUntil?.take(10)}" else "Unlock elite features", fontSize = 12.sp, color = Color.Gray)
                }
                if (!isSubscribed) {
                    Button(onClick = { onMessage("NAV_SUBSCRIPTION") }, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue), shape = RoundedCornerShape(12.dp)) {
                        Text("View Plans", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // System Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            val alpha by rememberInfiniteTransition(label = "").animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), "")
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(if (isAccessibilityGranted && isNotificationAccessGranted) NeonGreen else Color.Red))
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (isAccessibilityGranted && isNotificationAccessGranted) "LIVE SCANNER ACTIVE" else "SERVICE OFFLINE", color = if (isAccessibilityGranted && isNotificationAccessGranted) NeonGreen else Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Automation Toggles
        Text("ELITE SCANNER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonBlue, letterSpacing = 1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ConfigRowLite(Icons.Default.Bolt, "Service Active", automationMaster, { viewModel.updateMainScanner(it) }, NeonBlue)
                LiteDivider()
                ConfigRowLite(Icons.Default.Lock, "UPI Safe Mode", upiSafeMode, { if (it) showUpiDisclaimer = true else viewModel.updateUpiSafeMode(false) }, NeonPurple)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Platform Controls
        Text("PLATFORMS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonBlue, letterSpacing = 1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ConfigRowLite(Icons.AutoMirrored.Filled.DirectionsBike, "Scan Rapido", rapidoEnabled, { viewModel.updateRapidoEnabled(it) }, NeonPurple)
                LiteDivider()
                ConfigRowLite(Icons.Default.LocalTaxi, "Scan Uber", uberEnabled, { viewModel.updateUberEnabled(it) }, NeonBlue)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Ride Filters
        Text("RIDE FILTERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonBlue, letterSpacing = 1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var fareText by remember(minFare) { mutableStateOf(minFare.toString()) }
                OutlinedTextField(
                    value = fareText,
                    onValueChange = { fareText = it },
                    label = { Text("Minimum Fare (Max ₹100,000)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, null, tint = NeonBlue) },
                    trailingIcon = {
                        IconButton(onClick = {
                            fareText.toIntOrNull()?.let { v -> if (v in 0..100000) viewModel.updateMinFare(v) }
                        }) { Icon(Icons.Default.CheckCircle, "Set", tint = NeonGreen) }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                NonLinearSlider("Max Total Distance", maxDistance.toFloat(), 10000f, 100f, isDark) { viewModel.updateMaxDistance(it.toDouble()) }
                Spacer(modifier = Modifier.height(16.dp))
                ConfigRowLite(Icons.Default.ShoppingBag, "Accept Parcels", parcelFilter, { viewModel.updateParcelFilter(it) }, NeonBlue)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Auto-Accept
        Text("INSTANT AUTO-ACCEPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Red, letterSpacing = 1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isSubscribed) {
                    ConfigRowLite(Icons.Default.Bolt, "Auto-Accept", autoAccept, { if (it) onShowWarning() else viewModel.updateAutoAccept(false) }, Color.Red)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onMessage("NAV_SUBSCRIPTION") }) {
                        Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Auto-Accept (Locked)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                            Text("Unlock with Elite Plan", fontSize = 11.sp, color = NeonBlue)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Setup Steps
        if (!(isNotificationAccessGranted && isOverlayPermissionGranted && isAccessibilityGranted && isLocationGranted && !isBatteryOptimized && isPostNotifGranted)) {
            Text("REQUIRED SETUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonOrange, letterSpacing = 1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NeonOrange.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SetupStepLite("Location Access", isLocationGranted) { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }) }
                    SetupStepLite("Notification Reader", isNotificationAccessGranted) { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    SetupStepLite("Send Notifications", isPostNotifGranted) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101) }
                    SetupStepLite("Overlay Display", isOverlayPermissionGranted) { activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                    SetupStepLite("Screen Interaction", isAccessibilityGranted) { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    SetupStepLite("Battery Unrestricted", !isBatteryOptimized) { activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Optimization Guide for Chinese Phones
        Text("PHONE OPTIMIZATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonPurple, letterSpacing = 1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeonPurple.copy(alpha = 0.05f)),
            border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("For Vivo, Oppo, Realme & Xiaomi users:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = NeonPurple)
                Spacer(modifier = Modifier.height(8.dp))
                OptimizationStep("1. Lock App", "Open Recent Apps and swipe DOWN on IAccept to Lock it. (Crucial)")
                OptimizationStep("2. Auto-Start", "Allow IAccept to 'Auto-start' in your Phone Settings.")
                OptimizationStep("3. No Battery Limit", "Ensure 'Battery Unrestricted' is ready in the Setup section above.")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Footer()
    }

    if (showUpiDisclaimer) {
        AlertDialog(
            onDismissRequest = { showUpiDisclaimer = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("UPI Safe Mode Disclaimer", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Enabling this will AUTOMATICALLY DISABLE all Automation and Screen Clicking features for security.", color = Color.Gray) },
            confirmButton = { Button(onClick = { viewModel.updateUpiSafeMode(true); showUpiDisclaimer = false }, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) { Text("ENABLE") } },
            dismissButton = { TextButton(onClick = { showUpiDisclaimer = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
fun ProfileTab(
    user: UserProfile,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSub: () -> Unit,
    onOpenRef: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editUsername by remember { mutableStateOf(user.username) }
    var editPhone by remember { mutableStateOf(user.phone) }
    var editHomeAddress by remember { mutableStateOf(user.homeAddress) }
    var editVehicleType by remember { mutableStateOf(user.vehicleType) }

    LaunchedEffect(user) { 
        editUsername = user.username; editPhone = user.phone; editHomeAddress = user.homeAddress; editVehicleType = user.vehicleType
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
            Row {
                IconButton(onClick = { 
                    if (isEditing) {
                        scope.launch { 
                            val res = viewModel.getSupabaseManager().updateProfile(editUsername, editPhone, editHomeAddress, editVehicleType)
                            if (res.isSuccess) { isEditing = false; viewModel.refreshProfile(); snackbarHostState.showSnackbar("Profile Updated") }
                        }
                    } else isEditing = true
                }) { Icon(if (isEditing) Icons.Default.CheckCircle else Icons.Default.Edit, null, tint = if (isEditing) NeonGreen else NeonBlue) }
                IconButton(onClick = { viewModel.refreshProfile() }) { Icon(Icons.Default.Refresh, null, tint = NeonBlue) }
            }
        }
        
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(NeonBlue, NeonPurple))), contentAlignment = Alignment.Center) {
                    Text(user.username.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    if (isEditing) OutlinedTextField(value = editUsername, onValueChange = { editUsername = it }, label = { Text("Name") }, shape = RoundedCornerShape(12.dp))
                    else {
                        Text(user.username.ifBlank { "Captain" }, fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                        Text(user.gmail, fontSize = 12.sp, color = Color.Gray)
                        Row { LiteBadge("ACTIVE", NeonGreen); Spacer(modifier = Modifier.width(6.dp)); LiteBadge("VERIFIED", NeonBlue) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("APPEARANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonBlue, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                val themeMode by viewModel.themeMode.collectAsState(initial = 0)
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == 1, onClick = { viewModel.updateThemeMode(1) }, label = { Text("Light") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = themeMode == 2, onClick = { viewModel.updateThemeMode(2) }, label = { Text("Dark") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = themeMode == 0, onClick = { viewModel.updateThemeMode(0) }, label = { Text("Auto") }, modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ProfileMenuRowLite(Icons.Default.CardMembership, "Subscription", onOpenSub)
                    LiteDivider()
                    ProfileMenuRowLite(Icons.Default.People, "Referrals", onOpenRef)
                    LiteDivider()
                    val context = LocalContext.current
                    ProfileMenuRowLite(Icons.Default.SupportAgent, "Contact Support") {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/918764994185"))
                        context.startActivity(intent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("DETAILS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonBlue, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isEditing) {
                        OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = editHomeAddress, onValueChange = { editHomeAddress = it }, label = { Text("Home") }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LiteInfoItem("Phone", user.phone); LiteDivider(); LiteInfoItem("Home", user.homeAddress.ifBlank { "Not set" })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("VEHICLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonBlue, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val lastUpd = user.vehicleTypeUpdatedAt
                    val canChange = remember(lastUpd) { try { val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(lastUpd.take(10)); (System.currentTimeMillis() - (date?.time ?: 0)) / (1000 * 60 * 60 * 24) >= 30 } catch (e: Exception) { true } }
                    if (isEditing) {
                        if (canChange) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = editVehicleType == "bike", onClick = { editVehicleType = "bike" }, label = { Text("Bike") })
                                FilterChip(selected = editVehicleType == "auto", onClick = { editVehicleType = "auto" }, label = { Text("Auto") })
                                FilterChip(selected = editVehicleType == "car", onClick = { editVehicleType = "car" }, label = { Text("Car") })
                            }
                        } else Text("Vehicle Locked for 30 days", color = Color.Red, fontSize = 12.sp)
                    } else LiteInfoItem("Type", user.vehicleType.uppercase())
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            TextButton(onClick = { scope.launch(Dispatchers.IO) { val db = IAcceptDatabase.getDatabase(viewModel.getApplication()); db.dao().clearProfile() } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("Sign out", color = Color.Red.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable fun ConfigRowLite(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = tint))
    }
}

@Composable fun SetupStepLite(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(if (granted) NeonGreen.copy(alpha = 0.1f) else NeonBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(if (granted) Icons.Default.Check else Icons.Default.Info, null, tint = if (granted) NeonGreen else NeonBlue, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (granted) MaterialTheme.colorScheme.onSurface else Color.Gray, modifier = Modifier.weight(1f))
        if (!granted) TextButton(onClick = onClick) { Text("Fix", fontSize = 12.sp, color = NeonBlue) }
        else Text("Ready", fontSize = 11.sp, color = NeonGreen)
    }
}

@Composable fun LiteBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp), border = BorderStroke(0.5.dp, color.copy(alpha = 0.2f))) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = color, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable fun ProfileMenuRowLite(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = NeonBlue, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
    }
}

@Composable fun LiteInfoItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable fun LiteDivider() { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 0.5.dp) }

@Composable fun Footer() {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Automate your grind. Maximize your time.", fontSize = 12.sp, color = Color.Gray)
        Row { Text("Powered by ", fontSize = 11.sp, color = Color.Gray); Text("DRHACKER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = NeonBlue) }
    }
}

@Composable fun NonLinearSlider(label: String, value: Float, max: Float, mid: Float, isDark: Boolean, onValueChange: (Float) -> Unit) {
    var showManualInput by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(value.toInt().toString()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showManualInput = !showManualInput }) {
                Text(if (label.contains("Fare")) "₹${value.toInt()}" else "${value.toInt()} km", fontSize = 18.sp, fontWeight = FontWeight.Black, color = NeonBlue)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = NeonBlue)
            }
        }
        if (showManualInput) {
            OutlinedTextField(
                value = textValue, 
                onValueChange = { textValue = it; it.toFloatOrNull()?.let { v -> if (v in 0f..max) onValueChange(v) } }, 
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                shape = RoundedCornerShape(12.dp), 
                singleLine = true, 
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue, 
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), 
                    focusedTextColor = MaterialTheme.colorScheme.onSurface, 
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        } else {
            val pos = if (value <= mid) (value / mid) * 0.5f else 0.5f + ((value - mid) / (max - mid)) * 0.5f
            Slider(
                value = pos.coerceIn(0f, 1f), 
                onValueChange = { p -> 
                    val nv = if (p <= 0.5f) (p / 0.5f) * mid else mid + ((p - 0.5f) / 0.5f) * (max - mid)
                    val step = if (nv < 10) 1f else if (nv < 100) 5f else if (nv < 1000) 50f else 500f
                    onValueChange((Math.round(nv / step) * step).toFloat()) 
                }, 
                valueRange = 0f..1f, 
                colors = SliderDefaults.colors(thumbColor = NeonBlue, activeTrackColor = NeonBlue, inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            )
        }
    }
}

@Composable
fun OptimizationStep(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        val isDark = isSystemInDarkTheme()
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = if(isDark) Color.White else LiteTextPrimary)
        Text(desc, fontSize = 11.sp, color = Color.Gray)
    }
}
