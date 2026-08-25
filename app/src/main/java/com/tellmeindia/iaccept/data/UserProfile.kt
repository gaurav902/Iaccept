package com.tellmeindia.iaccept.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val localId: Int = 1, // Fixed ID to ensure only ONE profile ever exists locally
    var phone: String = "",
    var username: String = "",
    var gmail: String = "",
    var password: String = "",
    var homeAddress: String = "",
    var referredBy: String = "",
    var isLoggedIn: Boolean = false,
    var referralCode: String = "",
    var subscriptionUntil: String = ""
)
