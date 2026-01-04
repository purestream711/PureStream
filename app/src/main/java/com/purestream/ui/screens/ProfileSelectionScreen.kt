package com.purestream.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.purestream.data.model.Profile
import com.purestream.data.model.ProfileType
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.ui.theme.tvCircularFocusIndicator
import com.purestream.ui.theme.animatedProfileBorder
import com.purestream.utils.rememberIsMobile
import com.purestream.utils.LevelCalculator
import com.purestream.utils.SoundManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProfileSelectionScreen(
    profiles: List<Profile>,
    onProfileSelect: (Profile) -> Unit,
    onCreateProfile: () -> Unit,
    onDeleteProfile: (Profile) -> Unit,
    onEditProfile: (Profile) -> Unit = {},
    onBackClick: () -> Unit,
    isPremium: Boolean = false
) {
    val isMobile = rememberIsMobile()
    var showDeleteMode by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<Profile?>(null) }
    
    // Animation state
    var contentVisible by remember { mutableStateOf(false) }
    
    // Entrance animations
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "content_alpha"
    )
    val contentScale by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0.92f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "content_scale"
    )

    // Trigger entrance
    LaunchedEffect(Unit) {
        delay(100)
        contentVisible = true
    }

    // Intercept back button to trigger proper cleanup in MainActivity
    androidx.activity.compose.BackHandler {
        onBackClick()
    }

    // Focus requesters for navigation
    val manageButtonFocusRequester = remember { FocusRequester() }
    val firstProfileFocusRequester = remember { FocusRequester() }
    val createProfileFocusRequester = remember { FocusRequester() }
    val profileEditButtonFocusRequesters = remember(profiles.size) {
        List(profiles.size) { FocusRequester() }
    }
    val profileDeleteButtonFocusRequesters = remember(profiles.size) {
        List(profiles.size) { FocusRequester() }
    }

    // Handle profile deletion with proper focus management
    LaunchedEffect(profileToDelete) {
        profileToDelete?.let { profile ->
            val remainingProfiles = profiles.filter { it.id != profile.id }
            val validProfilesCount = if (isPremium) {
                remainingProfiles.size
            } else {
                remainingProfiles.count { it.profileType != ProfileType.CHILD }
            }

            if (validProfilesCount == 0) {
                showDeleteMode = false
                onDeleteProfile(profile)
                delay(100)
                try {
                    createProfileFocusRequester.requestFocus()
                } catch (e: Exception) {
                    android.util.Log.w("ProfileSelectionScreen", "Failed to focus Create button: ${e.message}")
                }
            } else {
                try {
                    manageButtonFocusRequester.requestFocus()
                } catch (e: Exception) {
                    android.util.Log.w("ProfileSelectionScreen", "Failed to focus Cancel button: ${e.message}")
                }
                
                onDeleteProfile(profile)
                delay(100)
                try {
                    manageButtonFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            profileToDelete = null
        }
    }

    // Set initial focus
    LaunchedEffect(profiles, isPremium, showDeleteMode) {
        if (!showDeleteMode) {
            val validProfiles = if (isPremium) profiles else profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
            try {
                if (validProfiles.isNotEmpty()) {
                    firstProfileFocusRequester.requestFocus()
                } else {
                    createProfileFocusRequester.requestFocus()
                }
            } catch (e: Exception) {
                android.util.Log.w("ProfileSelectionScreen", "Failed to set initial focus: ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF0D0D0D))
                )
            )
    ) {
        // Pulsating Background Glow
        val infiniteTransition = rememberInfiniteTransition(label = "bg_glow")
        val glowAlpha by infiniteTransition.animateFloat(
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
                .padding(if (isMobile) 24.dp else 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Who's Watching?",
                fontSize = if (isMobile) 32.sp else 44.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (isMobile) 64.dp else 40.dp)
            )

            Spacer(modifier = Modifier.height(if (isMobile) 64.dp else 64.dp))

            val validProfiles = if (isPremium) profiles else profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
            
            if (profiles.isEmpty() || validProfiles.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (profiles.isEmpty()) "Create your first profile to get started." else "Create an adult profile to get started.",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    CreateProfileCard(onClick = onCreateProfile, focusRequester = createProfileFocusRequester)
                }
            } else {
                if (isMobile) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val profilesToShow = if (isPremium) profiles else profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
                            items(profilesToShow.size) { index ->
                                ProfileCard(
                                    profile = profilesToShow[index],
                                    showDeleteMode = showDeleteMode,
                                    onClick = { onProfileSelect(profilesToShow[index]) },
                                    onDelete = { profileToDelete = profilesToShow[index] },
                                    onEdit = { onEditProfile(profilesToShow[index]) },
                                    editButtonFocusRequester = profileEditButtonFocusRequesters[index],
                                    deleteButtonFocusRequester = profileDeleteButtonFocusRequesters[index],
                                    manageButtonFocusRequester = manageButtonFocusRequester,
                                    nextEditButtonFocusRequester = profileEditButtonFocusRequesters.getOrNull(index + 1),
                                    prevDeleteButtonFocusRequester = profileDeleteButtonFocusRequesters.getOrNull(index - 1),
                                    profileFocusRequester = if (index == 0) firstProfileFocusRequester else null,
                                    delayIndex = index,
                                    isMobile = true
                                )
                            }
                            if (isPremium || profilesToShow.size < 1) {
                                item { CreateProfileCard(onClick = onCreateProfile, isMobile = true) }
                            }
                        }
                        
                        Box(modifier = Modifier.padding(bottom = 32.dp)) {
                            ManageButton(showDeleteMode, manageButtonFocusRequester, { showDeleteMode = !showDeleteMode }, isMobile = true)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            contentPadding = PaddingValues(horizontal = 40.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val profilesToShow = if (isPremium) profiles else profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
                            items(profilesToShow.size) { index ->
                                ProfileCard(
                                    profile = profilesToShow[index],
                                    showDeleteMode = showDeleteMode,
                                    onClick = { onProfileSelect(profilesToShow[index]) },
                                    onDelete = { profileToDelete = profilesToShow[index] },
                                    onEdit = { onEditProfile(profilesToShow[index]) },
                                    editButtonFocusRequester = profileEditButtonFocusRequesters[index],
                                    deleteButtonFocusRequester = profileDeleteButtonFocusRequesters[index],
                                    manageButtonFocusRequester = manageButtonFocusRequester,
                                    nextEditButtonFocusRequester = profileEditButtonFocusRequesters.getOrNull(index + 1),
                                    prevDeleteButtonFocusRequester = profileDeleteButtonFocusRequesters.getOrNull(index - 1),
                                    profileFocusRequester = if (index == 0) firstProfileFocusRequester else null,
                                    delayIndex = index
                                )
                            }
                            if (isPremium || profilesToShow.size < 1) {
                                item { CreateProfileCard(onClick = onCreateProfile, focusRequester = if (profilesToShow.isEmpty()) createProfileFocusRequester else null) }
                            }
                        }
                        Spacer(modifier = Modifier.height(64.dp))
                        ManageButton(showDeleteMode, manageButtonFocusRequester, { showDeleteMode = !showDeleteMode })
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageButton(
    showDeleteMode: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    isMobile: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val soundManager = remember { com.purestream.utils.SoundManager.getInstance(context) }
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFF8B5CF6)
            showDeleteMode -> Color(0xFFDC2626)
            else -> Color.White.copy(alpha = 0.1f)
        },
        label = "btn_bg"
    )

    Button(
        onClick = {
            soundManager.playSound(com.purestream.utils.SoundManager.Sound.CLICK)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = if (isFocused || showDeleteMode) Color.White else Color.White.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(6.dp), // Scaled corner
        modifier = Modifier
            .height(if (isMobile) 29.dp else 22.dp) // 30% larger on mobile
            .padding(horizontal = 16.dp)
            .focusRequester(focusRequester)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource),
        contentPadding = PaddingValues(horizontal = 12.dp) // Reduced padding
    ) {
        Text(
            text = if (showDeleteMode) "DONE" else "MANAGE PROFILES",
            fontSize = if (isMobile) 12.sp else 9.sp, // Larger font on mobile
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ProfileCard(
    profile: Profile,
    showDeleteMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    editButtonFocusRequester: FocusRequester? = null,
    deleteButtonFocusRequester: FocusRequester? = null,
    manageButtonFocusRequester: FocusRequester? = null,
    nextEditButtonFocusRequester: FocusRequester? = null,
    prevDeleteButtonFocusRequester: FocusRequester? = null,
    profileFocusRequester: FocusRequester? = null,
    delayIndex: Int = 0,
    isMobile: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200 + (delayIndex * 100)),
        label = "item_alpha"
    )
    
    LaunchedEffect(Unit) { visible = true }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(120.dp) // 75% of 160dp
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (!showDeleteMode) {
                    Modifier
                        .then(if (profileFocusRequester != null) Modifier.focusRequester(profileFocusRequester) else Modifier)
                        .hoverable(interactionSource)
                        .focusable(interactionSource = interactionSource)
                        .clickable { 
                            soundManager.playSound(SoundManager.Sound.CLICK)
                            onClick() 
                        }
                } else Modifier
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(105.dp) // 75% of 140dp
                .background(
                    color = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(18.dp) // Scaled
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(9.dp) // 75% of 12dp
        ) {
            val avatarResourceId = remember(profile.avatarImage) {
                context.resources.getIdentifier(profile.avatarImage, "drawable", context.packageName)
            }

            if (avatarResourceId != 0) {
                Image(
                    painter = painterResource(id = avatarResourceId),
                    contentDescription = profile.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), // Scaled
                    contentScale = ContentScale.Crop
                )
            }
            
            if (showDeleteMode) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(24.dp)) // Scaled
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp)) // Scaled

        Text(
            text = profile.name,
            fontSize = 14.sp, // ~75% of 18sp
            fontWeight = FontWeight.Bold,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val level = LevelCalculator.calculateLevel(profile.totalFilteredWordsCount).first
            Surface(
                color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
            ) {
                Text(
                    text = "LVL $level",
                    fontSize = 9.sp, // ~75% of 11sp
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFC4B5FD),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Surface(
                color = if (profile.profileType == ProfileType.ADULT) Color(0xFF3B82F6).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (profile.profileType == ProfileType.ADULT) Color(0xFF3B82F6).copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.3f))
            ) {
                Text(
                    text = profile.profileType.name,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (profile.profileType == ProfileType.ADULT) Color(0xFF93C5FD) else Color(0xFF6EE7B7),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        if (showDeleteMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val buttonSize = if (isMobile) 31.dp else 24.dp
                val iconSize = if (isMobile) 18.dp else 14.dp
                
                val editInteractionSource = remember { MutableInteractionSource() }
                val isEditFocused by editInteractionSource.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .background(if (isEditFocused) Color(0xFF8B5CF6) else Color(0xFF2563EB), CircleShape)
                        .clip(CircleShape)
                        .focusRequester(editButtonFocusRequester ?: FocusRequester())
                        .focusable(interactionSource = editInteractionSource)
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(iconSize))
                }
                
                val deleteInteractionSource = remember { MutableInteractionSource() }
                val isDeleteFocused by deleteInteractionSource.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .background(if (isDeleteFocused) Color(0xFF8B5CF6) else Color(0xFFDC2626), CircleShape)
                        .clip(CircleShape)
                        .focusRequester(deleteButtonFocusRequester ?: FocusRequester())
                        .focusable(interactionSource = deleteInteractionSource)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(iconSize))
                }
            }
        }
    }
}

@Composable
fun CreateProfileCard(
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    isMobile: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(120.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                soundManager.playSound(SoundManager.Sound.CLICK)
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .size(105.dp)
                .background(
                    color = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(18.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Profile",
                modifier = Modifier.size(30.dp),
                tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.4f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Add Profile",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}
