package com.purestream.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.purestream.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class BillingRepository(
    private val context: Context,
    private val appSettingsRepository: AppSettingsRepository
) {
    private val billingManager = BillingManager(context)
    private val tag = "BillingRepository"
    
    val billingState: StateFlow<BillingState> = billingManager.billingState

    /**
     * Set up connection to PremiumStatusManager for purchase completion callbacks
     */
    fun setPremiumStatusManager(premiumStatusManager: Any?) {
        if (premiumStatusManager is PurchaseCompletionListener) {
            billingManager.setPurchaseCompletionListener(premiumStatusManager)
            Log.d(tag, "Connected BillingRepository to PremiumStatusManager for purchase callbacks")
        }
    }

    suspend fun initialize(): Boolean {
        Log.d(tag, "Initializing billing repository")
        val connected = billingManager.startConnection()
        
        if (connected) {
            // Check existing purchases and update local premium status
            val isPremium = billingManager.checkPremiumStatus()
            updateLocalPremiumStatus(isPremium)
            Log.d(tag, "Billing initialized, premium status: $isPremium")
        }
        
        return connected
    }
    
    suspend fun purchasePremium(activity: Activity, subscriptionType: String = BillingConstants.BASE_PLAN_MONTHLY): PurchaseResult {
        Log.d(tag, "Starting premium purchase flow")
        
        if (!billingManager.billingState.value.isConnected) {
            Log.e(tag, "Billing not connected, attempting to reconnect")
            val connected = billingManager.startConnection()
            if (!connected) {
                return PurchaseResult(
                    success = false,
                    error = BillingConstants.BILLING_UNAVAILABLE_ERROR
                )
            }
        }
        
        val result = billingManager.launchPurchaseFlow(activity, subscriptionType)
        
        // If purchase was successful, wait for the state to update and then sync with local storage
        if (result.success) {
            // The BillingManager will handle the purchase callback and update the state
            // We can monitor the state to know when the purchase is complete
            Log.d(tag, "Purchase flow launched successfully")
        }
        
        return result
    }
    
    suspend fun purchaseMonthlyPremium(activity: Activity): PurchaseResult {
        Log.d(tag, "Starting monthly premium purchase flow")
        return purchasePremium(activity, BillingConstants.BASE_PLAN_MONTHLY)
    }
    
    suspend fun purchaseAnnualPremium(activity: Activity): PurchaseResult {
        Log.d(tag, "Starting annual premium purchase flow")
        return purchasePremium(activity, BillingConstants.BASE_PLAN_ANNUAL)
    }
    
    suspend fun checkPremiumStatus(): Boolean {
        Log.d(tag, "Checking premium status")
        return billingManager.checkPremiumStatus()
    }
    
    suspend fun syncPremiumStatusWithLocal() {
        Log.d(tag, "Syncing premium status with local storage")
        val isPremium = billingManager.checkPremiumStatus()
        updateLocalPremiumStatus(isPremium)
    }
    
    private suspend fun updateLocalPremiumStatus(isPremium: Boolean) {
        try {
            Log.d(tag, "Updating local premium status to: $isPremium")
            appSettingsRepository.updatePremiumStatus(isPremium)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update local premium status: ${e.message}")
        }
    }
    
    suspend fun getPremiumStatusFromLocal(): Boolean {
        return try {
            val settings = appSettingsRepository.getAppSettings().first()
            settings?.isPremium ?: false
        } catch (e: Exception) {
            Log.e(tag, "Failed to get premium status from local storage: ${e.message}")
            false
        }
    }
    
    fun disconnect() {
        Log.d(tag, "Disconnecting billing repository")
        billingManager.disconnect()
    }
}