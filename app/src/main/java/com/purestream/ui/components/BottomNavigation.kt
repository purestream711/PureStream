package com.purestream.ui.components

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
    // Animate the visibility with smooth slide animation
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 200f, // 200dp slide down when hidden
        animationSpec = tween(durationMillis = 300),
        label = "bottom_nav_offset"
    )

    // Animate transparency - fully transparent when hidden
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 0.9f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "bottom_nav_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.roundToInt()) },
        color = Color.Black.copy(alpha = alpha),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Search,
                label = "Search",
                isSelected = currentSection == "search",
                onClick = onSearchClick,
                modifier = Modifier.weight(1f)
            )
            
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentSection == "home",
                onClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
            
            BottomNavItem(
                icon = Icons.Default.Movie,
                label = "Movies",
                isSelected = currentSection == "movies",
                onClick = onMoviesClick,
                modifier = Modifier.weight(1f)
            )
            
            BottomNavItem(
                icon = Icons.Default.Tv,
                label = "TV Shows",
                isSelected = currentSection == "tv_shows",
                onClick = onTvShowsClick,
                modifier = Modifier.weight(1f)
            )
            
            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = currentSection == "settings",
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            )
            
            BottomNavProfileItem(
                currentProfile = currentProfile,
                onClick = onProfileClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    Column(
        modifier = modifier
            .clickable { 
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick() 
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF9CA3AF),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun BottomNavProfileItem(
    currentProfile: Profile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    Column(
        modifier = modifier
            .clickable { 
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick() 
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (currentProfile != null) {
            val avatarResourceId = context.resources.getIdentifier(
                currentProfile.avatarImage, 
                "drawable", 
                context.packageName
            )
            
            if (avatarResourceId != 0) {
                Image(
                    painter = painterResource(id = avatarResourceId),
                    contentDescription = "${currentProfile.name} Avatar",
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback to text avatar if image not found
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = Color(0xFF6366F1),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentProfile.name.take(1).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else {
            // Default profile icon when no profile is selected
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = Color(0xFF6B7280),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}