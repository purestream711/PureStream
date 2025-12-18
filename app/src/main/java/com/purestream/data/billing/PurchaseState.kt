package com.purestream.data.billing

import com.android.billingclient.api.Purchase

data class BillingState(
    val isConnected: Boolean = false,
    val connectionError: String? = null,
    val purchaseState: PurchaseState = PurchaseState.Idle,
    val isPremium: Boolean = false
)

sealed class PurchaseState {
    object Idle : PurchaseState()
    object Loading : PurchaseState()
    object Success : PurchaseState()
    data class Error(val message: String) : PurchaseState()
    object Cancelled : PurchaseState()
}

data class PurchaseResult(
    val success: Boolean,
    val purchase: Purchase? = null,
    val error: String? = null
)

object BillingConstants {
    const val PRODUCT_ID_PRO = "purestream_pro" // Keep for existing one-time purchases
    const val SUBSCRIPTION_ID = "purestream_pro_subscription"
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_ANNUAL = "annual"
    const val BILLING_UNAVAILABLE_ERROR = "Google Play Billing is not available"
    const val PURCHASE_CANCELLED_ERROR = "Purchase was cancelled"
    const val PURCHASE_FAILED_ERROR = "Purchase failed"
    const val ACKNOWLEDGE_FAILED_ERROR = "Failed to acknowledge purchase"
}