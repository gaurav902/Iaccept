package com.tellmeindia.iaccept.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellmeindia.iaccept.data.ReferralInfo
import com.tellmeindia.iaccept.data.ReferralReward
import com.tellmeindia.iaccept.data.SupabaseManager
import com.tellmeindia.iaccept.data.UserProfile
import com.tellmeindia.iaccept.ui.components.PremiumCard
import com.tellmeindia.iaccept.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(
    supabaseManager: SupabaseManager,
    userProfile: UserProfile,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var referrals by remember { mutableStateOf<List<ReferralInfo>>(emptyList()) }
    var rewards by remember { mutableStateOf<List<ReferralReward>>(emptyList()) }
    var settings by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        referrals = supabaseManager.getMyReferrals()
        rewards = supabaseManager.getReferralRewards()
        settings = supabaseManager.getSystemSettings()
        isLoading = false
    }

    Scaffold(
        containerColor = NeonBackground,
        topBar = {
            TopAppBar(
                title = { Text("Referral Program", fontWeight = FontWeight.Black, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isLoading = true
                        scope.launch {
                            referrals = supabaseManager.getMyReferrals()
                            rewards = supabaseManager.getReferralRewards()
                            settings = supabaseManager.getSystemSettings()
                            isLoading = false
                        }
                    }) {
                        Icon(Icons.Default.Sync, null, tint = NeonBlue)
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
            // My Code Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NeonSurface),
                border = BorderStroke(1.dp, White10)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your Referral Code", fontSize = 14.sp, color = Color.Gray)
                    Text(userProfile.referralCode, fontSize = 32.sp, fontWeight = FontWeight.Black, color = NeonPurple)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val context = LocalContext.current
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { clipboardManager.setText(AnnotatedString(userProfile.referralCode)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, White10)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp), tint = NeonBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy", color = Color.White)
                        }
                        Button(
                            onClick = { 
                                val shareText = "Hey! Use my referral code ${userProfile.referralCode} to join IAccept and earn free elite automation days!"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Referral Code"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share", color = Color.White)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonBlue)
                }
            } else {
                // Progress Logic
                val required = settings["required_paid_referrals"]?.toIntOrNull() ?: 5
                val rewardDays = settings["referral_reward_days"]?.toIntOrNull() ?: 7
                
                // Optimized Paid Count (Sign up + Active Sub)
                val paidCount = referrals.count { ref ->
                    val isPaidStatus = ref.status.lowercase() == "paid"
                    val hasActiveSub = ref.referredProfile?.cloudSubUntil?.let { until ->
                        try {
                            val iso = until.replace(" ", "T").split("+")[0].split("Z")[0] + "Z"
                            kotlinx.datetime.Instant.parse(iso) > kotlinx.datetime.Clock.System.now()
                        } catch(e: Exception) { false }
                    } ?: false
                    isPaidStatus || hasActiveSub
                }

                PremiumCard(title = "Your Progress") {
                    LinearProgressIndicator(
                        progress = { 
                            val p = (paidCount.toFloat() / required)
                            p.coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = NeonPurple,
                        trackColor = White10
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$paidCount / $required Paid Referrals",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    val remaining = (required - paidCount).coerceAtLeast(0)
                    if (remaining > 0) {
                        Text(
                            "$remaining more to earn $rewardDays days free!",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            "Goal reached! Reward granted ✅",
                            fontSize = 12.sp,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Referred Users", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (referrals.isEmpty()) {
                    Text("No referrals yet.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    referrals.forEach { ref ->
                        ReferralItemPremium(ref)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Reward History", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (rewards.isEmpty()) {
                    Text("No rewards earned yet.", color = Color.Gray, fontSize = 14.sp)
                } else {
                    rewards.forEach { reward ->
                        RewardItemPremium(reward)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ReferralItemPremium(info: ReferralInfo) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeonSurface),
        border = BorderStroke(1.dp, White10)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(NeonPurple.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Text(
                    text = (info.referredProfile?.username ?: "C").take(1).uppercase(),
                    color = NeonPurple,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.referredProfile?.username ?: "Elite Captain", 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
                Text(
                    text = "ID: ${info.referredProfile?.phone?.takeLast(4) ?: "..."}", 
                    fontSize = 11.sp, 
                    color = Color.Gray
                )
            }
            
            // Smart Status logic
            val hasPaid = info.status.lowercase() == "paid" || info.referredProfile?.cloudSubUntil?.let { until ->
                try {
                    val iso = until.replace(" ", "T").split("+")[0].split("Z")[0] + "Z"
                    kotlinx.datetime.Instant.parse(iso) > kotlinx.datetime.Clock.System.now()
                } catch(e: Exception) { false }
            } == true

            Surface(
                color = if (hasPaid) NeonGreen.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (hasPaid) "PAID ✅" else "JOINED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasPaid) NeonGreen else Color.Gray
                )
            }
        }
    }
}

@Composable
fun RewardItemPremium(reward: ReferralReward) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeonSurface),
        border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${reward.rewardDays} Days Subscription", fontWeight = FontWeight.Black, color = NeonOrange)
            Text("Reason: Reached ${reward.requiredPaidReferrals} Paid Referrals", fontSize = 12.sp, color = Color.White)
            Text("Date: ${reward.createdAt.take(10)}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}
