package com.purestream.data.manager

import android.content.Context
import android.util.Log
import com.purestream.data.billing.BillingRepository
import com.purestream.data.repository.AppSettingsRepository
import com.purestream.data.repository.ProfileRepository
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.data.manager.ProfileManager
import com.purestream.data.billing.PurchaseCompletionListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Centralized manager for premium status across the entire application.
 * Handles cross-platform synchronization and prevents multiple billing initialization conflicts.
 */
class PremiumStatusManager private constructor(private val context: Context) : PurchaseCompletionListener {
    
    private val tag = "PremiumStatusManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    
    // Repositories
    private val appSettingsRepository = AppSettingsRepository(context)
    private val profileRepository = ProfileRepository(context)
    private var billingRepository: BillingRepository? = null
    
    // State management
    private val _premiumStatus = MutableStateFlow<PremiumStatusState>(PremiumStatusState.Unknown)
    val premiumStatus: StateFlow<PremiumStatusState> = _premiumStatus.asStateFlow()
    
    private var lastCheckTime = 0L
    private val checkCooldownMs = 30_000L // 30 seconds between checks
    
    companion object {
        @Volatile
        private var INSTANCE: PremiumStatusManager? = null
        
        fun getInstance(context: Context): PremiumStatusManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PremiumStatusManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize premium status checking. This should be called once during app startup.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                Log.d(tag, "Initializing PremiumStatusManager...")
                _premiumStatus.value = PremiumStatusState.Loading
                
                // Initialize billing repository
                billingRepository = BillingRepository(context, appSettingsRepository)
                // Connect this PremiumStatusManager to receive purchase completion callbacks
                billingRepository!!.setPremiumStatusManager(this)
                val billingConnected = billingRepository!!.initialize()
                
                if (billingConnected) {
                    // Check premium status and sync with local storage
                    val localStatus = getLocalPremiumStatus()
                    val actualPremiumStatus = checkActualPremiumStatus()
                    
                    Log.d(tag, "Premium status comparison - Local: $localStatus, Google Play: $actualPremiumStatus")
                    
                    if (localStatus != actualPremiumStatus) {
                        Log.w(tag, "Premium status mismatch detected! Local: $localStatus, Google Play: $actualPremiumStatus")
                        if (!localStatus && actualPremiumStatus) {
                            Log.i(tag, "Premium subscription activated - Local shows free but Google Play shows premium")
                        }
                    }

                    updateLocalPremiumStatus(actualPremiumStatus)

                    _premiumStatus.value = if (actualPremiumStatus) {
                        PremiumStatusState.Premium
                    } else {
                        // ALWAYS enforce profile limits when status is Free (not just on transitions)
                        Log.w(tag, "Premium status is FREE - enforcing non-premium profile limitations")
                        enforceNonPremiumProfileLimits()
                        PremiumStatusState.Free
                    }
                    
                    // Set last check time to prevent immediate cooldown after initialization
                    lastCheckTime = System.currentTimeMillis()
                    
                    Log.d(tag, "PremiumStatusManager initialized successfully. Final Status: ${_premiumStatus.value}")
                    true
                } else {
                    Log.w(tag, "Billing connection failed, falling back to local storage")
                    val localStatus = getLocalPremiumStatus()
                    _premiumStatus.value = if (localStatus) {
                        PremiumStatusState.Premium
                    } else {
                        // ALWAYS enforce profile limits when status is Free (fallback case)
                        Log.w(tag, "Local premium status is FREE - enforcing non-premium profile limitations")
                        enforceNonPremiumProfileLimits()
                        PremiumStatusState.Free
                    }
                    false
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize PremiumStatusManager", e)
                _premiumStatus.value = PremiumStatusState.Error(e.message ?: "Unknown error")
                false
            }
        }
    }
    
    /**
     * Force refresh premium status from Google Play (with cooldown protection)
     */
    suspend fun refreshPremiumStatus(): Boolean = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        // Prevent too frequent checks
        if (currentTime - lastCheckTime < checkCooldownMs && _premiumStatus.value !is PremiumStatusState.Error) {
            Log.d(tag, "Premium status check skipped due to cooldown")
            return@withContext isPremium()
        }
        
        mutex.withLock {
            try {
                Log.d(tag, "Refreshing premium status...")
                _premiumStatus.value = PremiumStatusState.Loading
                
                val localStatus = getLocalPremiumStatus()
                val actualStatus = checkActualPremiumStatus()

                Log.d(tag, "Premium status refresh check - Local: $localStatus, Google Play: $actualStatus")

                updateLocalPremiumStatus(actualStatus)

                _premiumStatus.value = if (actualStatus) {
                    PremiumStatusState.Premium
                } else {
                    // ALWAYS enforce profile limits when status is Free (during refresh)
                    Log.w(tag, "Premium status is FREE during refresh - enforcing non-premium profile limitations")
                    enforceNonPremiumProfileLimits()
                    PremiumStatusState.Free
                }
                
                lastCheckTime = currentTime
                Log.d(tag, "Premium status refreshed: ${_premiumStatus.value}")
                actualStatus
            } catch (e: Exception) {
                Log.e(tag, "Failed to refresh premium status", e)
                _premiumStatus.value = PremiumStatusState.Error(e.message ?: "Refresh failed")
                // Fallback to local storage
                getLocalPremiumStatus()
            }
        }
    }
    
    /**
     * Force refresh premium status from Google Play (bypasses cooldown)
     * Use this after purchases, cancellations, or app startup for immediate updates
     */
    suspend fun forceRefreshPremiumStatus(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                Log.d(tag, "Force refreshing premium status (bypassing cooldown)...")
                _premiumStatus.value = PremiumStatusState.Loading
                
                val localStatus = getLocalPremiumStatus()
                val actualStatus = checkActualPremiumStatus()

                Log.d(tag, "Premium status force refresh check - Local: $localStatus, Google Play: $actualStatus")

                updateLocalPremiumStatus(actualStatus)

                _premiumStatus.value = if (actualStatus) {
                    PremiumStatusState.Premium
                } else {
                    // ALWAYS enforce profile limits when status is Free (during force refresh)
                    Log.w(tag, "Premium status is FREE during force refresh - enforcing non-premium profile limitations")
                    enforceNonPremiumProfileLimits()
                    PremiumStatusState.Free
                }
                
                lastCheckTime = System.currentTimeMillis()
                Log.d(tag, "Premium status force refreshed: ${_premiumStatus.value}")
                actualStatus
            } catch (e: Exception) {
                Log.e(tag, "Failed to force refresh premium status", e)
                _premiumStatus.value = PremiumStatusState.Error(e.message ?: "Force refresh failed")
                // Fallback to local storage
                getLocalPremiumStatus()
            }
        }
    }

    /**
     * Handle purchase completion - immediately refresh premium status
     * This ensures UI and profile enforcement update instantly after purchase
     */
    suspend fun handlePurchaseCompletion(): Boolean = withContext(Dispatchers.IO) {
        Log.i(tag, "Purchase completed - triggering immediate premium status refresh")
        return@withContext forceRefreshPremiumStatus()
    }

    /**
     * Implementation of PurchaseCompletionListener interface
     * Called by BillingManager when purchase is completed
     */
    override suspend fun onPurchaseCompleted() {
        Log.i(tag, "PurchaseCompletionListener callback triggered")
        handlePurchaseCompletion()
    }

    /**
     * Handle app resume from background - force refresh to check for subscription changes
     * This is useful for detecting cancellations that happened while app was in background
     */
    suspend fun handleAppResume(): Boolean = withContext(Dispatchers.IO) {
        Log.d(tag, "App resumed from background, checking for subscription changes")
        return@withContext forceRefreshPremiumStatus()
    }
    
    /**
     * Get current premium status (synchronous)
     */
    fun isPremium(): Boolean {
        return when (val current = _premiumStatus.value) {
            is PremiumStatusState.Premium -> true
            is PremiumStatusState.Free -> false
            is PremiumStatusState.Loading -> {
                // During loading, check local storage as fallback
                runBlocking { getLocalPremiumStatus() }
            }
            is PremiumStatusState.Unknown -> {
                // If unknown, assume free and trigger refresh
                scope.launch { refreshPremiumStatus() }
                false
            }
            is PremiumStatusState.Error -> {
                // On error, fallback to local storage
                Log.w(tag, "Premium status error, falling back to local storage: ${current.message}")
                runBlocking { getLocalPremiumStatus() }
            }
        }
    }
    
    /**
     * Check actual premium status from Google Play Billing
     */
    private suspend fun checkActualPremiumStatus(): Boolean {
        return try {
            val repository = billingRepository
            if (repository == null) {
                Log.w(tag, "BillingRepository not initialized, using local storage")
                getLocalPremiumStatus()
            } else {
                val status = repository.checkPremiumStatus()
                Log.d(tag, "Google Play premium status: $status")
                status
            }
        } catch (e: Exception) {
            Log.e(tag, "Error checking premium status from Google Play", e)
            getLocalPremiumStatus()
        }
    }
    
    /**
     * Get premium status from local storage
     */
    private suspend fun getLocalPremiumStatus(): Boolean {
        return try {
            appSettingsRepository.getAppSettings().first()?.isPremium ?: false
        } catch (e: Exception) {
            Log.e(tag, "Error reading local premium status", e)
            false
        }
    }
    
    /**
     * Update local storage premium status
     */
    private suspend fun updateLocalPremiumStatus(isPremium: Boolean) {
        try {
            Log.d(tag, "Updating local premium status to: $isPremium")
            appSettingsRepository.updatePremiumStatus(isPremium)
        } catch (e: Exception) {
            Log.e(tag, "Failed to update local premium status", e)
        }
    }
    
    /**
     * Get billing repository for purchase flows (with safety checks)
     */
    fun getBillingRepository(): BillingRepository? {
        return billingRepository
    }
    
    /**
     * Debug method to log current premium status information
     * Useful for troubleshooting cross-platform sync issues
     */
    suspend fun debugPremiumStatus(): String {
        return try {
            val currentState = _premiumStatus.value
            val localStatus = getLocalPremiumStatus()
            val googlePlayStatus = checkActualPremiumStatus()
            
            val debugInfo = buildString {
                appendLine("=== Premium Status Debug Information ===")
                appendLine("Current State: $currentState")
                appendLine("Local Storage Status: $localStatus")
                appendLine("Google Play Status: $googlePlayStatus")
                appendLine("Last Check Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastCheckTime))}")
                appendLine("Billing Repository Initialized: ${billingRepository != null}")
                
                if (localStatus != googlePlayStatus) {
                    appendLine("⚠️  WARNING: Local storage and Google Play status mismatch!")
                    appendLine("   This indicates a cross-platform sync issue.")
                }
                
                appendLine("==========================================")
            }
            
            Log.d(tag, debugInfo)
            debugInfo
        } catch (e: Exception) {
            val errorInfo = "Error generating debug information: ${e.message}"
            Log.e(tag, errorInfo, e)
            errorInfo
        }
    }

    /**
     * Enforce profile limitations for non-premium users
     * This is called whenever premium status is confirmed as FREE (not just on transitions)
     */
    private suspend fun enforceNonPremiumProfileLimits() {
        Log.i(tag, "Enforcing non-premium profile limitations...")
        revertProfilesForFreeUser()
    }

    /**
     * Revert profiles to free user limitations when premium subscription expires
     */
    private suspend fun revertProfilesForFreeUser() {
        try {
            Log.i(tag, "=== STARTING PROFILE REVERSION FOR EXPIRED PREMIUM ===")

            val profiles = profileRepository.getAllProfiles().first()
            Log.i(tag, "Found ${profiles.size} total profiles to check")

            var profilesModified = false

            for (profile in profiles) {
                Log.d(tag, "Checking profile: '${profile.name}' (Type: ${profile.profileType}, Filter: ${profile.profanityFilterLevel})")

                // Revert non-MILD filter levels to MILD for adult profiles only
                if (profile.profanityFilterLevel != ProfanityFilterLevel.MILD &&
                    profile.profileType != com.purestream.data.model.ProfileType.CHILD) {

                    Log.w(tag, "REVERTING profile '${profile.name}' filter level from ${profile.profanityFilterLevel} to MILD")

                    val updatedProfile = profile.copy(profanityFilterLevel = ProfanityFilterLevel.MILD)

                    try {
                        profileRepository.updateProfile(updatedProfile)
                        Log.i(tag, "Successfully updated profile '${profile.name}' in database")
                        profilesModified = true
                    } catch (updateException: Exception) {
                        Log.e(tag, "Failed to update profile '${profile.name}' in database", updateException)
                    }
                } else {
                    Log.d(tag, "Profile '${profile.name}' does not need filter level changes (Filter: ${profile.profanityFilterLevel}, Type: ${profile.profileType})")
                }
            }

            if (profilesModified) {
                Log.i(tag, "=== PROFILE REVERSION COMPLETED SUCCESSFULLY ===")

                // CRITICAL: Force ProfileManager to refresh from database to fix race condition
                // This ensures the UI immediately reflects the database changes
                try {
                    Log.w(tag, "Forcing ProfileManager to refresh current profile from database...")
                    val profileManager = ProfileManager.getInstance(context)

                    // Get current profile from ProfileManager
                    val currentProfileFlow = profileManager.currentProfile
                    val currentProfile = currentProfileFlow.value

                    if (currentProfile != null) {
                        Log.d(tag, "Current profile before refresh: '${currentProfile.name}' (Filter: ${currentProfile.profanityFilterLevel})")

                        // Re-fetch the updated profile from database
                        val updatedProfile = profileRepository.getProfileById(currentProfile.id)
                        if (updatedProfile != null) {
                            Log.w(tag, "Re-setting ProfileManager with updated profile: '${updatedProfile.name}' (Filter: ${updatedProfile.profanityFilterLevel})")
                            profileManager.setCurrentProfile(updatedProfile)
                        } else {
                            Log.e(tag, "Failed to re-fetch updated profile from database")
                        }
                    } else {
                        Log.w(tag, "No current profile in ProfileManager to refresh")
                    }
                } catch (profileManagerException: Exception) {
                    Log.e(tag, "Failed to refresh ProfileManager after profile reversion", profileManagerException)
                }
            } else {
                Log.i(tag, "=== NO PROFILE FILTER LEVEL CHANGES WERE NEEDED ===")
            }

        } catch (e: Exception) {
            Log.e(tag, "=== ERROR DURING PROFILE REVERSION ===", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
        billingRepository?.disconnect()
        Log.d(tag, "PremiumStatusManager cleaned up")
    }

    /**
     * Temporarily set premium status for testing/demo purposes.
     * @param durationMs Duration in milliseconds to keep the temporary status.
     */
    fun setTemporaryPremiumStatus(durationMs: Long) {
        scope.launch {
            Log.d(tag, "Activating temporary premium status for ${durationMs}ms")
            _premiumStatus.value = PremiumStatusState.Premium
            
            delay(durationMs)
            
            Log.d(tag, "Temporary premium status expired, reverting...")
            refreshPremiumStatus()
        }
    }
}

/**
 * Represents the current state of premium status checking
 */
sealed class PremiumStatusState {
    object Unknown : PremiumStatusState()
    object Loading : PremiumStatusState()
    object Premium : PremiumStatusState()
    object Free : PremiumStatusState()
    data class Error(val message: String) : PremiumStatusState()
}