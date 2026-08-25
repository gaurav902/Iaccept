package com.tellmeindia.iaccept.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tellmeindia.iaccept.data.UserProfile

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
    
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignup) "Create Account" else "Welcome Back",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSignup) "Join the elite captains" else "Login to start accepting",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        if (isSignup) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Person, null) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number or Gmail") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.AccountBox, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        
        if (isSignup) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = gmail,
                onValueChange = { gmail = it },
                label = { Text("Gmail (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Email, null) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = homeAddress,
                onValueChange = { homeAddress = it },
                label = { Text("Home Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Home, null) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = referralCode,
                onValueChange = { referralCode = it },
                label = { Text("Referral Code (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.ConfirmationNumber, null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 6 chars)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { 
                if (username.isBlank() && isSignup) return@Button
                if (phone.isBlank() || password.length < 6) return@Button
                
                // Creating a profile object for the app state
                val user = UserProfile(
                    phone = phone.trim(),
                    username = username.trim(),
                    gmail = gmail.trim(),
                    password = password,
                    homeAddress = homeAddress.trim(),
                    referredBy = referralCode.trim(),
                    isLoggedIn = true
                )
                onAuthAction(user, isSignup)
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(if (isSignup) "SIGN UP" else "SIGN IN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isSignup = !isSignup }) {
            Text(
                if (isSignup) "Already have an account? Login" 
                else "Don't have an account? Create one",
                fontWeight = FontWeight.Medium
            )
        }
    }
}
