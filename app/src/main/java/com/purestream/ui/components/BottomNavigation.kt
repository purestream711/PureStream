package com.purestream.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purestream.data.model.Profile
import com.purestream.ui.theme.*
import com.purestream.utils.SoundManager
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource

/**
 * Bottom navigation bar for mobile devices
 * Replaces the left sidebar functionality on mobile platforms
 */
@Composable
fun BottomNavigation(
    currentProfile: Profile?,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoviesClick: () -> Unit,
    onTvShowsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    currentSection: String = "home",
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    // Animate the visibility with smooth slide animation
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 120f, // Slide down
        animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "bottom_nav_offset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .offset { IntOffset(0, offsetY.roundToInt()) },
        contentAlignment = Alignment.Center
    ) {
        // Floating Glass Panel
        Surface(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            color = Color(0xFF1A1C2E).copy(alpha = 0.8f),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent))),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(Icons.Default.Search, "Search", currentSection == "search", { soundManager.playSound(SoundManager.Sound.CLICK); onSearchClick() })
                BottomNavItem(Icons.Default.Home, "Home", currentSection == "home", { soundManager.playSound(SoundManager.Sound.CLICK); onHomeClick() })
                BottomNavItem(Icons.Default.Movie, "Movies", currentSection == "movies", { soundManager.playSound(SoundManager.Sound.CLICK); onMoviesClick() })
                BottomNavItem(Icons.Default.Tv, "TV", currentSection == "tv_shows", { soundManager.playSound(SoundManager.Sound.CLICK); onTvShowsClick() })
                BottomNavItem(Icons.Default.Settings, "Settings", currentSection == "settings", { soundManager.playSound(SoundManager.Sound.CLICK); onSettingsClick() })
                
                // Profile Item
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onProfileClick)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentProfile != null) {
                        val avatarResourceId = context.resources.getIdentifier(currentProfile.avatarImage, "drawable", context.packageName)
                        if (avatarResourceId != 0) {
                            Image(painterResource(avatarResourceId), null, Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(28.dp).background(Color(0xFF8B5CF6), CircleShape), contentAlignment = Alignment.Center) {
                                Text(currentProfile.name.take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
        label = "icon_color"
    )
    
    val indicatorScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300),
        label = "indicator_scale"
    )

    Column(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick, interactionSource = remember { MutableInteractionSource() }, indication = null),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
        if (isSelected) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .scale(indicatorScale)
                    .background(Color(0xFF8B5CF6), CircleShape)
            )
        }
    }
}
