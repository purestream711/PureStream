package com.purestream.ui.screens

import androidx.compose.animation.animateColorAsState // Added for smooth color transitions
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.hoverable
import com.purestream.ui.theme.tvCircularFocusIndicator
import com.purestream.ui.theme.animatedProfileBorder
// com.purestream.ui.theme.getAnimatedButtonBackgroundColor (Removed usage to customize colors)
import com.purestream.data.model.Profile
import com.purestream.data.model.ProfileType
import com.purestream.data.model.ProfanityFilterLevel
import com.purestream.ui.theme.NetflixDarkGray
import com.purestream.utils.rememberIsMobile
import androidx.compose.foundation.lazy.LazyColumn

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

    // Set initial focus to first profile or create profile button
    LaunchedEffect(profiles, isPremium) {
        val validProfiles = if (isPremium) {
            profiles
        } else {
            profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
        }
        if (validProfiles.isNotEmpty()) {
            firstProfileFocusRequester.requestFocus()
        } else {
            // When no valid profiles exist, focus the "Add Profile" button
            createProfileFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Who's Watching?",
                fontSize = if (isMobile) 48.sp else 40.sp, // Reduce font size on TV for better spacing
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )

            Spacer(modifier = Modifier.height(if (isMobile) 64.dp else 48.dp)) // Reduce spacing on TV for better layout

            // Profiles Grid - check if we have valid profiles after premium filtering
            val validProfiles = if (isPremium) {
                profiles
            } else {
                profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
            }
            if (profiles.isEmpty() || validProfiles.isEmpty()) {
                // No profiles - show create prompt
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (profiles.isEmpty()) {
                            "No profiles found. Create your first profile to get started."
                        } else {
                            "No available profiles. Create an adult profile to get started."
                        },
                        fontSize = 20.sp,
                        color = Color(0xFFB3B3B3),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    CreateProfileCard(
                        onClick = onCreateProfile,
                        focusRequester = createProfileFocusRequester
                    )
                }
            } else {
                // Show existing profiles - responsive layout
                if (isMobile) {
                    // Mobile: Vertical stacking with LazyColumn
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Limit profile access for free users - filter out child profiles and limit adult profiles to 1
                        val profilesToShow = if (isPremium) {
                            profiles
                        } else {
                            profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
                        }
                        items(profilesToShow.size) { index ->
                            val profile = profilesToShow[index]
                            ProfileCard(
                                profile = profile,
                                showDeleteMode = showDeleteMode,
                                onClick = { onProfileSelect(profile) },
                                onDelete = { onDeleteProfile(profile) },
                                onEdit = { onEditProfile(profile) },
                                editButtonFocusRequester = profileEditButtonFocusRequesters[index],
                                deleteButtonFocusRequester = profileDeleteButtonFocusRequesters[index],
                                manageButtonFocusRequester = manageButtonFocusRequester,
                                nextEditButtonFocusRequester = profileEditButtonFocusRequesters.getOrNull(index + 1),
                                prevDeleteButtonFocusRequester = profileDeleteButtonFocusRequesters.getOrNull(index - 1),
                                profileFocusRequester = if (index == 0) firstProfileFocusRequester else null
                            )
                        }

                        // Add new profile option - always show for premium, or for free users if they have space
                        if (isPremium || profilesToShow.size < 1) {
                            item {
                                CreateProfileCard(onClick = onCreateProfile)
                            }
                        }

                        // Manage Profiles button - mobile only (inside scrollable content)
                        if (profilesToShow.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(32.dp))

                                val manageButtonInteractionSource = remember { MutableInteractionSource() }
                                val isFocused by manageButtonInteractionSource.collectIsFocusedAsState()

                                val manageButtonBackgroundColor by animateColorAsState(
                                    targetValue = if (isFocused) {
                                        Color(0xFF8B5CF6) // Purple when focused
                                    } else {
                                        if (showDeleteMode) Color(0xFFDC2626) else Color(0xFF6B7280) // Red/Grey when not
                                    },
                                    label = "manageButtonColor"
                                )

                                Button(
                                    onClick = { showDeleteMode = !showDeleteMode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = manageButtonBackgroundColor,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .padding(bottom = 24.dp)
                                        .focusRequester(manageButtonFocusRequester)
                                        .focusProperties {
                                            up = if (showDeleteMode && profileEditButtonFocusRequesters.isNotEmpty()) {
                                                profileEditButtonFocusRequesters[0]
                                            } else {
                                                FocusRequester.Default
                                            }
                                        }
                                        .hoverable(manageButtonInteractionSource)
                                        .focusable(interactionSource = manageButtonInteractionSource)
                                ) {
                                    Text(if (showDeleteMode) "Cancel" else "Manage Profiles")
                                }
                            }
                        }
                    }
                } else {
                    // TV: Horizontal scrolling with LazyRow (original layout)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp)
                    ) {
                        // Limit profile access for free users - filter out child profiles and limit adult profiles to 1
                        val profilesToShow = if (isPremium) {
                            profiles
                        } else {
                            profiles.filter { it.profileType != ProfileType.CHILD }.take(1)
                        }
                        items(profilesToShow.size) { index ->
                            val profile = profilesToShow[index]
                            ProfileCard(
                                profile = profile,
                                showDeleteMode = showDeleteMode,
                                onClick = { onProfileSelect(profile) },
                                onDelete = { onDeleteProfile(profile) },
                                onEdit = { onEditProfile(profile) },
                                editButtonFocusRequester = profileEditButtonFocusRequesters[index],
                                deleteButtonFocusRequester = profileDeleteButtonFocusRequesters[index],
                                manageButtonFocusRequester = manageButtonFocusRequester,
                                nextEditButtonFocusRequester = profileEditButtonFocusRequesters.getOrNull(index + 1),
                                prevDeleteButtonFocusRequester = profileDeleteButtonFocusRequesters.getOrNull(index - 1),
                                profileFocusRequester = if (index == 0) firstProfileFocusRequester else null
                            )
                        }

                        // Add new profile option - always show for premium, or for free users if they have space
                        if (isPremium || profilesToShow.size < 1) {
                            item {
                                CreateProfileCard(onClick = onCreateProfile)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Manage Profiles button with animation - TV only (mobile has it inside LazyColumn)
                if (validProfiles.isNotEmpty() && !isMobile) {
                    val manageButtonInteractionSource = remember { MutableInteractionSource() }
                    val isFocused by manageButtonInteractionSource.collectIsFocusedAsState()

                    // UPDATED: Logic for Manage/Cancel Button
                    val manageButtonBackgroundColor by animateColorAsState(
                        targetValue = if (isFocused) {
                            Color(0xFF8B5CF6) // Purple when focused
                        } else {
                            if (showDeleteMode) Color(0xFFDC2626) else Color(0xFF6B7280) // Original colors
                        },
                        label = "manageButtonColor"
                    )

                    Button(
                        onClick = { showDeleteMode = !showDeleteMode },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = manageButtonBackgroundColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                            .focusRequester(manageButtonFocusRequester)
                            .focusProperties {
                                up = if (showDeleteMode && profileEditButtonFocusRequesters.isNotEmpty()) {
                                    profileEditButtonFocusRequesters[0]
                                } else {
                                    FocusRequester.Default
                                }
                            }
                            .hoverable(manageButtonInteractionSource)
                            .focusable(interactionSource = manageButtonInteractionSource)
                    ) {
                        Text(if (showDeleteMode) "Cancel" else "Manage Profiles")
                    }
                }
            }
        }
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
    profileFocusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(160.dp)
            .then(
                if (!showDeleteMode) {
                    // Normal mode: full interactivity
                    Modifier
                        .then(
                            if (profileFocusRequester != null) {
                                Modifier.focusRequester(profileFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .hoverable(interactionSource)
                        .focusable(interactionSource = interactionSource)
                        .tvCircularFocusIndicator(focusScale = 1.1f)
                        .clickable { onClick() }
                } else {
                    // Manage mode: completely non-interactive, no focus, no click
                    Modifier
                }
            )
    ) {
        // Profile Avatar with animated border (only when not in delete mode)
        val context = LocalContext.current
        val avatarResourceId = context.resources.getIdentifier(
            profile.avatarImage,
            "drawable",
            context.packageName
        )

        if (avatarResourceId != 0) {
            Image(
                painter = painterResource(id = avatarResourceId),
                contentDescription = "${profile.name} Avatar",
                modifier = Modifier
                    .size(120.dp)
                    .then(
                        if (!showDeleteMode) {
                            Modifier.animatedProfileBorder(interactionSource = interactionSource)
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback to text avatar if image not found
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .then(
                        if (!showDeleteMode) {
                            Modifier.animatedProfileBorder(interactionSource = interactionSource)
                        } else {
                            Modifier
                        }
                    )
                    .background(
                        color = Color(0xFF6366F1),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.take(2).uppercase(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Name
        Text(
            text = profile.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // Profile Type Badge
        Text(
            text = if (profile.profileType == ProfileType.CHILD) "Child" else "Adult",
            fontSize = 12.sp,
            color = if (profile.profileType == ProfileType.CHILD) Color(0xFF10B981) else Color(0xFF6366F1),
            modifier = Modifier
                .background(
                    color = if (profile.profileType == ProfileType.CHILD)
                        Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF6366F1).copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Filter Level
        Text(
            text = when (profile.profanityFilterLevel) {
                ProfanityFilterLevel.NONE -> "No Filter"
                ProfanityFilterLevel.MILD -> "Mild Filter"
                ProfanityFilterLevel.MODERATE -> "Moderate Filter"
                ProfanityFilterLevel.STRICT -> "Strict Filter"
            },
            fontSize = 10.sp,
            color = Color(0xFFB3B3B3),
            textAlign = TextAlign.Center
        )

        // Edit and Delete buttons beneath profile when in manage mode
        if (showDeleteMode) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Edit button with animation
                val editButtonInteractionSource = remember { MutableInteractionSource() }
                val isEditFocused by editButtonInteractionSource.collectIsFocusedAsState()

                // UPDATED: Purple when focused, Blue when not
                val editButtonBackgroundColor by animateColorAsState(
                    targetValue = if (isEditFocused) Color(0xFF8B5CF6) else Color(0xFF2563EB),
                    label = "editButtonColor"
                )

                Button(
                    onClick = {
                        android.util.Log.d("ProfileCard", "Edit button clicked for profile: ${profile.name}")
                        onEdit()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .then(
                            if (editButtonFocusRequester != null) {
                                Modifier
                                    .focusRequester(editButtonFocusRequester)
                                    .focusProperties {
                                        right = deleteButtonFocusRequester ?: FocusRequester.Default
                                        left = prevDeleteButtonFocusRequester ?: FocusRequester.Default
                                        down = manageButtonFocusRequester ?: FocusRequester.Default
                                    }
                            } else {
                                Modifier
                            }
                        )
                        .hoverable(editButtonInteractionSource)
                        .focusable(interactionSource = editButtonInteractionSource),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = editButtonBackgroundColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete button with animation
                val deleteButtonInteractionSource = remember { MutableInteractionSource() }
                val isDeleteFocused by deleteButtonInteractionSource.collectIsFocusedAsState()

                // UPDATED: Purple when focused, Red when not
                val deleteButtonBackgroundColor by animateColorAsState(
                    targetValue = if (isDeleteFocused) Color(0xFF8B5CF6) else Color(0xFFDC2626),
                    label = "deleteButtonColor"
                )

                Button(
                    onClick = {
                        android.util.Log.d("ProfileCard", "Delete button clicked for profile: ${profile.name}")
                        onDelete()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .then(
                            if (deleteButtonFocusRequester != null) {
                                Modifier
                                    .focusRequester(deleteButtonFocusRequester)
                                    .focusProperties {
                                        left = editButtonFocusRequester ?: FocusRequester.Default
                                        right = nextEditButtonFocusRequester ?: FocusRequester.Default
                                        down = manageButtonFocusRequester ?: FocusRequester.Default
                                    }
                            } else {
                                Modifier
                            }
                        )
                        .hoverable(deleteButtonInteractionSource)
                        .focusable(interactionSource = deleteButtonInteractionSource),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = deleteButtonBackgroundColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Profile",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateProfileCard(
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(160.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .tvCircularFocusIndicator(focusScale = 1.1f)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = Color(0xFF374151),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Profile",
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFB3B3B3)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Add Profile",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFB3B3B3),
            textAlign = TextAlign.Center
        )
    }
}