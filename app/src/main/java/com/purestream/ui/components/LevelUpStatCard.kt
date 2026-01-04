package com.purestream.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.purestream.data.model.Profile
import com.purestream.ui.theme.AccentPurple
import com.purestream.ui.theme.AccentGold
import com.purestream.ui.theme.NetflixDarkGray
import com.purestream.ui.theme.NetflixGray
import com.purestream.utils.LevelCalculator
import com.purestream.utils.rememberIsMobile
import kotlinx.coroutines.delay

@Composable
fun LevelUpStatCard(
    profile: Profile,
    filthiestMovieTitle: String? = null,
    onDismiss: () -> Unit
) {
    val isMobile = rememberIsMobile()
    val closeButtonFocusRequester = remember { FocusRequester() }
    
    // Animation state
    var cardVisible by remember { mutableStateOf(false) }
    var statsStarted by remember { mutableStateOf(false) }
    
    // Entrance animations
    val cardAlpha by animateFloatAsState(
        targetValue = if (cardVisible) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "card_alpha"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (cardVisible) 1f else 0.95f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "card_scale"
    )

    // Trigger animations
    LaunchedEffect(Unit) {
        delay(100)
        cardVisible = true
        delay(400)
        statsStarted = true
        
        if (!isMobile) {
            delay(200)
            try {
                closeButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Ensure full opacity
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF334155)
                    )
                )
            )
            .clickable { if (isMobile) onDismiss() } // Tap anywhere to dismiss on mobile
            .onKeyEvent { keyEvent ->
                // Handle Back and Select/Center/Enter to dismiss on TV
                if (keyEvent.type == KeyEventType.KeyUp && 
                    (keyEvent.key == Key.Back || keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .focusRequester(closeButtonFocusRequester) // Target for auto-focus
            .focusable(), // Ensure Box can receive key events on TV
        contentAlignment = Alignment.Center
    ) {
        // Background Glow Effect
        val infiniteTransition = rememberInfiniteTransition(label = "background_glow")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_alpha"
        )

        Box(
            modifier = Modifier
                .size(400.dp)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentPurple.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )

        // Main Card Content
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = cardAlpha
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .widthIn(max = if (isMobile) 600.dp else 900.dp)
                .fillMaxWidth(if (isMobile) 0.92f else 0.9f)
                .background(
                    color = Color(0xFF1A1C2E).copy(alpha = 0.95f), 
                    shape = RoundedCornerShape(28.dp)
                )
                .border(
                    width = 1.dp, 
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ), 
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(if (isMobile) 24.dp else 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header: Title
            Text(
                text = "Profile Statistics",
                fontSize = if (isMobile) 24.sp else 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(if (isMobile) 32.dp else 24.dp))

            // Calculate level information
            val (currentLevel, wordsIntoLevel, wordsRequired) = LevelCalculator.calculateLevel(profile.totalFilteredWordsCount)
            val progress = LevelCalculator.calculateProgress(wordsIntoLevel, wordsRequired)

            if (isMobile) {
                // Mobile Vertical Layout
                ProfileHeader(profile, currentLevel, statsStarted, progress)
                
                Spacer(modifier = Modifier.height(40.dp))
                
                MilestonesHeader()
                Spacer(modifier = Modifier.height(20.dp))
                MilestonesRow(currentLevel, statsStarted)
                
                Spacer(modifier = Modifier.height(40.dp))
                
                ImpactHeader()
                Spacer(modifier = Modifier.height(20.dp))
                ImpactGrid(profile, statsStarted, filthiestMovieTitle)
                
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "TAP ANYWHERE TO CLOSE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.3f),
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                // TV Landscape Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Column: Profile & Milestones
                    Column(modifier = Modifier.weight(1f)) {
                        ProfileHeader(profile, currentLevel, statsStarted, progress, isMobile = false)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        MilestonesHeader()
                        Spacer(modifier = Modifier.height(16.dp))
                        MilestonesRow(currentLevel, statsStarted)
                    }
                    
                    // Right Column: Lifetime Impact
                    Column(modifier = Modifier.weight(1.2f)) {
                        ImpactHeader()
                        Spacer(modifier = Modifier.height(16.dp))
                        ImpactGrid(profile, statsStarted, filthiestMovieTitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: Profile,
    currentLevel: Int,
    statsStarted: Boolean,
    progress: Float,
    isMobile: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isMobile) Arrangement.Center else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Profile Avatar with Glow
        val context = LocalContext.current
        val avatarResourceId = context.resources.getIdentifier(
            profile.avatarImage,
            "drawable",
            context.packageName
        )
        
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (isMobile) 80.dp else 70.dp)
                    .background(AccentPurple.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, AccentPurple.copy(alpha = 0.5f), CircleShape)
            )
            
            if (avatarResourceId != 0) {
                Image(
                    painter = painterResource(id = avatarResourceId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (isMobile) 72.dp else 62.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Spacer(modifier = Modifier.width(if (isMobile) 32.dp else 24.dp))
        
        // Circular Level Indicator with animated fill
        val animatedProgress by animateFloatAsState(
            targetValue = if (statsStarted) progress else 0f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing),
            label = "level_progress"
        )

        Box(contentAlignment = Alignment.Center) {
            CircularLevelIndicator(
                currentLevel = currentLevel,
                progress = animatedProgress,
                size = if (isMobile) 100.dp else 90.dp,
                strokeWidth = if (isMobile) 8.dp else 7.dp,
                progressColor = AccentPurple,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
private fun MilestonesHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(12.dp, 4.dp).background(AccentPurple, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "LEVEL MILESTONES",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun MilestonesRow(currentLevel: Int, statsStarted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val milestones = listOf(
            10 to "Analysis",
            15 to "Advanced",
            20 to "Custom",
            30 to "Maxed"
        )
        
        milestones.forEachIndexed { index, (lvl, title) ->
            val delayTime = 600 + (index * 100)
            val milestoneAlpha by animateFloatAsState(
                targetValue = if (statsStarted) 1f else 0f,
                animationSpec = tween(500, delayMillis = delayTime),
                label = "milestone_alpha"
            )
            
            Box(modifier = Modifier.graphicsLayer { alpha = milestoneAlpha }) {
                UnlockMilestone(level = lvl, title = title, currentLevel = currentLevel)
            }
        }
    }
}

@Composable
private fun ImpactHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(12.dp, 4.dp).background(AccentGold, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "LIFETIME IMPACT",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun ImpactGrid(profile: Profile, statsStarted: Boolean, filthiestMovieTitle: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val animatedFilteredWords by animateIntAsState(
                targetValue = if (statsStarted) profile.totalFilteredWordsCount else 0,
                animationSpec = tween(1500, easing = FastOutSlowInEasing),
                label = "filtered_words"
            )
            
            StatItem(
                title = "Words Filtered",
                value = animatedFilteredWords.toString(),
                icon = Icons.Default.Close,
                iconColor = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
            
            val silenceSeconds = (profile.totalFilteredWordsCount * profile.audioMuteDuration) / 1000L
            val animatedSilenceSeconds by animateIntAsState(
                targetValue = if (statsStarted) silenceSeconds.toInt() else 0,
                animationSpec = tween(1500, easing = FastOutSlowInEasing),
                label = "silence_seconds"
            )
            
            val silenceText = if (animatedSilenceSeconds < 60) {
                "$animatedSilenceSeconds sec"
            } else {
                "${animatedSilenceSeconds / 60} min"
            }
            
            StatItem(
                title = "Silence Created",
                value = silenceText,
                icon = Icons.Default.LockOpen,
                iconColor = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val totalBadges = Achievement.values().size
            val unlockedCount = profile.unlockedAchievements.size
            val animatedBadges by animateIntAsState(
                targetValue = if (statsStarted) unlockedCount else 0,
                animationSpec = tween(1500, easing = FastOutSlowInEasing),
                label = "badges"
            )
            
            StatItem(
                title = "Badges Won",
                value = "$animatedBadges/$totalBadges",
                icon = Icons.Default.Lock,
                iconColor = AccentGold,
                modifier = Modifier.weight(1f)
            )
            
            StatItem(
                title = "Filthiest Media",
                value = filthiestMovieTitle ?: "None yet",
                valueFontSize = 14.sp,
                icon = Icons.Default.Lock, // Generic icon or placeholder
                iconColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UnlockMilestone(
    level: Int,
    title: String,
    currentLevel: Int
) {
    val isUnlocked = currentLevel >= level
    val baseColor = if (isUnlocked) AccentPurple else Color.White.copy(alpha = 0.1f)
    val contentColor = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.3f)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(75.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(baseColor.copy(alpha = 0.15f), CircleShape)
                .border(1.5.dp, baseColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isUnlocked) AccentPurple else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "LVL $level",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = baseColor,
            letterSpacing = 0.5.sp
        )
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun StatItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 20.sp
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                fontSize = valueFontSize,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Start,
                maxLines = 2,
                softWrap = true,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = (valueFontSize.value * 1.2).sp
            )
        }
    }
}