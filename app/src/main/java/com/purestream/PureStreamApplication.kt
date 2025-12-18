package com.purestream

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.purestream.data.manager.PremiumStatusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom Application class for PureStream
 * Configures Coil image loading with optimized caching for Android TV
 * Initializes premium status management for cross-platform sync
 */
class PureStreamApplication : Application(), ImageLoaderFactory {
    
    private val tag = "PureStreamApplication"
    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "PureStream application starting...")
        
        // Initialize premium status manager for cross-platform sync
        initializePremiumStatus()
    }

    /**
     * Initialize premium status management for cross-platform synchronization
     */
    private fun initializePremiumStatus() {
        applicationScope.launch {
            try {
                Log.d(tag, "Initializing premium status manager...")
                val premiumManager = PremiumStatusManager.getInstance(this@PureStreamApplication)
                val success = premiumManager.initialize()
                
                if (success) {
                    Log.d(tag, "Premium status manager initialized successfully")
                } else {
                    Log.w(tag, "Premium status manager initialization had issues, but will continue")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize premium status manager", e)
                // Continue app startup even if premium status fails
            }
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up premium status manager
        try {
            PremiumStatusManager.getInstance(this).cleanup()
        } catch (e: Exception) {
            Log.e(tag, "Error during premium status manager cleanup", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Use 25% of available RAM for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // Use 2% of available disk space
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false) // Ignore cache headers for better caching
            .allowHardware(true) // Enable hardware bitmaps for better performance
            .allowRgb565(true) // Use RGB565 format to save memory
            .logger(null) // Disable logging for performance
            .build()
    }
}