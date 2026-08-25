package com.tellmeindia.iaccept

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.activity.ComponentActivity
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.viewinterop.AndroidView
import com.tellmeindia.iaccept.data.SupabaseManager
import com.tellmeindia.iaccept.data.ProfileRow
import com.tellmeindia.iaccept.data.AuthResult
import com.tellmeindia.iaccept.ui.PlanSelectionScreen
import androidx.compose.ui.draw.blur
import android.webkit.WebView
import android.webkit.WebViewClient
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tellmeindia.iaccept.R
import com.tellmeindia.iaccept.data.IAcceptDatabase
import com.tellmeindia.iaccept.data.PreferenceManager
import com.tellmeindia.iaccept.data.UserProfile
import com.tellmeindia.iaccept.data.RideRecord
import com.tellmeindia.iaccept.service.RideAccessibilityService
import com.tellmeindia.iaccept.ui.AuthScreen
import com.tellmeindia.iaccept.ui.theme.*
import com.tellmeindia.iaccept.ui.components.PremiumCard
import com.tellmeindia.iaccept.ui.SubscriptionScreen
import com.tellmeindia.iaccept.ui.ReferralScreen
import com.tellmeindia.iaccept.ui.CloudRideHistoryScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

import androidx.lifecycle.viewmodel.compose.viewModel
import com.tellmeindia.iaccept.ui.MainViewModel

