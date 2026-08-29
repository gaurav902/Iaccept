package com.tellmeindia.iaccept.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellmeindia.iaccept.data.UserProfile
import com.tellmeindia.iaccept.ui.theme.*
import com.tellmeindia.iaccept.ui.components.PremiumCard
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.tellmeindia.iaccept.R

@Composable
fun AuthScreen(
    isLoading: Boolean,
    onAuthAction: (UserProfile, Boolean) -> Unit
) {
    var isSignup by remember { mutableStateOf(true) } // Default to true as it's a new system
    
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var gmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var homeAddress by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("bike") }
    
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonBackground)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // Header Logo & Title
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isSignup) "Create Account" else "Welcome Back",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        
        Text(
            text = if (isSignup) "Join the elite captains" else "Login to start accepting",
            fontSize = 14.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        if (isSignup) {
            PremiumCard(title = "Profile Info") {
                AuthTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username",
                    icon = Icons.Default.Person
                )
                Spacer(modifier = Modifier.height(16.dp))
                AuthTextField(
                    value = gmail,
                    onValueChange = { gmail = it },
                    label = "Gmail",
                    icon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            PremiumCard(title = "Professional Details") {
                AuthTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Contact Number",
                    icon = Icons.Default.Phone,
                    keyboardType = KeyboardType.Phone
                )
                Spacer(modifier = Modifier.height(16.dp))
                AuthTextField(
                    value = homeAddress,
                    onValueChange = { homeAddress = it },
                    label = "Home Area (City/Area)",
                    icon = Icons.Default.LocationOn
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "VEHICLE CATEGORY", 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = NeonBlue, 
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleChoiceChip(
                        selected = vehicleType == "bike",
                        label = "Bike",
                        icon = Icons.AutoMirrored.Filled.DirectionsBike,
                        modifier = Modifier.weight(1f),
                        onClick = { vehicleType = "bike" }
                    )
                    VehicleChoiceChip(
                        selected = vehicleType == "auto",
                        label = "Auto",
                        icon = Icons.Default.Agriculture, // Rickshaw-like fallback
                        modifier = Modifier.weight(1f),
                        onClick = { vehicleType = "auto" }
                    )
                    VehicleChoiceChip(
                        selected = vehicleType == "car",
                        label = "Car",
                        icon = Icons.Default.DirectionsCar,
                        modifier = Modifier.weight(1f),
                        onClick = { vehicleType = "car" }
                    )
                }
            }
        } else {
            // Login specific fields
            PremiumCard(title = "Credentials") {
                AuthTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Phone or Gmail",
                    icon = Icons.Default.AccountBox
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PremiumCard(title = "Security") {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 6 chars)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = White10,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonBlue
                ),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = NeonBlue) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, 
                            null,
                            tint = Color.Gray
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { 
                if (isSignup && (username.isBlank() || phone.isBlank() || gmail.isBlank() || password.length < 6)) return@Button
                if (!isSignup && (phone.isBlank() || password.length < 6)) return@Button
                
                val user = UserProfile(
                    phone = phone.trim(),
                    username = username.trim(),
                    gmail = gmail.trim(),
                    password = password,
                    homeAddress = homeAddress.trim(),
                    referredBy = "", // Handled in post-signup popup
                    vehicleType = vehicleType,
                    isLoggedIn = true
                )
                onAuthAction(user, isSignup)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && if (isSignup) {
                username.isNotBlank() && phone.isNotBlank() && gmail.isNotBlank() && password.length >= 6
            } else {
                phone.isNotBlank() && password.length >= 6
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonBlue,
                contentColor = Color.White
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    if (isSignup) "CREATE ACCOUNT" else "LOG IN", 
                    fontWeight = FontWeight.Black, 
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isSignup = !isSignup }) {
            Text(
                if (isSignup) "Already have an account? Login" 
                else "Don't have an account? Create one",
                color = NeonBlue,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun ReferralOnboardingDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(NeonBlue.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CardGiftcard, null, tint = NeonBlue, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Elite Referral Benefit", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
        },
        text = {
            Column {
                Text(
                    "Got a friend's code? Enter it now to help them earn bonus subscription days!",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Referral Code") },
                    placeholder = { Text("e.g. IA1234") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = White10,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "It helps build our community and rewards the Captain who invited you.",
                    color = NeonBlue,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (code.isNotBlank()) onSubmit(code) },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CLAIM BENEFIT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now", color = Color.Gray)
            }
        }
    )
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        leadingIcon = { Icon(icon, null, tint = NeonBlue) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonBlue,
            unfocusedBorderColor = White10,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NeonBlue
        )
    )
}

@Composable
fun VehicleChoiceChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NeonBlue.copy(alpha = 0.2f) else NeonSurface)
            .border(
                1.dp, 
                if (selected) NeonBlue else White10, 
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon, 
                null, 
                tint = if (selected) NeonBlue else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                color = if (selected) Color.White else Color.Gray,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
