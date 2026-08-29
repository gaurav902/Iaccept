package com.tellmeindia.iaccept.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRow(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("gmail") val gmail: String? = null,
    @SerialName("home_address") val homeAddress: String? = null,
    @SerialName("subscription_until") val cloudSubUntil: String? = null,
    @SerialName("referral_code") val cloudReferralCode: String? = null,
    @SerialName("paid_referrals_count") val paidReferralsCount: Int = 0,
    @SerialName("vehicle_type") val vehicleType: String? = "bike",
    @SerialName("vehicle_updated_at") val vehicleUpdatedAt: String? = null,
    @SerialName("live_lat") val liveLat: Double? = 0.0,
    @SerialName("live_lng") val liveLng: Double? = 0.0
)

@Serializable
data class ProfileUpdate(
    @SerialName("username") val username: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("home_address") val homeAddress: String? = null,
    @SerialName("vehicle_type") val vehicleType: String? = null,
    @SerialName("live_lat") val liveLat: Double? = null,
    @SerialName("live_lng") val liveLng: Double? = null
)

@Serializable
data class Plan(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("days") val days: Int,
    @SerialName("price") val price: Int,
    @SerialName("vehicle_type") val vehicleType: String = "bike",
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class PaymentRequest(
    @SerialName("user_id") val userId: String,
    @SerialName("plan_id") val planId: Int,
    @SerialName("amount_paid") val amountPaid: Int,
    @SerialName("status") val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ReferralInfo(
    @SerialName("id") val id: Int,
    @SerialName("referred_user_id") val referredUserId: String,
    @SerialName("status") val status: String = "signed_up",
    @SerialName("created_at") val createdAt: String,
    @SerialName("referred_profile") val referredProfile: ProfileRow? = null
)

@Serializable
data class ReferralReward(
    @SerialName("id") val id: Int,
    @SerialName("required_paid_referrals") val requiredPaidReferrals: Int,
    @SerialName("reward_days") val rewardDays: Int,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SubscriptionHistory(
    @SerialName("id") val id: Int,
    @SerialName("source") val source: String,
    @SerialName("days_added") val daysAdded: Int,
    @SerialName("subscription_start") val start: String,
    @SerialName("subscription_end") val end: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SystemSettings(
    @SerialName("key") val key: String,
    @SerialName("value") val value: String
)


