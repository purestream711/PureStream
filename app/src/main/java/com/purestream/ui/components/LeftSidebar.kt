package com.purestream.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.purestream.data.model.Profile
import com.purestream.ui.theme.tvIconFocusIndicator
import com.purestream.ui.theme.animatedNavigationIcon
import com.purestream.ui.theme.getAnimatedNavigationIconColor
import com.purestream.ui.theme.animatedProfileBorder
import com.purestream.utils.SoundManager

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LeftSidebar(
    currentProfile: Profile?,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoviesClick: () -> Unit,
    onTvShowsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    sidebarFocusRequester: FocusRequester,
    heroPlayButtonFocusRequester: FocusRequester,
    currentSection: String = "home", // Track current section for highlighting
    modifier: Modifier = Modifier
) {
    // Focus requesters for each sidebar item
    val searchFocusRequester = remember { FocusRequester() }
    val homeFocusRequester = remember { FocusRequester() }
    val moviesFocusRequester = remember { FocusRequester() }
    val tvShowsFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }
    
    Column(
        modifier = modifier
            .background(Color.Transparent)
            .padding(start = 6.dp, end = 6.dp)
            .focusRequester(sidebarFocusRequester)
            .focusProperties {
                right = heroPlayButtonFocusRequester
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Navigation Items - Icons only
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            SidebarIconButton(
                icon = Icons.Default.Search,
                isSelected = currentSection == "search",
                onClick = onSearchClick,
                focusRequester = searchFocusRequester,
                modifier = Modifier.focusProperties {
                    down = homeFocusRequester
                }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Home,
                isSelected = currentSection == "home",
                onClick = onHomeClick,
                focusRequester = homeFocusRequester,
                modifier = Modifier.focusProperties {
                    up = searchFocusRequester
                    down = moviesFocusRequester
                }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Movie,
                isSelected = currentSection == "movies",
                onClick = onMoviesClick,
                focusRequester = moviesFocusRequester,
                modifier = Modifier.focusProperties {
                    up = homeFocusRequester
                    down = tvShowsFocusRequester
                }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Tv,
                isSelected = currentSection == "tv_shows",
                onClick = onTvShowsClick,
                focusRequester = tvShowsFocusRequester,
                modifier = Modifier.focusProperties {
                    up = moviesFocusRequester
                    down = settingsFocusRequester
                }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Settings,
                isSelected = currentSection == "settings",
                onClick = onSettingsClick,
                focusRequester = settingsFocusRequester,
                modifier = Modifier.focusProperties {
                    up = tvShowsFocusRequester
                    down = profileFocusRequester
                }
            )
            
            // Profile Icon - Inline with other icons
            ProfileIconButton(
                currentProfile = currentProfile,
                onClick = onProfileClick,
                focusRequester = profileFocusRequester,
                modifier = Modifier.focusProperties {
                    up = settingsFocusRequester
                }
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SidebarIconButton(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    val animatedIconColor = getAnimatedNavigationIconColor(
        interactionSource = interactionSource,
        defaultColor = if (isSelected || isFocused) Color.White else Color(0xFF9CA3AF)
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .background(Color.Transparent)
            .clip(RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                
                // Play sound when gaining focus (not when losing focus)
                if (!wasFocused && focusState.isFocused) {
                    android.util.Log.d("LeftSidebar", "Sidebar icon gained focus - playing MOVE sound")
                    soundManager.playSound(SoundManager.Sound.MOVE)
                }
            }
            .clickable { 
                android.util.Log.d("LeftSidebar", "Sidebar icon clicked - playing CLICK sound")
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick() 
            }
            .tvIconFocusIndicator(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = animatedIconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProfileIconButton(
    currentProfile: Profile?,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .animatedProfileBorder(interactionSource = interactionSource)
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                
                // Play sound when gaining focus (not when losing focus)
                if (!wasFocused && focusState.isFocused) {
                    android.util.Log.d("LeftSidebar", "Profile icon gained focus - playing MOVE sound")
                    soundManager.playSound(SoundManager.Sound.MOVE)
                }
            }
            .clickable { 
                android.util.Log.d("LeftSidebar", "Profile icon clicked - playing CLICK sound")
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick() 
            }
            .tvIconFocusIndicator(),
        contentAlignment = Alignment.Center
    ) {
        if (currentProfile != null) {
            val context = LocalContext.current
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
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback to text avatar if image not found
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = Color(0xFF6366F1),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentProfile.name.take(2).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        } else {
            // Default profile icon when no profile is selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = Color(0xFF6B7280),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}