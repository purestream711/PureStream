package com.purestream.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.purestream.data.billing.BillingRepository
import com.purestream.data.billing.BillingConstants
import com.purestream.data.billing.PurchaseState
import com.purestream.data.manager.PremiumStatusManager
import com.purestream.data.manager.PremiumStatusState
import com.purestream.data.model.AppSettings
import com.purestream.data.repository.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsState(
    val appSettings: AppSettings = AppSettings(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val purchaseState: PurchaseState = PurchaseState.Idle,
    val billingConnected: Boolean = false
)

class SettingsViewModel(context: Context) : ViewModel() {
    
    private val appSettingsRepository = AppSettingsRepository(context)
    private val premiumStatusManager = PremiumStatusManager.getInstance(context)
    
    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()
    
    init {
        loadAppSettings()
        observePremiumStatus()
    }
    
    private fun loadAppSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                appSettingsRepository.getAppSettings().collect { settings ->
                    _uiState.value = _uiState.value.copy(
                        appSettings = settings ?: AppSettings(),
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load settings: ${e.message}"
                )
            }
        }
    }
    
    private fun observePremiumStatus() {
        viewModelScope.launch {
            premiumStatusManager.premiumStatus.collect { premiumState ->
                val billingConnected = when (premiumState) {
                    is PremiumStatusState.Premium, is PremiumStatusState.Free -> true
                    is PremiumStatusState.Loading -> false
                    is PremiumStatusState.Unknown -> false
                    is PremiumStatusState.Error -> false
                }
                
                // Determine effective premium status (handles cheat codes)
                val isPremiumEffective = premiumState is PremiumStatusState.Premium
                
                _uiState.value = _uiState.value.copy(
                    billingConnected = billingConnected,
                    appSettings = _uiState.value.appSettings.copy(isPremium = isPremiumEffective)
                )
                
                // Refresh app settings when premium status changes
                if (premiumState is PremiumStatusState.Premium || premiumState is PremiumStatusState.Free) {
                    // We don't call loadAppSettings() here to avoid overwriting the effective status
                    // Instead, we trust PremiumStatusManager as the source of truth for premium status
                    // AppSettingsRepository will be updated by PremiumStatusManager eventually if needed
                }
            }
        }
    }
    
    fun upgradeToPremium(activity: Activity, subscriptionType: String = "monthly") {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "Starting premium upgrade for activity: ${activity::class.java.simpleName}")
                android.util.Log.d("SettingsViewModel", "Subscription type: $subscriptionType")
                
                _uiState.value = _uiState.value.copy(purchaseState = PurchaseState.Loading)
                
                // Get billing repository from premium status manager (safer approach)
                val billingRepository = premiumStatusManager.getBillingRepository()
                if (billingRepository == null) {
                    val error = "Billing system not initialized. Please restart the app and try again."
                    android.util.Log.e("SettingsViewModel", error)
                    _uiState.value = _uiState.value.copy(
                        purchaseState = PurchaseState.Error(error),
                        error = error
                    )
                    return@launch
                }
                
                // Convert subscription type to billing constants
                val billingSubscriptionType = when (subscriptionType.lowercase()) {
                    "annual", "yearly" -> BillingConstants.BASE_PLAN_ANNUAL
                    else -> BillingConstants.BASE_PLAN_MONTHLY
                }
                
                android.util.Log.d("SettingsViewModel", "Launching purchase flow for billing subscription type: $billingSubscriptionType")
                val result = billingRepository.purchasePremium(activity, billingSubscriptionType)
                android.util.Log.d("SettingsViewModel", "Purchase flow result: success=${result.success}, error=${result.error}")
                
                if (!result.success) {
                    android.util.Log.e("SettingsViewModel", "Purchase failed: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        purchaseState = PurchaseState.Error(result.error ?: "Purchase failed"),
                        error = result.error
                    )
                } else {
                    android.util.Log.d("SettingsViewModel", "Purchase flow launched successfully")
                    _uiState.value = _uiState.value.copy(purchaseState = PurchaseState.Success)
                    
                    // Force refresh premium status immediately after purchase (bypasses cooldown)
                    premiumStatusManager.forceRefreshPremiumStatus()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Exception during premium upgrade: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    purchaseState = PurchaseState.Error(e.message ?: "Unknown error"),
                    error = "Failed to upgrade to premium: ${e.message}"
                )
            }
        }
    }
    
    fun switchToFreeVersion() {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "Switching to free version...")
                appSettingsRepository.updatePremiumStatus(false)
                android.util.Log.d("SettingsViewModel", "Switched to free version")
                loadAppSettings() // Refresh the state
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to switch to free version: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to switch to free version: ${e.message}"
                )
            }
        }
    }
    
    fun clearPurchaseState() {
        _uiState.value = _uiState.value.copy(purchaseState = PurchaseState.Idle)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Note: PremiumStatusManager cleanup is handled at the application level
        // Individual ViewModels no longer manage billing lifecycle
    }
}