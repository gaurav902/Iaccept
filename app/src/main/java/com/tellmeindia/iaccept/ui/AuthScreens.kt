package com.tellmeindia.iaccept.ui

import androidx.compose.foundation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.tellmeindia.iaccept.R
import com.tellmeindia.iaccept.data.UserProfile
import com.tellmeindia.iaccept.ui.theme.*
import com.tellmeindia.iaccept.ui.components.PremiumCard

@Composable
fun AuthScreen(
    isLoading: Boolean,
    onAuthAction: (UserProfile, Boolean) -> Unit
) {
    var isSignup by remember { mutableStateOf(false) } // Default to Login for better UX
    val context = LocalContext.current
    
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var gmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var homeAddress by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("bike") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonBackground)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // Elite Header
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Brush.linearGradient(listOf(NeonBlue, NeonPurple)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(if(isSignup) Icons.Default.Person else Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isSignup) "Join Elite" else "Captain Login",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = if (isSignup) "Start your automated journey" else "Resume your elite grind",
            fontSize = 13.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(40.dp))

        if (isSignup) {
            // Registration Fields
            AuthTextField(username, { username = it; errorMessage = null }, "Full Name", Icons.Default.Person)
            Spacer(modifier = Modifier.height(12.dp))
            AuthTextField(gmail, { gmail = it; errorMessage = null }, "Gmail Address", Icons.Default.Email, KeyboardType.Email)
            Spacer(modifier = Modifier.height(12.dp))
            AuthTextField(phone, { if (it.all { char -> char.isDigit() }) { phone = it; errorMessage = null } }, "Phone Number (Digits only)", Icons.Default.Phone, KeyboardType.Phone)
            Spacer(modifier = Modifier.height(12.dp))
            AuthTextField(homeAddress, { homeAddress = it; errorMessage = null }, "Home City/Area", Icons.Default.Home)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text("VEHICLE CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonBlue, modifier = Modifier.align(Alignment.Start).padding(start = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleChoiceChip(vehicleType == "bike", "Bike", Icons.Default.Star, Modifier.weight(1f)) { vehicleType = "bike" }
                VehicleChoiceChip(vehicleType == "auto", "Auto", Icons.Default.Build, Modifier.weight(1f)) { vehicleType = "auto" }
                VehicleChoiceChip(vehicleType == "car", "Car", Icons.Default.Place, Modifier.weight(1f)) { vehicleType = "car" }
            }
        } else {
            // Login Fields
            AuthTextField(phone, { phone = it; errorMessage = null }, "Phone or Gmail", Icons.Default.Person)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Security Field (Common)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonBlue,
                unfocusedBorderColor = White10,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = NeonBlue) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Info else Icons.Default.Clear, null, tint = Color.Gray)
                }
            }
        )

        if (!isSignup) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://iaccept.tellmeindia.com/forgot-password"))
                    context.startActivity(intent)
                }) {
                    Text("Forgot Password?", color = NeonPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error message box
        if (errorMessage != null) {
            Surface(
                color = Color(0xFFFF5252).copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(errorMessage!!, color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = { 
                if (isSignup) {
                    if (username.trim().isBlank()) {
                        errorMessage = "Please enter your Full Name"
                        return@Button
                    }
                    val cleanEmail = gmail.trim().lowercase()
                    val emailRegex = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
                    if (cleanEmail.isBlank() || !emailRegex.matches(cleanEmail)) {
                        errorMessage = "Please enter a valid Email address (e.g. name@gmail.com)"
                        return@Button
                    }
                    val cleanPhone = phone.trim()
                    if (cleanPhone.isBlank() || !cleanPhone.all { it.isDigit() } || cleanPhone.length < 10) {
                        errorMessage = "Please enter a valid 10-digit Phone number (numbers only)"
                        return@Button
                    }
                    if (homeAddress.trim().isBlank()) {
                        errorMessage = "Please enter your Home City or Area"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                } else {
                    if (phone.trim().isBlank()) {
                        errorMessage = "Please enter your Phone or Gmail"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                }

                errorMessage = null
                val user = UserProfile(
                    phone = phone.trim(),
                    username = username.trim(),
                    gmail = gmail.trim(),
                    password = password,
                    homeAddress = homeAddress.trim(),
                    vehicleType = vehicleType,
                    isLoggedIn = true
                )
                onAuthAction(user, isSignup)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(if (isSignup) "CREATE ACCOUNT" else "ACCESS DASHBOARD", fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isSignup) "Already a member?" else "New Captain?", color = Color.Gray, fontSize = 13.sp)
            TextButton(onClick = { isSignup = !isSignup; errorMessage = null }) {
                Text(if (isSignup) "Log In" else "Sign Up", color = NeonBlue, fontWeight = FontWeight.Bold)
            }
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
    val isDark = isSystemInDarkTheme()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if(isDark) NeonSurface else Color.White,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(NeonBlue.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, null, tint = NeonBlue, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Elite Referral Benefit", color = if(isDark) Color.White else Color.Black, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
        },
        text = {
            Column {
                Text(
                    "Got a friend's code? Enter it now to earn bonus subscription days!",
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        focusedTextColor = if(isDark) Color.White else Color.Black,
                        unfocusedTextColor = if(isDark) Color.White else Color.Black
                    ),
                    singleLine = true
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
            unfocusedTextColor = Color.White
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
            .border(1.dp, if (selected) NeonBlue else White10, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) NeonBlue else Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = if (selected) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
