package com.purestream.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purestream.data.model.Achievement
import com.purestream.ui.theme.AccentPurple
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.utils.SoundManager
import com.purestream.utils.rememberIsMobile
import kotlinx.coroutines.delay

@Composable
fun AchievementUnlockCelebrationScreen(
    achievement: Achievement,
    onDismiss: () -> Unit
) {
    val isMobile = rememberIsMobile()
    val context = LocalContext.current

    // Play level-up sound effect (reusing for achievement unlock)
    LaunchedEffect(Unit) {
        SoundManager.getInstance(context).playSound(SoundManager.Sound.LEVEL_UP)
    }

    // Continue button focus
    val continueButtonFocusRequester = remember { FocusRequester() }
    val continueInteractionSource = remember { MutableInteractionSource() }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    // Shimmer animation for purple elements
    val purpleShimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "purple_shimmer"
    )

    // Shimmer for continue button
    val buttonShimmerOffset by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "button_shimmer"
    )

    // Pulsing animation for badge
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_pulse"
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

    // TV: Auto-focus Continue button
    LaunchedEffect(Unit) {
        if (!isMobile) {
            delay(1000)
            try {
                continueButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Full-screen overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF0D0D0D))
                )
            )
            .clickable(enabled = false) {}, // Block clicks passing through
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
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header Text
                val purpleShimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6B46C1),
                        AccentPurple,
                        Color(0xFFC4B5FD),
                        AccentPurple,
                        Color(0xFF6B46C1)
                    ),
                    start = androidx.compose.ui.geometry.Offset(purpleShimmerOffset - 500f, 0f),
                    end = androidx.compose.ui.geometry.Offset(purpleShimmerOffset + 500f, 0f)
                )

                Text(
                    text = "Achievement Unlocked!",
                    fontSize = if (isMobile) 32.sp else 40.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(brush = purpleShimmerBrush),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Badge Image
                Image(
                    painter = painterResource(id = achievement.iconResId),
                    contentDescription = achievement.title,
                    modifier = Modifier
                        .size(if (isMobile) 150.dp else 200.dp)
                        .scale(badgeScale),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Achievement Title
                Text(
                    text = achievement.title,
                    fontSize = if (isMobile) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Achievement Description
                Text(
                    text = achievement.description,
                    fontSize = if (isMobile) 16.sp else 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = achievement.criteria,
                    fontSize = if (isMobile) 14.sp else 16.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Continue button
                val continueButtonShimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF6B46C1),
                        AccentPurple,
                        Color(0xFFC4B5FD),
                        AccentPurple,
                        Color(0xFF6B46C1)
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
                            text = "Awesome!",
                            fontSize = if (isMobile) 18.sp else 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}