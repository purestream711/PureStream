package com.purestream.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purestream.data.model.Profile
import com.purestream.ui.theme.AccentPurple
import com.purestream.utils.LevelCalculator
import kotlinx.coroutines.delay

/**
 * RPG-style level-up tracker card displaying profanity filter progress.
 *
 * Shows:
 * - Current filter level with shield icon
 * - Progress bar to next level (purple themed)
 * - Total words filtered count
 * - Subtle scale animation on level-up
 *
 * @param currentProfile The profile to display level information for
 * @param modifier Optional modifier for the card
 */
@Composable
fun LevelUpTrackerCard(
    currentProfile: Profile,
    modifier: Modifier = Modifier
) {
    // Calculate level information
    val (currentLevel, wordsIntoLevel, wordsRequired) = remember(currentProfile.totalFilteredWordsCount) {
        LevelCalculator.calculateLevel(currentProfile.totalFilteredWordsCount)
    }

    val progress = remember(wordsIntoLevel, wordsRequired) {
        LevelCalculator.calculateProgress(wordsIntoLevel, wordsRequired)
    }

    // Level-up animation state
    var isLevelingUp by remember { mutableStateOf(false) }
    var previousLevel by remember { mutableIntStateOf(currentLevel) }

    // Detect level change and trigger animation
    LaunchedEffect(currentLevel) {
        if (currentLevel > previousLevel && previousLevel > 0) {
            isLevelingUp = true
            delay(2000) // Animation duration
            isLevelingUp = false
        }
        previousLevel = currentLevel
    }

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isLevelingUp) 1.05f else 1.0f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "scale_animation"
    )

    // Color animation for level-up pulse
    val titleColor by animateColorAsState(
        targetValue = if (isLevelingUp) AccentPurple else Color.White,
        animationSpec = tween(
            durationMillis = 1000,
            easing = LinearEasing
        ),
        label = "color_animation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A) // BackgroundCard color
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shield icon with purple background
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = AccentPurple.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Filter Level Shield",
                    tint = AccentPurple,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Level information and progress
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Level title
                Text(
                    text = "Filter Level $currentLevel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Total words filtered
                Text(
                    text = "${currentProfile.totalFilteredWordsCount} words filtered",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = AccentPurple,
                        trackColor = Color(0xFF2A2A2A),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress text
                    Text(
                        text = "$wordsIntoLevel / $wordsRequired to next level",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
