package com.purestream.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Platform detection utility for determining if the app is running on TV or mobile
 */
object PlatformDetector {
    
    /**
     * Determines if the current device is an Android TV
     */
    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
    
    /**
     * Determines if the current device is a mobile device (phone/tablet)
     */
    fun isMobile(context: Context): Boolean {
        return !isTv(context)
    }
    
    /**
     * Returns true if the device has touchscreen capabilities
     */
    fun hasTouch(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    }
    
    /**
     * Returns true if the device supports leanback (TV) UI
     */
    fun hasLeanback(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.software.leanback")
    }
}

/**
 * Composable function to detect platform within Compose UI
 */
@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember { PlatformDetector.isTv(context) }
}

/**
 * Composable function to detect mobile platform within Compose UI
 */
@Composable
fun rememberIsMobile(): Boolean {
    val context = LocalContext.current
    return remember { PlatformDetector.isMobile(context) }
}