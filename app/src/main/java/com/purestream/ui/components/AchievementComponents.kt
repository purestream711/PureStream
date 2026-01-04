package com.purestream.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.purestream.data.model.Achievement
import com.purestream.ui.theme.AccentPurple
import com.purestream.utils.SoundManager
import com.purestream.utils.rememberIsMobile

@Composable
fun AchievementRow(
    unlockedAchievements: List<String>,
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
    levelFocusRequester: FocusRequester? = null,
    lastBadgeFocusRequester: FocusRequester? = null
) {
    val allAchievements = Achievement.values()
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }
    
    // Create FocusRequesters for each badge to enable navigation
    val focusRequesters = remember { List(allAchievements.size) { FocusRequester() } }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        allAchievements.forEachIndexed { index, achievement ->
            val isUnlocked = unlockedAchievements.contains(achievement.name)
            
            AchievementIcon(
                achievement = achievement,
                isUnlocked = isUnlocked,
                onClick = { selectedAchievement = achievement },
                focusRequester = focusRequesters[index],
                extraFocusRequester = if (index == allAchievements.size - 1) lastBadgeFocusRequester else null,
                nextFocusRequester = if (index < allAchievements.size - 1) {
                    focusRequesters[index + 1] 
                } else {
                    levelFocusRequester // Link last item to level area
                },
                previousFocusRequester = if (index > 0) focusRequesters[index - 1] else null,
                downFocusRequester = contentFocusRequester
            )
        }
    }

    selectedAchievement?.let { achievement ->
        val isUnlocked = unlockedAchievements.contains(achievement.name)
        AchievementCardDialog(
            achievement = achievement,
            isUnlocked = isUnlocked,
            onDismiss = { selectedAchievement = null }
        )
    }
}

@Composable
fun AchievementIcon(
    achievement: Achievement,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    extraFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    previousFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    val isMobile = rememberIsMobile()
    val size = if (isMobile) 48.dp else 47.dp
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    
    // Play move sound on focus removed as per request
    LaunchedEffect(isFocused) {
        if (isFocused) {
            // SoundManager.getInstance(context).playSound(SoundManager.Sound.MOVE)
        }
    }

    // Grayscale filter for locked achievements
    val colorFilter = if (isUnlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    val alpha = if (isUnlocked) 1f else 0.5f

    Box(
        modifier = Modifier
            .size(size)
            .let { mod ->
                if (!isMobile && focusRequester != null) {
                    var m = mod.focusRequester(focusRequester)
                    if (extraFocusRequester != null) {
                        m = m.focusRequester(extraFocusRequester)
                    }
                    m.focusProperties {
                            right = nextFocusRequester ?: FocusRequester.Default
                            left = previousFocusRequester ?: FocusRequester.Default
                            down = downFocusRequester ?: FocusRequester.Default
                        }
                        .focusable(interactionSource = interactionSource)
                } else {
                    mod
                }
            }
            .clickable {
                // Play sound only if unlocked
                if (isUnlocked) {
                    SoundManager.getInstance(context).playSound(SoundManager.Sound.BADGE_SELECT)
                }
                onClick()
            }
            // Add border when focused - matches badge size
            .let { mod ->
                if (isFocused) {
                    mod.border(
                        width = 1.5.dp,
                        color = AccentPurple,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                } else {
                    mod
                }
            }
    ) {
        Image(
            painter = painterResource(id = achievement.iconResId),
            contentDescription = achievement.title,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
    }
}

@Composable
fun AchievementCardDialog(
    achievement: Achievement,
    isUnlocked: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF0D0D0D))
                    )
                )
                .clickable { onDismiss() }, // Click anywhere to close
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

            AchievementCardContent(achievement, isUnlocked)
        }
    }
}

@Composable
fun AchievementCardContent(
    achievement: Achievement,
    isUnlocked: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "achievement_pulse")

    // Pulsing animation for the badge
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_pulse"
    )

    // Shimmery purple border animation
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_shimmer"
    )

    val borderColor = AccentPurple.copy(alpha = borderAlpha)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        // Card Container - GLASSY
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(600.dp) // Reverted to previous height from original request
                .background(
                    color = Color(0xFF1A1C2E).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 4.dp,
                    brush = if (isUnlocked) {
                        Brush.verticalGradient(listOf(borderColor, Color.Transparent))
                    } else {
                        Brush.verticalGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Transparent))
                    },
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween // Distribute space
            ) {
                // Title
                Text(
                    text = achievement.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) Color.White else Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Badge Image - SHRUNK by 33% from previous 265dp
                val imageColorFilter = if (isUnlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                
                Box(
                    modifier = Modifier
                        .weight(1f) // Let image take available space
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = achievement.iconResId),
                        contentDescription = achievement.title,
                        modifier = Modifier
                            .size(178.dp) // New size for badge image
                            .scale(if (isUnlocked) scale else 1.0f),
                        contentScale = ContentScale.Fit,
                        colorFilter = imageColorFilter
                    )
                    
                    if (!isUnlocked) {
                         Text(
                            text = "LOCKED",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray.copy(alpha=0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(8.dp)).padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Criteria / Description (MOVED INSIDE)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = achievement.description,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = achievement.criteria,
                        fontSize = 16.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}