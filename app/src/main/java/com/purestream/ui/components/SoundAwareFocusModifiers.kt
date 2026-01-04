package com.purestream.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import com.purestream.utils.SoundManager

/**
 * Modifier that plays navigation sounds when focus changes.
 * Plays MOVE sound when gaining focus, integrates with TV navigation.
 */
@Composable
fun Modifier.soundAwareFocus(): Modifier = composed {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    this.onFocusChanged { focusState ->
        // No sound on focus move as per request
    }
}

/**
 * Modifier that plays click sound when component is clicked.
 * Should be used with clickable modifiers.
 */
@Composable
fun Modifier.soundAwareClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    this.clickable(enabled = enabled) {
        android.util.Log.d("SoundAwareClickable", "Component clicked - playing CLICK sound")
        soundManager.playSound(SoundManager.Sound.CLICK)
        onClick()
    }
}

/**
 * Combined modifier for components that need both focus and click sounds.
 * This is the most common use case for TV navigation.
 */
@Composable
fun Modifier.soundAwareNavigation(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    this
        .onFocusChanged { focusState ->
            // No sound on focus move
        }
        .clickable(enabled = enabled) {
            android.util.Log.d("SoundAwareNavigation", "Component clicked - playing CLICK sound")
            soundManager.playSound(SoundManager.Sound.CLICK)
            onClick()
        }
}