package com.purestream.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Utility class for managing haptic feedback (vibrations) across the app.
 * Optimized for mobile devices to provide subtle tactile feedback.
 */
class HapticManager private constructor(context: Context) {
    
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val isMobile = PlatformDetector.isMobile(context)

    companion object {
        @Volatile
        private var INSTANCE: HapticManager? = null

        fun getInstance(context: Context): HapticManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HapticManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Performs a subtle click haptic feedback.
     * Only executes on mobile devices.
     */
    fun performClickHaptic() {
        if (!isMobile) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10) // Very short vibration for older devices
            }
        } catch (e: Exception) {
            android.util.Log.e("HapticManager", "Failed to perform haptic feedback: ${e.message}")
        }
    }

    /**
     * Performs a slightly stronger haptic feedback, useful for level up or achievements.
     * Only executes on mobile devices.
     */
    fun performSuccessHaptic() {
        if (!isMobile) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 10, 50, 10), -1)
            }
        } catch (e: Exception) {
            android.util.Log.e("HapticManager", "Failed to perform success haptic: ${e.message}")
        }
    }
}
