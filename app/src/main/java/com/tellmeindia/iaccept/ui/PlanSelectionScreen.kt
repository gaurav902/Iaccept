package com.tellmeindia.iaccept.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlanSelectionScreen(onPlanSelected: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
                    ),
                    RoundedCornerShape(32.dp)
                )
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    RoundedCornerShape(32.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Star,
                null,
                tint = Color.Cyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Subscription Required",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                "Unlock all elite automation features",
                fontSize = 12.sp,
                color = Color.LightGray
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            PlanItem("DAILY", "₹50", "1 Day Access", onPlanSelected)
            PlanItem("WEEKLY", "₹300", "7 Days + Bonus", onPlanSelected)
            PlanItem("MONTHLY", "₹1000", "30 Days + VIP", onPlanSelected)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = { /* Referral Info */ }) {
                Text("Invite friends to get free days!", color = Color.Cyan, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun PlanItem(title: String, price: String, desc: String, onClick: (String) -> Unit) {
    Card(
        onClick = { onClick(title) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                Text(desc, fontSize = 10.sp, color = Color.Gray)
            }
            Text(price, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.Cyan)
        }
    }
}
