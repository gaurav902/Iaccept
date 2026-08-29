package com.tellmeindia.iaccept.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellmeindia.iaccept.MainActivity
import com.tellmeindia.iaccept.data.Plan
import com.tellmeindia.iaccept.data.SupabaseManager
import com.tellmeindia.iaccept.data.UserProfile
import com.tellmeindia.iaccept.ui.theme.*
import com.tellmeindia.iaccept.ui.components.PremiumCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    supabaseManager: SupabaseManager,
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var settings by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPlan by remember { mutableStateOf<Plan?>(null) }

    val isSubscribed by supabaseManager.subscriptionActive.collectAsState()

    LaunchedEffect(Unit) {
        plans = supabaseManager.getActivePlans(userProfile.vehicleType)
        settings = supabaseManager.getSystemSettings()
        isLoading = false
    }

    Scaffold(
        containerColor = NeonBackground,
        topBar = {
            TopAppBar(
                title = { Text("Elite Subscription", fontWeight = FontWeight.Black, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, White10)
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isSubscribed) NeonGreen.copy(alpha = 0.1f) else NeonBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(if (isSubscribed) Icons.Default.CheckCircle else Icons.Default.Payment, null, tint = if (isSubscribed) NeonGreen else NeonBlue, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(if (isSubscribed) "Premium Active" else "Elite Plan Inactive", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (isSubscribed) {
                            Text("Valid till ${userProfile.subscriptionUntil.take(10)}", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            Text("Auto-accept & Advanced filters", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Text("Plans for ${userProfile.vehicleType.replaceFirstChar { it.uppercase() }}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonBlue)
                }
            } else {
                plans.forEach { plan ->
                    PlanItemPremium(
                        plan, 
                        isSelected = selectedPlan?.id == plan.id,
                        onPayClick = {
                            scope.launch {
                                val userId = userProfile.cloudId.ifBlank { userProfile.phone }
                                val planId = plan.id.toString()
                                val amount = plan.price
                                
                                if (userId.isNotBlank() && planId.isNotBlank()) {
                                    val payUrl = "https://iaccept.tellmeindia.com/pay?user_id=$userId&plan_id=$planId&amount=$amount"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payUrl))
                                    context.startActivity(intent)
                                }
                            }
                        }
                    ) {
                        selectedPlan = plan
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun PlanItemPremium(plan: Plan, isSelected: Boolean, onPayClick: () -> Unit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) NeonBlue.copy(alpha = 0.1f) else NeonSurface),
        border = BorderStroke(1.dp, if (isSelected) NeonBlue else White10)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (plan.days > 7) NeonPurple.copy(alpha = 0.1f) else NeonBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(if (plan.days > 7) Icons.Default.Star else Icons.Default.Payment, null, tint = if (plan.days > 7) NeonPurple else NeonBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(plan.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                    Text("${plan.days} Days Access", fontSize = 12.sp, color = Color.Gray)
                }
                Text("₹${plan.price}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            }
            
            if (isSelected) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPayClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text("BUY NOW", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}


