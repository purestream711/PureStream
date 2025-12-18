package com.purestream.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

interface PurchaseCompletionListener {
    suspend fun onPurchaseCompleted()
}

class BillingManager(private val context: Context) : PurchasesUpdatedListener {
    
    private val _billingState = MutableStateFlow(BillingState())
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()
    
    private lateinit var billingClient: BillingClient

    private val tag = "BillingManager"

    // Callback for purchase completion events
    private var purchaseCompletionListener: PurchaseCompletionListener? = null
    
    init {
        setupBillingClient()
    }
    
    private fun setupBillingClient() {
        Log.d(tag, "Setting up billing client for subscription products")
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts() // Keep for any existing one-time purchases
                    .enablePrepaidPlans()   // Enable for subscription products
                    .build()
            )
            .build()
        Log.d(tag, "Billing client setup completed")
    }

    /**
     * Set listener for purchase completion events
     */
    fun setPurchaseCompletionListener(listener: PurchaseCompletionListener?) {
        this.purchaseCompletionListener = listener
        Log.d(tag, "Purchase completion listener ${if (listener != null) "set" else "cleared"}")
    }

    suspend fun startConnection(): Boolean = suspendCancellableCoroutine { continuation ->
        Log.d(tag, "Starting billing client connection...")
        
        if (billingClient.isReady) {
            Log.d(tag, "Billing client already connected")
            _billingState.value = _billingState.value.copy(isConnected = true)
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d(tag, "Billing setup finished with response code: ${billingResult.responseCode}")
                when (billingResult.responseCode) {
                    BillingResponseCode.OK -> {
                        Log.d(tag, "Billing client connected successfully")
                        _billingState.value = _billingState.value.copy(
                            isConnected = true,
                            connectionError = null
                        )
                        continuation.resume(true)
                    }
                    BillingResponseCode.BILLING_UNAVAILABLE -> {
                        val error = "Google Play Billing is not available on this device: ${billingResult.debugMessage}"
                        Log.e(tag, error)
                        _billingState.value = _billingState.value.copy(
                            isConnected = false,
                            connectionError = error
                        )
                        continuation.resume(false)
                    }
                    BillingResponseCode.SERVICE_UNAVAILABLE -> {
                        val error = "Google Play Store service is unavailable: ${billingResult.debugMessage}"
                        Log.e(tag, error)
                        _billingState.value = _billingState.value.copy(
                            isConnected = false,
                            connectionError = error
                        )
                        continuation.resume(false)
                    }
                    else -> {
                        val error = "Billing setup failed: ${billingResult.debugMessage} (Response code: ${billingResult.responseCode})"
                        Log.e(tag, error)
                        _billingState.value = _billingState.value.copy(
                            isConnected = false,
                            connectionError = error
                        )
                        continuation.resume(false)
                    }
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w(tag, "Billing service disconnected - will attempt to reconnect on next purchase")
                _billingState.value = _billingState.value.copy(isConnected = false)
            }
        })
    }
    
    suspend fun queryProductDetails(): ProductDetails? = suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            Log.e(tag, "Billing client not ready for product query")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        
        Log.d(tag, "Querying product details for subscription: ${BillingConstants.SUBSCRIPTION_ID}")
        
        // Query only subscription products (no more one-time purchases)
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConstants.SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            when (billingResult.responseCode) {
                BillingResponseCode.OK -> {
                    Log.d(tag, "Product details query successful. Found ${productDetailsList.size} products")
                    
                    val subscriptionProduct = if (productDetailsList.isNotEmpty()) {
                        val product = productDetailsList[0]
                        Log.d(tag, "Found subscription product: ${product.productId} (type: ${product.productType})")
                        product
                    } else {
                        Log.e(tag, "No subscription products found")
                        null
                    }
                    
                    continuation.resume(subscriptionProduct)
                }
                else -> {
                    Log.e(tag, "Failed to query product details: ${billingResult.debugMessage} (Response code: ${billingResult.responseCode})")
                    continuation.resume(null)
                }
            }
        }
    }
    
    suspend fun launchPurchaseFlow(activity: Activity, subscriptionType: String = BillingConstants.BASE_PLAN_MONTHLY): PurchaseResult {
        Log.d(tag, "Starting purchase flow for activity: ${activity::class.java.simpleName}")
        
        // Check billing client readiness
        if (!billingClient.isReady) {
            val error = "Billing client not ready. Connection status: ${_billingState.value.isConnected}"
            Log.e(tag, error)
            return PurchaseResult(
                success = false,
                error = BillingConstants.BILLING_UNAVAILABLE_ERROR
            )
        }
        
        Log.d(tag, "Billing client is ready, setting loading state")
        _billingState.value = _billingState.value.copy(purchaseState = PurchaseState.Loading)
        
        // Query product details with enhanced error handling
        val productDetails = queryProductDetails()
        if (productDetails == null) {
            val error = "Subscription product not found in Google Play Console. Check if subscription '${BillingConstants.SUBSCRIPTION_ID}' is properly configured with monthly/annual base plans."
            Log.e(tag, error)
            _billingState.value = _billingState.value.copy(
                purchaseState = PurchaseState.Error("Product not found")
            )
            return PurchaseResult(
                success = false,
                error = error
            )
        }
        
        Log.d(tag, "Product details retrieved: ${productDetails.productId} (${productDetails.productType})")
        
        // Build billing flow parameters based on product type
        val productDetailsParamsList = if (productDetails.productType == BillingClient.ProductType.SUBS) {
            // For subscriptions, we need to specify the base plan
            Log.d(tag, "Setting up subscription billing flow for subscription type: $subscriptionType")
            val subscriptionOfferDetails = productDetails.subscriptionOfferDetails
            if (subscriptionOfferDetails.isNullOrEmpty()) {
                val error = "No subscription offers found for product ${productDetails.productId}"
                Log.e(tag, error)
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Error(error)
                )
                return PurchaseResult(success = false, error = error)
            }
            
            // Find the offer for the specified base plan (monthly or annual)
            val targetOffer = subscriptionOfferDetails.find { offer ->
                offer.basePlanId == subscriptionType
            }
            
            if (targetOffer == null) {
                val error = "No offer found for subscription type: $subscriptionType"
                Log.e(tag, error)
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Error(error)
                )
                return PurchaseResult(success = false, error = error)
            }
            
            val offerToken = targetOffer.offerToken
            Log.d(tag, "Using subscription offer token for $subscriptionType: $offerToken")
            
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            // For one-time products
            Log.d(tag, "Setting up one-time product billing flow")
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
        }
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        Log.d(tag, "Launching billing flow...")
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        
        return when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                Log.d(tag, "Purchase flow launched successfully")
                PurchaseResult(success = true)
            }
            BillingResponseCode.BILLING_UNAVAILABLE -> {
                val error = "Google Play Billing is not available on this device"
                Log.e(tag, error)
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Error(error)
                )
                PurchaseResult(success = false, error = error)
            }
            BillingResponseCode.DEVELOPER_ERROR -> {
                val error = "Developer error: ${billingResult.debugMessage}. Check product configuration in Play Console."
                Log.e(tag, error)
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Error(error)
                )
                PurchaseResult(success = false, error = error)
            }
            BillingResponseCode.USER_CANCELED -> {
                val error = "User canceled the purchase flow"
                Log.w(tag, error)
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Cancelled
                )
                PurchaseResult(success = false, error = error)
            }
            else -> {
                val error = "Failed to launch purchase flow: ${billingResult.debugMessage} (Response code: ${billingResult.responseCode})"
                Log.e(tag, error)
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Error(error)
                )
                PurchaseResult(success = false, error = error)
            }
        }
    }
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    Log.d(tag, "Purchase successful: ${purchase.products}")
                    handlePurchase(purchase)
                }
            }
            BillingResponseCode.USER_CANCELED -> {
                Log.d(tag, "Purchase cancelled by user")
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Cancelled
                )
            }
            else -> {
                Log.e(tag, "Purchase failed: ${billingResult.debugMessage}")
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Error(
                        billingResult.debugMessage ?: BillingConstants.PURCHASE_FAILED_ERROR
                    )
                )
            }
        }
    }
    
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            } else {
                Log.d(tag, "Purchase already acknowledged")
                _billingState.value = _billingState.value.copy(
                    purchaseState = PurchaseState.Success,
                    isPremium = true
                )
                // Trigger premium status refresh
                triggerPurchaseCompletionCallback()
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(tag, "Purchase is pending")
            _billingState.value = _billingState.value.copy(
                purchaseState = PurchaseState.Loading
            )
        }
    }
    
    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            when (billingResult.responseCode) {
                BillingResponseCode.OK -> {
                    Log.d(tag, "Purchase acknowledged successfully")
                    _billingState.value = _billingState.value.copy(
                        purchaseState = PurchaseState.Success,
                        isPremium = true
                    )
                    // Trigger premium status refresh
                    triggerPurchaseCompletionCallback()
                }
                else -> {
                    Log.e(tag, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                    _billingState.value = _billingState.value.copy(
                        purchaseState = PurchaseState.Error(
                            billingResult.debugMessage ?: BillingConstants.ACKNOWLEDGE_FAILED_ERROR
                        )
                    )
                }
            }
        }
    }

    /**
     * Trigger purchase completion callback to notify PremiumStatusManager
     */
    private fun triggerPurchaseCompletionCallback() {
        purchaseCompletionListener?.let { listener ->
            Log.i(tag, "Triggering purchase completion callback")
            // Use a coroutine to call the suspend function
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    listener.onPurchaseCompleted()
                } catch (e: Exception) {
                    Log.e(tag, "Error in purchase completion callback", e)
                }
            }
        } ?: Log.d(tag, "No purchase completion listener set")
    }

    suspend fun queryPurchases(): List<Purchase> = suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        
        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            when (billingResult.responseCode) {
                BillingResponseCode.OK -> {
                    Log.d(tag, "Query purchases successful: ${purchasesList.size} purchases found")
                    continuation.resume(purchasesList)
                }
                else -> {
                    Log.e(tag, "Failed to query purchases: ${billingResult.debugMessage}")
                    continuation.resume(emptyList())
                }
            }
        }
    }
    
    suspend fun checkPremiumStatus(): Boolean {
        val purchases = queryPurchases()
        var premiumPurchase: Purchase? = null
        for (purchase in purchases) {
            if (purchase.products.contains(BillingConstants.SUBSCRIPTION_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                premiumPurchase = purchase
                Log.d(tag, "Found active premium subscription: ${purchase.products}")
                break
            }
        }
        
        val isPremium = premiumPurchase != null
        Log.d(tag, "Premium status check result: $isPremium")
        _billingState.value = _billingState.value.copy(isPremium = isPremium)
        return isPremium
    }
    
    fun disconnect() {
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
            _billingState.value = _billingState.value.copy(isConnected = false)
            Log.d(tag, "Billing client disconnected")
        }
    }
}