import com.tellmeindia.iaccept.ui.DisclosureScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel: MainViewModel = viewModel()
            val snackbarHostState = remember { SnackbarHostState() }
            val supabaseManager = viewModel.getSupabaseManager()
            val isSubscribed by viewModel.isSubscribed.collectAsState(initial = false)
            val cloudProfile by viewModel.cloudProfile.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            
            val localProfile by viewModel.localProfile.collectAsState(initial = null)
            val disclosureAccepted by viewModel.disclosureAccepted.collectAsState(initial = true)
            
            var showWelcome by remember { mutableStateOf(true) }
            var isAuthLoading by remember { mutableStateOf(false) }

            IacceptTheme {
                Scaffold(
                    containerColor = NeonBackground,
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        color = NeonBackground
                    ) {
                        if (showWelcome) {
                            WelcomeScreen(onFinish = { showWelcome = false })
                        } else if (!disclosureAccepted) {
                            DisclosureScreen(onAccept = { viewModel.updateDisclosureAccepted(true) })
                        } else {
                            if (localProfile?.isLoggedIn == true) {
                                MainScreen(viewModel, localProfile!!, this@MainActivity, isSubscribed, snackbarHostState)
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
                                                        phone = data.phone ?: authUser.phone,
                                                        username = data.username ?: authUser.username,
                                                        gmail = data.gmail ?: authUser.gmail,
                                                        password = authUser.password,
                                                        homeAddress = data.homeAddress ?: authUser.homeAddress,
                                                        isLoggedIn = true,
                                                        referralCode = data.cloudReferralCode ?: "",
                                                        subscriptionUntil = data.cloudSubUntil ?: ""
                                                    )
                                                    withContext(Dispatchers.IO) {
                                                        val db = IAcceptDatabase.getDatabase(this@MainActivity)
                                                        db.dao().clearAllRides()
                                                        db.dao().saveProfile(finalUser)
                                                    }
                                                    viewModel.refreshProfile()
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
fun WelcomeScreen(onFinish: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (startAnimation) 1.1f else 0.9f, animationSpec = tween(1200))
    val alpha by animateFloatAsState(targetValue = if (startAnimation) 1f else 0f, animationSpec = tween(800))

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000)
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize().background(NeonBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(scale).alpha(alpha)) {
            Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(32.dp)))
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "IAccept", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
            Text(text = "ELITE CAPTAIN TOOLS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonBlue, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    userProfile: UserProfile,
    activity: MainActivity,
    isSubscribed: Boolean,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val uiState by viewModel.uiState.collectAsState()
    
    val autoAccept by viewModel.autoAcceptEnabled.collectAsState(initial = false)
    val autoAcceptMode by viewModel.autoAcceptMode.collectAsState(initial = 0)
    val lastNotification by viewModel.lastNotification.collectAsState(initial = "None")
    
    val automationMaster by viewModel.automationMaster.collectAsState(initial = true)
    val upiSafeMode by viewModel.upiSafeMode.collectAsState(initial = false)
    val screenInteraction by viewModel.screenInteraction.collectAsState(initial = true)
    
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
        isPostNotifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checkAllPermissions() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        isLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        checkAllPermissions()
    }

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
        containerColor = NeonBackground,
        bottomBar = {
            NavigationBar(containerColor = NeonSurface, tonalElevation = 0.dp, modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                NavigationBarItem(selected = uiState.currentTab == 0 && uiState.subScreen == null, onClick = { viewModel.setTab(0) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home", fontWeight = FontWeight.Bold) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonBlue, unselectedIconColor = Color.Gray, indicatorColor = NeonBlue.copy(alpha = 0.1f)))
                NavigationBarItem(selected = uiState.currentTab == 1 && uiState.subScreen == null, onClick = { viewModel.setTab(1) }, icon = { Icon(Icons.Default.History, null) }, label = { Text("History", fontWeight = FontWeight.Bold) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonPurple, unselectedIconColor = Color.Gray, indicatorColor = NeonPurple.copy(alpha = 0.1f)))
                NavigationBarItem(selected = uiState.currentTab == 2 || uiState.subScreen != null, onClick = { viewModel.setTab(2) }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile", fontWeight = FontWeight.Bold) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = NeonGreen, unselectedIconColor = Color.Gray, indicatorColor = NeonGreen.copy(alpha = 0.1f)))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (uiState.subScreen != null) {
                when (uiState.subScreen) {
                    "subscription" -> SubscriptionScreen(viewModel.getSupabaseManager(), userProfile, onBack = { viewModel.setSubScreen(null) })
                    "referral" -> ReferralScreen(viewModel.getSupabaseManager(), userProfile, onBack = { viewModel.setSubScreen(null) })
                    "ride_history" -> { LaunchedEffect(Unit) { viewModel.setTab(1) } }
                }
            } else {
                when (uiState.currentTab) {
                    0 -> DashboardTab(
                        viewModel, isNotificationAccessGranted, isOverlayPermissionGranted, 
                        isAccessibilityGranted, isBatteryOptimized, isLocationGranted, isPostNotifGranted, 
                        lastNotification, autoAccept, autoAcceptMode, automationMaster, upiSafeMode, 
                        screenInteraction, scope, context, activity, isSubscribed = isSubscribed,
                        userProfile = userProfile,
                        onShowWarning = { showAutoAcceptWarning = true },
                        onMessage = { msg -> if (msg == "NAV_SUBSCRIPTION") viewModel.setSubScreen("subscription") else scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    1 -> HistoryTab(viewModel, userProfile)
                    2 -> ProfileTab(userProfile, viewModel, snackbarHostState, { viewModel.setSubScreen("subscription") }, { viewModel.setSubScreen("referral") }, { viewModel.setSubScreen("ride_history") })
                }
            }
        }
    }

    if (showAutoAcceptWarning) {
        AlertDialog(
            onDismissRequest = { showAutoAcceptWarning = false },
            containerColor = NeonSurface,
            title = { Text("Caution Required", color = Color.White) },
            text = { Text("Auto-acceptance may be detected by app security systems. Use responsibly.", color = Color.Gray) },
            confirmButton = {
                Button(onClick = { viewModel.updateAutoAccept(true); showAutoAcceptWarning = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RoundedCornerShape(12.dp)) { Text("I UNDERSTAND", fontWeight = FontWeight.Bold) }
            },
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
    lastNotification: String,
    autoAccept: Boolean,
    autoAcceptMode: Int,
    automationMaster: Boolean,
    upiSafeMode: Boolean,
    screenInteraction: Boolean,
    scope: CoroutineScope,
    context: Context,
    activity: MainActivity,
    isSubscribed: Boolean,
    userProfile: UserProfile,
    onShowWarning: () -> Unit,
    onMessage: (String) -> Unit
) {
    val minFare by viewModel.minFare.collectAsState(initial = 0)
    val maxDistance by viewModel.maxDistance.collectAsState(initial = 100.0)
    var showUpiDisclaimer by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Hello, ${userProfile.username.ifBlank { "Captain" }} 👋", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("Drive more. Earn more.", fontSize = 14.sp, color = Color.Gray)
            }
            IconButton(onClick = { viewModel.refreshProfile() }) {
                Icon(Icons.Default.Refresh, null, tint = NeonBlue)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        if (!(isNotificationAccessGranted && isOverlayPermissionGranted && isAccessibilityGranted && !isBatteryOptimized && isLocationGranted && isPostNotifGranted)) {
            PremiumCard(title = "Required Setup") {
                SetupStep(1, "Location Access", isLocationGranted) { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }) }
                SetupStep(2, "Notification Reader", isNotificationAccessGranted) { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                SetupStep(3, "Send Notifications", isPostNotifGranted) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101) }
                SetupStep(4, "Overlay Display", isOverlayPermissionGranted) { activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                SetupStep(5, "Screen Interaction", isAccessibilityGranted) { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                SetupStep(6, "Battery Unrestricted", !isBatteryOptimized) { activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PremiumCard(title = "Subscription Status") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (isSubscribed) "Active Plan" else "Subscription Inactive", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isSubscribed) NeonGreen else Color.Gray)
                    if (isSubscribed) Text("Valid till ${userProfile.subscriptionUntil.take(10)}", fontSize = 11.sp, color = Color.Gray)
                }
                if (!isSubscribed) Button(onClick = { onMessage("NAV_SUBSCRIPTION") }, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) { Text("View Plans", fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PremiumCard(title = "Automation Control") {
            ConfigRow(Icons.Default.PlayArrow, "Master Automation", if (automationMaster) "🟢 Monitoring & Clicking Active" else "🔴 Stopped", automationMaster, { viewModel.updateAutomationMaster(it) }, NeonBlue)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = White10)
            ConfigRow(Icons.Default.Lock, "UPI Safe Mode", "Secures device for payments", upiSafeMode, { 
                if (it) showUpiDisclaimer = true
                else viewModel.updateUpiSafeMode(false)
            }, NeonBlue)
        }

        Spacer(modifier = Modifier.height(16.dp))

        PremiumCard(title = "Elite Platform Controls") {
            val isRapido by viewModel.rapidoEnabled.collectAsState(initial = true)
            val isUber by viewModel.uberEnabled.collectAsState(initial = true)

            ConfigRow(Icons.Default.DirectionsBike, "Scan Rapido", "Capture Rapido Captain rides", isRapido, { viewModel.updateRapidoEnabled(it) }, NeonPurple)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = White10)
            ConfigRow(Icons.Default.LocalTaxi, "Scan Uber", "Capture Uber Driver rides", isUber, { viewModel.updateUberEnabled(it) }, NeonBlue)
        }

        Spacer(modifier = Modifier.height(16.dp))

        PremiumCard(title = "Ride Filters") {
            ConfigRow(Icons.Default.Settings, "Service Active", "Scan incoming rides", viewModel.serviceEnabled.collectAsState(initial = false).value, { viewModel.updateServiceEnabled(it) }, NeonBlue)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = White10)
            
            NonLinearSlider("Minimum Fare", minFare.toFloat(), 1000f, 400f) { viewModel.updateMinFare(it.toInt()) }
            Spacer(modifier = Modifier.height(16.dp))
            
            NonLinearSlider("Max Total Distance", maxDistance.toFloat(), 10000f, 100f) { viewModel.updateMaxDistance(it.toDouble()) }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = White10)
            ConfigRow(Icons.Default.ShoppingBag, "Accept Parcels", "Toggle parcel matching", viewModel.parcelFilter.collectAsState(initial = false).value, { viewModel.updateParcelFilter(it) }, NeonBlue)
        }

        Spacer(modifier = Modifier.height(16.dp))

        PremiumCard(title = "Instant Auto-Accept") {
            if (isSubscribed) {
                ConfigRow(Icons.Default.Bolt, "Auto-Accept", "Instant interaction", autoAccept, { if (it) onShowWarning() else viewModel.updateAutoAccept(false) }, Color.Red)
                if (autoAccept) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = White10)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(autoAcceptMode == 0, { viewModel.updateAutoAcceptMode(0) }, { Text("Visual Only", color = Color.White) }, modifier = Modifier.weight(1f))
                        FilterChip(autoAcceptMode == 1, { viewModel.updateAutoAcceptMode(1) }, { Text("Full Auto", color = Color.White) }, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().clickable { onMessage("NAV_SUBSCRIPTION") }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Accept (Locked)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Gray)
                        Text("Unlock with Elite Plan", fontSize = 11.sp, color = NeonBlue)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PremiumCard(title = "Live Scanner", containerColor = Color.Black.copy(alpha = 0.6f)) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "alpha"
            )
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.8f, targetValue = 1.2f,
                animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "scale"
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).scale(pulseScale).alpha(pulseAlpha).clip(CircleShape).background(NeonGreen))
                Spacer(modifier = Modifier.width(12.dp))
                Text("SCANNER PULSE ACTIVE", color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(if (isAccessibilityGranted && isNotificationAccessGranted) "LIVE 🛰️" else "OFFLINE ⚠️", color = if (isAccessibilityGranted && isNotificationAccessGranted) NeonGreen else Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    lastNotification.split("\n").forEach { log ->
                        Text(log, color = NeonGreen.copy(alpha = 0.9f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (lastNotification.isBlank()) {
                        Text("Waiting for rides...", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Footer()
    }

    if (showUpiDisclaimer) {
        AlertDialog(
            onDismissRequest = { showUpiDisclaimer = false },
            containerColor = NeonSurface,
            title = { Text("UPI Safe Mode Disclaimer", color = Color.White) },
            text = { 
                Text(
                    "UPI Safe Mode is designed to protect your device during sensitive financial transactions. " +
                    "\n\nEnabling this will AUTOMATICALLY DISABLE all Automation and Screen Clicking features. This ensures no accidental interaction happens while you are on a payment screen. " +
                    "\n\nWould you like to turn it on?", 
                    color = Color.Gray
                ) 
            },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.updateUpiSafeMode(true)
                        showUpiDisclaimer = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) { Text("ENABLE", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showUpiDisclaimer = false }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
fun ProfileTab(
    user: UserProfile,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSub: () -> Unit,
    onOpenRef: () -> Unit,
    onOpenHist: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editUsername by remember { mutableStateOf(user.username) }
    var editPhone by remember { mutableStateOf(user.phone) }
    var editHomeAddress by remember { mutableStateOf(user.homeAddress) }

    LaunchedEffect(user) { editUsername = user.username; editPhone = user.phone; editHomeAddress = user.homeAddress }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditing) {
                    IconButton(onClick = { 
                        scope.launch { 
                            val res = viewModel.getSupabaseManager().updateProfile(editUsername, editPhone, editHomeAddress)
                            if (res.isSuccess) { 
                                isEditing = false
                                viewModel.refreshProfile()
                                snackbarHostState.showSnackbar("Profile Updated")
                            } else {
                                snackbarHostState.showSnackbar("Update Failed")
                            }
                        }
                    }) { Icon(Icons.Default.CheckCircle, null, tint = NeonGreen) }
                } else {
                    IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, null, tint = NeonBlue) }
                }
                IconButton(onClick = { viewModel.refreshProfile() }) { Icon(Icons.Default.Refresh, null, tint = NeonBlue) }
            }
        }
        
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = NeonSurface), border = BorderStroke(1.dp, White10)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Brush.linearGradient(listOf(NeonBlue, NeonPurple))), contentAlignment = Alignment.Center) {
                        Text(user.username.take(1).uppercase(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    if (isEditing) {
                        OutlinedTextField(value = editUsername, onValueChange = { editUsername = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("Contact Phone") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                    } else {
                        Text(user.username.ifBlank { "Captain" }, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(user.gmail, fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Surface(color = NeonGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) { Text("ACTIVE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = NeonBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) { Text("VERIFIED", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = NeonBlue, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            PremiumCard("Menu") {
                ProfileMenuRow(Icons.Default.CardMembership, "My Subscription", "Elite plan status", onOpenSub)
                ProfileMenuRow(Icons.Default.People, "Referral Program", "Earn free days", onOpenRef)
                ProfileMenuRow(Icons.Default.History, "Ride History", "Detailed logs", onOpenHist)
            }

            Spacer(modifier = Modifier.height(24.dp))
            PremiumCard("Address") {
                if (isEditing) OutlinedTextField(value = editHomeAddress, onValueChange = { editHomeAddress = it }, label = { Text("Home Area") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                else ProfileInfoRow("Home", user.homeAddress.ifBlank { "Not set" })
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { scope.launch(Dispatchers.IO) { 
                val db = IAcceptDatabase.getDatabase(viewModel.getApplication())
                db.dao().clearProfile()
                db.dao().clearAllRides() 
            } }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(modifier = Modifier.width(8.dp)); Text("SIGN OUT", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable fun HistoryTab(viewModel: MainViewModel, user: UserProfile) {
    val history by viewModel.rideHistory.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.performSync(user.gmail) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("History", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
            IconButton(onClick = { viewModel.performSync(user.gmail) }) { if (uiState.isSyncing) CircularProgressIndicator(color = NeonBlue, modifier = Modifier.size(20.dp)) else Icon(Icons.Default.Sync, null, tint = NeonBlue) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (history.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No records found.", color = Color.Gray) }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            history.forEach { ride ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = NeonSurface), border = BorderStroke(1.dp, White10)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonGreen.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Payments, null, tint = NeonGreen, modifier = Modifier.size(20.dp)) }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("₹${ride.fare}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White)
                            }
                            Text(SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ride.timestamp)), fontSize = 11.sp, color = NeonBlue, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NeonGreen))
                                Box(modifier = Modifier.width(1.dp).height(30.dp).background(White10))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(ride.pickupAddress, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(ride.dropAddress, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp)); HorizontalDivider(color = White10); Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ride.distance} km total", fontSize = 11.sp, color = Color.Gray)
                            if (ride.isSynced) Icon(Icons.Default.CloudDone, null, modifier = Modifier.size(14.dp), tint = NeonBlue)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable fun ProfileMenuRow(i: ImageVector, t: String, s: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(i, null, tint = NeonBlue, modifier = Modifier.size(24.dp).background(NeonBlue.copy(alpha = 0.1f), CircleShape).padding(4.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(t, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text(s, fontSize = 11.sp, color = Color.Gray)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.DarkGray, modifier = Modifier.size(14.dp))
    }
}

@Composable fun ProfileInfoRow(l: String, v: String) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(l, color = Color.Gray, fontSize = 14.sp); Text(v, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White) } }

@Composable fun SetupStep(n: Int, t: String, g: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(if (g) NeonGreen.copy(alpha = 0.1f) else NeonBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(if (g) Icons.Default.Check else Icons.Default.Info, null, tint = if (g) NeonGreen else NeonBlue, modifier = Modifier.size(18.dp)) }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(t, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (g) Color.White else Color.Gray)
            Text(if (g) "Ready" else "Required", fontSize = 11.sp, color = if (g) NeonGreen else Color.Red)
        }
        if (!g) Button(onClick = onClick, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonBlue), modifier = Modifier.height(34.dp)) { Text("Fix", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable fun Footer() {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Automate your grind. Maximize your time.", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Row { Text("Powered by ", fontSize = 11.sp, color = Color.Gray); Text("DRHACKER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = NeonBlue, letterSpacing = 1.sp) }
    }
}

@Composable fun NonLinearSlider(label: String, value: Float, max: Float, mid: Float, onValueChange: (Float) -> Unit) {
    var showManualInput by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(value.toInt().toString()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showManualInput = !showManualInput }) {
                Text(if (label.contains("Fare")) "₹${value.toInt()}" else "${value.toInt()} km", fontSize = 18.sp, fontWeight = FontWeight.Black, color = NeonBlue)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = NeonBlue)
            }
        }
        if (showManualInput) {
            OutlinedTextField(value = textValue, onValueChange = { textValue = it; it.toFloatOrNull()?.let { v -> if (v in 0f..max) onValueChange(v) } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonBlue, unfocusedBorderColor = White10, focusedTextColor = Color.White, unfocusedTextColor = Color.White))
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
                colors = SliderDefaults.colors(thumbColor = NeonBlue, activeTrackColor = NeonBlue, inactiveTrackColor = White10)
            )
        }
    }
}
@Composable fun ConfigRow(i: ImageVector, t: String, s: String, c: Boolean, onC: (Boolean) -> Unit, tint: Color) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(i, null, tint = tint, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) { Text(t, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White); Text(s, fontSize = 11.sp, color = Color.Gray) }; Switch(checked = c, onCheckedChange = onC, colors = SwitchDefaults.colors(checkedThumbColor = tint, checkedTrackColor = tint.copy(alpha = 0.3f))) } }
