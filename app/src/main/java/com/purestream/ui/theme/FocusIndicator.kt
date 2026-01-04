package com.purestream.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom focus indicator defaults for the TV app
 */
object FocusIndicatorDefaults {
    val FocusColor = FocusRed // Netflix red for focus indicator from theme
    val DefaultBorderWidth = 3.dp
    val DefaultCornerRadius = 8.dp
    val DefaultFocusScale = 1.05f // 5% size increase on focus
    val DefaultAnimationDuration = 150 // Animation duration in milliseconds
}

/**
 * Modifier that applies a subtle scale increase to any focusable element when focused.
 * This creates a professional focus indicator by slightly enlarging the element.
 * 
 * @param focusScale The scale factor when focused (default: 1.05f for 5% increase)
 * @param animationDuration Duration of the scale animation in milliseconds
 */
@Composable
fun Modifier.tvFocusIndicator(
    focusScale: Float = FocusIndicatorDefaults.DefaultFocusScale,
    animationDuration: Int = FocusIndicatorDefaults.DefaultAnimationDuration
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = tween(durationMillis = animationDuration),
        label = "focusScale"
    )
    
    return this
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .scale(scale)
}

/**
 * Variant for circular focus indicators (like profile avatars)
 */
@Composable
fun Modifier.tvCircularFocusIndicator(
    focusScale: Float = FocusIndicatorDefaults.DefaultFocusScale,
    animationDuration: Int = FocusIndicatorDefaults.DefaultAnimationDuration
): Modifier {
    return this.tvFocusIndicator(focusScale, animationDuration)
}

/**
 * Variant for card-style focus indicators with slightly larger scale increase
 */
@Composable
fun Modifier.tvCardFocusIndicator(
    focusScale: Float = 1.04f, // Reduced from 1.08f for subtle increase
    animationDuration: Int = FocusIndicatorDefaults.DefaultAnimationDuration
): Modifier {
    return this.tvFocusIndicator(focusScale, animationDuration)
}

/**
 * Convenience variant for icons and small elements with smaller scale increase
 */
@Composable
fun Modifier.tvIconFocusIndicator(
    focusScale: Float = 1.03f, // Smaller scale for icons
    animationDuration: Int = FocusIndicatorDefaults.DefaultAnimationDuration
): Modifier {
    return this.tvFocusIndicator(focusScale, animationDuration)
}

/**
 * Extension function to easily apply TV focus indicator to any Composable
 * Usage: MyComposable().tvFocus()
 */
@Composable
fun Modifier.tvFocus(
    focusScale: Float = FocusIndicatorDefaults.DefaultFocusScale,
    animationDuration: Int = FocusIndicatorDefaults.DefaultAnimationDuration
): Modifier = this.tvFocusIndicator(focusScale, animationDuration)

/**
 * Extension function specifically for Buttons - most common use case
 * Usage: Button(..., modifier = Modifier.tvButtonFocus())
 */
@Composable
fun Modifier.tvButtonFocus(): Modifier = this.tvFocusIndicator(
    focusScale = 1.04f, // Slightly smaller scale for buttons
    animationDuration = FocusIndicatorDefaults.DefaultAnimationDuration
)