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
import androidx.compose.ui.focus.*
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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusGroup
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
    // Focus requesters
    val searchFocusRequester = remember { FocusRequester() }
    // sidebarFocusRequester will be used for the Home button
    val moviesFocusRequester = remember { FocusRequester() }
    val tvShowsFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .padding(vertical = 24.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Panel (Now Transparent)
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .width(56.dp)
                .focusGroup()
                .focusProperties { end = heroPlayButtonFocusRequester },
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SidebarIconButton(
                icon = Icons.Default.Search,
                isSelected = currentSection == "search",
                onClick = onSearchClick,
                focusRequester = searchFocusRequester,
                modifier = Modifier.focusProperties { down = sidebarFocusRequester }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Home,
                isSelected = currentSection == "home",
                onClick = onHomeClick,
                focusRequester = sidebarFocusRequester,
                modifier = Modifier.focusProperties { up = searchFocusRequester; down = moviesFocusRequester }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Movie,
                isSelected = currentSection == "movies",
                onClick = onMoviesClick,
                focusRequester = moviesFocusRequester,
                modifier = Modifier.focusProperties { up = sidebarFocusRequester; down = tvShowsFocusRequester }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Tv,
                isSelected = currentSection == "tv_shows",
                onClick = onTvShowsClick,
                focusRequester = tvShowsFocusRequester,
                modifier = Modifier.focusProperties { up = moviesFocusRequester; down = settingsFocusRequester }
            )
            
            SidebarIconButton(
                icon = Icons.Default.Settings,
                isSelected = currentSection == "settings",
                onClick = onSettingsClick,
                focusRequester = settingsFocusRequester,
                modifier = Modifier.focusProperties { up = tvShowsFocusRequester; down = profileFocusRequester }
            )
            
            ProfileIconButton(
                currentProfile = currentProfile,
                onClick = onProfileClick,
                focusRequester = profileFocusRequester,
                modifier = Modifier.focusProperties { up = settingsFocusRequester }
            )
        }
    }
}

@Composable
private fun SidebarIconButton(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        label = "btn_bg"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .background(bgColor, CircleShape)
            .then(if (isSelected && !isFocused) Modifier.border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f), CircleShape) else Modifier)
            .clip(CircleShape)
            .focusRequester(focusRequester)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged {  }
            .clickable(interactionSource = interactionSource, indication = null) {
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else if (isSelected) Color(0xFFC4B5FD) else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ProfileIconButton(
    currentProfile: Profile?,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    Box(
        modifier = modifier
            .size(44.dp)
            .background(if (isFocused) Color.White else Color.Transparent, CircleShape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                shape = CircleShape
            )
            .clip(CircleShape)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged {  }
            .clickable(interactionSource = interactionSource, indication = null) {
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (currentProfile != null) {
            val avatarResourceId = context.resources.getIdentifier(currentProfile.avatarImage, "drawable", context.packageName)
            if (avatarResourceId != 0) {
                Image(
                    painter = painterResource(avatarResourceId),
                    contentDescription = null,
                    modifier = Modifier.size(if (isFocused) 36.dp else 32.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.size(32.dp).background(Color(0xFF8B5CF6), CircleShape), contentAlignment = Alignment.Center) {
                    Text(currentProfile.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}