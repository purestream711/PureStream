package com.purestream.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated focus/hover effects for PureStream app  
 * Provides consistent color cycling and rotation animations for TV navigation
 * Works with both focus (Android TV) and hover (desktop) interactions
 */

// PureStream focus colors - optimized for performance
private val FocusColor = Color(0xFF8B5CF6) // Purple
private val HoverColor = Color(0xFFFFFFFF) // White

/**
 * Simple focus/hover color selector - much more performant than infinite animations
 * Returns purple for focus, white for hover, transparent for inactive
 */
@Composable
fun rememberFocusHoverColor(isFocused: Boolean, isHovered: Boolean): Color {
    return when {
        isFocused -> FocusColor
        isHovered -> HoverColor
        else -> Color.Transparent
    }
}

/**
 * Removed infinite rotation for performance - static focus indicators are much faster
 */

/**
 * Optimized poster border - static purple focus, no animations
 * Much more performance-friendly for Android TV navigation
 */
fun Modifier.animatedPosterBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    borderWidth: Dp = 1.dp,
    interactionSource: MutableInteractionSource = MutableInteractionSource()
) = composed {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = rememberFocusHoverColor(isFocused, isHovered)
    
    this
        .hoverable(interactionSource)
        .focusable(interactionSource = interactionSource)
        .border(
            width = borderWidth,
            color = borderColor,
            shape = shape
        )
}

/**
 * Modifier for navigation icons with color cycling animation
 * Icons cycle through colors when focused/hovered, default to white
 */
fun Modifier.animatedNavigationIcon(
    interactionSource: MutableInteractionSource = MutableInteractionSource()
) = composed {
    this
        .hoverable(interactionSource)
        .focusable(interactionSource = interactionSource)
}

/**
 * Optimized navigation icon colors - static focus states for better performance
 */
@Composable
fun getAnimatedNavigationIconColor(
    interactionSource: MutableInteractionSource,
    defaultColor: Color = Color.White
): Color {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    return when {
        isFocused -> FocusColor
        isHovered -> HoverColor
        else -> defaultColor
    }
}

/**
 * Optimized button background - static focus states for performance
 */
fun Modifier.animatedButtonBackground(
    shape: Shape = RoundedCornerShape(4.dp),
    defaultColor: Color = Color.Yellow,
    interactionSource: MutableInteractionSource = MutableInteractionSource()
) = composed {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = when {
        isFocused -> FocusColor
        isHovered -> HoverColor
        else -> defaultColor
    }
    
    this
        .hoverable(interactionSource)
        .focusable(interactionSource = interactionSource)
        .background(
            color = backgroundColor,
            shape = shape
        )
}

/**
 * Optimized button background colors - static focus states for performance
 */
@Composable
fun getAnimatedButtonBackgroundColor(
    interactionSource: MutableInteractionSource,
    defaultColor: Color = Color.Yellow
): Color {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    return when {
        isFocused -> defaultColor
        isHovered -> defaultColor
        else -> defaultColor
    }
}

/**
 * Optimized tab bubble - static focus states for performance
 */
fun Modifier.animatedTabBubble(
    shape: Shape = RoundedCornerShape(16.dp),
    interactionSource: MutableInteractionSource = MutableInteractionSource()
) = composed {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = when {
        isFocused -> FocusColor
        isHovered -> HoverColor
        else -> Color.Yellow
    }
    
    this
        .hoverable(interactionSource)
        .focusable(interactionSource = interactionSource)
        .background(
            color = backgroundColor,
            shape = shape
        )
}

/**
 * Optimized tab bubble colors - static focus states for performance  
 */
@Composable
fun getAnimatedTabBubbleColor(
    interactionSource: MutableInteractionSource,
    defaultColor: Color = Color.Yellow
): Color {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    return when {
        isFocused -> FocusColor
        isHovered -> HoverColor
        else -> defaultColor
    }
}

/**
 * Optimized profile border - simple static focus ring for better performance
 * No complex gradients or animations that slow down TV navigation
 */
fun Modifier.animatedProfileBorder(
    borderWidth: Dp = 2.dp,
    interactionSource: MutableInteractionSource = MutableInteractionSource()
) = composed {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = rememberFocusHoverColor(isFocused, isHovered)
    
    this
        .hoverable(interactionSource)
        .focusable(interactionSource = interactionSource)
        .border(
            width = borderWidth,
            color = borderColor,
            shape = androidx.compose.foundation.shape.CircleShape
        )
}

/**
 * Extension function to check if a button should be excluded from animated styling
 * The "Analyze Profanity" button retains its original style
 */
fun String.shouldExcludeFromAnimation(): Boolean {
    return this.contains("Analyze Profanity", ignoreCase = true) ||
           this.contains("Profanity", ignoreCase = true)
}