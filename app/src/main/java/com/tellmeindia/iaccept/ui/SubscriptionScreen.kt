package com.tellmeindia.iaccept.ui

import android.content.Intent
import android.net.Uri
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
    var showInstructions by remember { mutableStateOf(false) }

    val isSubscribed by supabaseManager.subscriptionActive.collectAsState()

    LaunchedEffect(Unit) {
        plans = supabaseManager.getActivePlans()
        settings = supabaseManager.getSystemSettings()
        isLoading = false
    }

    Scaffold(
        containerColor = NeonBackground,
        topBar = {
            TopAppBar(
                title = { Text("Subscription", fontWeight = FontWeight.Black, color = Color.White) },
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
                colors = CardDefaults.cardColors(containerColor = NeonSurface),
                border = BorderStroke(1.dp, White10)
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isSubscribed) NeonGreen.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(if (isSubscribed) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = if (isSubscribed) NeonGreen else Color.Gray, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(if (isSubscribed) "Current Status: Active" else "Status: Inactive", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                        if (isSubscribed) {
                            Text("Valid till ${userProfile.subscriptionUntil.take(10)}", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            Text("Purchase a plan to unlock elite features", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Text("Available Plans", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonBlue)
                }
            } else {
                plans.forEach { plan ->
                    PlanItemPremium(plan, isSelected = selectedPlan?.id == plan.id) {
                        selectedPlan = plan
                        showInstructions = true
                    }
                }

                if (showInstructions && selectedPlan != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    PremiumCard(title = "How it works") {
                        StepItem("1", "Select a plan from the list above")
                        StepItem("2", "Send payment to the details below")
                        StepItem("3", "Share screenshot on WhatsApp/Telegram")
                        StepItem("4", "Admin will activate your plan instantly")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    PremiumCard(title = "Payment Details") {
                        val inst = settings["payment_instructions"] ?: "Contact Admin for UPI details."
                        Text(inst, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SocialButton("WhatsApp", Color(0xFF25D366), Modifier.weight(1f)) {
                                val wa = settings["whatsapp_contact"] ?: ""
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa")))
                            }
                            SocialButton("Telegram", Color(0xFF0088CC), Modifier.weight(1f)) {
                                val tg = settings["telegram_contact"] ?: ""
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$tg")))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    supabaseManager.createPaymentRequest(selectedPlan!!)
                                    showInstructions = false
                                    selectedPlan = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                        ) {
                            Text("I HAVE SENT THE PAYMENT", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun PlanItemPremium(plan: Plan, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) NeonBlue.copy(alpha = 0.1f) else NeonSurface),
        border = BorderStroke(1.dp, if (isSelected) NeonBlue else White10)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
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
    }
}

@Composable
fun SocialButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f), contentColor = color),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun StepItem(num: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(NeonBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(num, color = NeonBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
