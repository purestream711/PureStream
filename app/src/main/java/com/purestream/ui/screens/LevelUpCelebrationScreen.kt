package com.purestream.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.purestream.R
import com.purestream.ui.theme.AccentPurple
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.utils.rememberIsMobile
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.purestream.utils.SoundManager

/**
 * Level-up celebration screen displayed when user advances to a new level.
 *
 * Shows:
 * - Animated LevelUp.gif
 * - Level number transition animation (old → new)
 * - Purple progress bar filling to 100%
 * - Continue button with TV focus support
 *
 * Automatically dismisses when user presses the Continue button.
 *
 * @param oldLevel The previous level (before leveling up)
 * @param newLevel The new level (after leveling up)
 * @param totalFilteredWords The total number of profanity words filtered
 * @param onDismiss Callback when user dismisses the celebration
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun LevelUpCelebrationScreen(
    oldLevel: Int,
    newLevel: Int,
    totalFilteredWords: Int,
    onDismiss: () -> Unit
) {
    val isMobile = rememberIsMobile()
    val context = LocalContext.current

    // Play level-up sound effect
    LaunchedEffect(Unit) {
        SoundManager.getInstance(context).playSound(SoundManager.Sound.LEVEL_UP)
    }

    // Progress bar animation state
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    // Level number animation state
    var showNewLevel by remember { mutableStateOf(false) }

    // Continue button focus
    val continueButtonFocusRequester = remember { FocusRequester() }
    val continueInteractionSource = remember { MutableInteractionSource() }
    val continueBackgroundColor = getAnimatedButtonBackgroundColor(
        interactionSource = continueInteractionSource,
        defaultColor = AccentPurple
    )

    // Shimmer animation for purple elements (level number and continue button)
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val purpleShimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "purple_shimmer"
    )

    // Shimmer animation for gold unlock messages (slightly offset timing)
    val goldShimmerOffset by infiniteTransition.animateFloat(
        initialValue = 250f,
        targetValue = 1250f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gold_shimmer"
    )

    // Shimmer for progress bar (left-to-right sweep, resets every 3 seconds)
    val progressBarShimmerOffset by infiniteTransition.animateFloat(
        initialValue = -400f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_bar_shimmer"
    )

    // Shimmer for continue button (horizontal left-to-right sweep, offset timing from progress bar)
    val buttonShimmerOffset by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "button_shimmer"
    )

    // Pulsing animation for level number
    val levelNumberScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "level_pulse"
    )

    // Pulsing animation for continue button
    val continueButtonScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "button_pulse"
    )

    // Start animations
    LaunchedEffect(Unit) {
        // Start progress bar animation immediately
        progress = 1f

        // Start level number transition after 500ms
        delay(500)
        showNewLevel = true
    }

    // TV: Auto-focus Continue button after animations complete
    LaunchedEffect(Unit) {
        if (!isMobile) {
            delay(2500)  // Wait for animations to complete
            try {
                continueButtonFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // Retry if focus request fails
                delay(300)
                try {
                    continueButtonFocusRequester.requestFocus()
                } catch (e2: Exception) {
                    android.util.Log.w("LevelUpCelebrationScreen", "Focus request failed: ${e2.message}")
                }
            }
        }
    }

    // Full-screen overlay with opaque gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Dark blue-gray top
                        Color(0xFF0D0D0D), // Pure black middle
                        Color(0xFF0D0D0D)  // Pure black bottom
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Pulsating Background Glow
        val glowTransition = rememberInfiniteTransition(label = "bg_glow")
        val glowAlpha by glowTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6).copy(alpha = glowAlpha), Color.Transparent),
                        radius = 1000f
                    )
                )
        )

        // Glassy Content Panel
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 600.dp)
                .background(Color(0xFF1A1C2E).copy(alpha = 0.6f), RoundedCornerShape(32.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(if (isMobile) 24.dp else 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // LevelUp.gif - Constrain height on TV to move everything else up
                Box(
                    modifier = Modifier.height(if (isMobile) 300.dp else 220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = R.raw.levelup,
                        contentDescription = "Level Up!",
                        modifier = Modifier.size(300.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (isMobile) 8.dp else 0.dp))

                // Animated level number (old → new with slide-up transition)
                AnimatedContent(
                    targetState = showNewLevel,
                    transitionSpec = {
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(durationMillis = 800)
                        ) + fadeIn(animationSpec = tween(durationMillis = 800)) with
                        slideOutVertically(
                            targetOffsetY = { -it / 2 },
                            animationSpec = tween(durationMillis = 800)
                        ) + fadeOut(animationSpec = tween(durationMillis = 800))
                    },
                    label = "level_number_animation"
                ) { newLevelVisible ->
                    // Create purple shimmer gradient
                    val purpleShimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B46C1),  // Darker purple
                            AccentPurple,        // Main purple
                            Color(0xFFC4B5FD),  // Light purple
                            AccentPurple,        // Main purple
                            Color(0xFF6B46C1)   // Darker purple
                        ),
                        start = androidx.compose.ui.geometry.Offset(purpleShimmerOffset - 500f, 0f),
                        end = androidx.compose.ui.geometry.Offset(purpleShimmerOffset + 500f, 0f)
                    )

                    Text(
                        text = if (newLevelVisible) "$newLevel" else "$oldLevel",
                        fontSize = if (isMobile) 72.sp else 60.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(brush = purpleShimmerBrush),
                        modifier = Modifier.graphicsLayer {
                            scaleX = levelNumberScale
                            scaleY = levelNumberScale
                        }
                    )
                }

                Spacer(modifier = Modifier.height(if (isMobile) 12.dp else 8.dp))

                // Purple progress bar with shimmer (animates 0% → 100%)
                Box(
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    // Create purple shimmer gradient for progress bar
                    val progressBarShimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B46C1),  // Darker purple
                            AccentPurple,        // Main purple
                            Color(0xFFC4B5FD),  // Light purple
                            AccentPurple,        // Main purple
                            Color(0xFF6B46C1)   // Darker purple
                        ),
                        start = androidx.compose.ui.geometry.Offset(progressBarShimmerOffset - 300f, 0f),
                        end = androidx.compose.ui.geometry.Offset(progressBarShimmerOffset + 300f, 0f)
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isMobile) 8.dp else 6.dp)
                    ) {
                        val barWidth = size.width * animatedProgress
                        drawRoundRect(
                            brush = progressBarShimmerBrush,
                            size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isMobile) 16.dp else 10.dp))

                // Random celebration message with total filtered count
                val celebrationMessages = listOf(
                    "You're a clean freak!",
                    "Soap for the ears, straight from the source.",
                    "Keeping it PG in a Rated-R world.",
                    "You missed a spot... just kidding, you got 'em all!",
                    "Filtering like a boss.",
                    "Bleeping awesome work!",
                    "Silence is golden (and cleaner).",
                    "Pure stream, pure ears.",
                    "Clarity achieved.",
                    "Filtering out the noise.",
                    "Your content, refined.",
                    "Crystal clear viewing unlocked.",
                    "Protecting the vibe, one word at a time.",
                    "Profanity Purge Complete.",
                    "Filter Streak: Unstoppable.",
                    "Ears Defended. XP Gained.",
                    "Critical Hit on bad language!",
                    "Level Up! Your hearing protection just got stronger.",
                    "Squeaky clean!",
                    "Scrubbed.",
                    "Pure excellence.",
                    "Nice and tidy.",
                    "Nice job!"
                )

                val randomMessage = remember { celebrationMessages.random() }

                Text(
                    text = "$randomMessage You've filtered a total of $totalFilteredWords profanity!",
                    fontSize = if (isMobile) 20.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                // Unlock message for special levels
                val unlockMessage = when (newLevel) {
                    10 -> "You unlocked Subtitle Analysis Results!"
                    15 -> "You unlocked All Profanity Filter Options!"
                    20 -> "You unlocked Dashboard Customization!"
                    else -> null
                }

                if (unlockMessage != null) {
                    Spacer(modifier = Modifier.height(if (isMobile) 12.dp else 8.dp))

                    // Create gold shimmer gradient
                    val goldShimmerBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFB8860B),  // Dark goldenrod
                            Color(0xFFFFD700),  // Gold
                            Color(0xFFFFFACD),  // Light gold
                            Color(0xFFFFD700),  // Gold
                            Color(0xFFB8860B)   // Dark goldenrod
                        ),
                        start = androidx.compose.ui.geometry.Offset(goldShimmerOffset - 500f, 0f),
                        end = androidx.compose.ui.geometry.Offset(goldShimmerOffset + 500f, 0f)
                    )

                    Text(
                        text = unlockMessage,
                        fontSize = if (isMobile) 18.sp else 16.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(brush = goldShimmerBrush),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(if (isMobile) 16.dp else 10.dp))

                // Continue button with purple shimmer (horizontal left-to-right sweep)
                val continueButtonShimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6B46C1),  // Darker purple
                        AccentPurple,        // Main purple
                        Color(0xFFC4B5FD),  // Light purple
                        AccentPurple,        // Main purple
                        Color(0xFF6B46C1)   // Darker purple
                    ),
                    start = androidx.compose.ui.geometry.Offset(buttonShimmerOffset - 350f, 0f),
                    end = androidx.compose.ui.geometry.Offset(buttonShimmerOffset + 350f, 0f)
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = continueButtonScale
                            scaleY = continueButtonScale
                        }
                        .background(brush = continueButtonShimmerBrush, shape = RoundedCornerShape(12.dp))
                        .let { mod ->
                            if (isMobile) {
                                mod
                            } else {
                                mod.focusRequester(continueButtonFocusRequester)
                                    .hoverable(continueInteractionSource)
                                    .focusable(interactionSource = continueInteractionSource)
                            }
                        }
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(
                            horizontal = if (isMobile) 32.dp else 24.dp,
                            vertical = if (isMobile) 12.dp else 8.dp
                        )
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = if (isMobile) 18.sp else 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
