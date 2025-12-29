package com.purestream.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.purestream.data.model.*
import com.purestream.data.repository.ProfileRepository
import com.purestream.ui.viewmodel.EditProfileViewModel
import com.purestream.ui.components.*
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.ui.theme.NetflixDarkGray
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profileId: String,
    onNavigateBack: () -> Unit,
    onProfileUpdated: (Profile) -> Unit,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {
    val context = LocalContext.current
    val profileRepository = remember { ProfileRepository(context) }
    
    val editProfileViewModel: EditProfileViewModel = viewModel { 
        EditProfileViewModel(
            context = context, 
            profileId = profileId,
            profileRepository = profileRepository
        )
    }
    val uiState by editProfileViewModel.uiState.collectAsStateWithLifecycle()
    val nameFocusRequester = remember { FocusRequester() }
    val bearAvatarFocusRequester = remember { FocusRequester() }
    val adultProfileTypeFocusRequester = remember { FocusRequester() }
    val defaultProfileFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && uiState.profile != null) {
            nameFocusRequester.requestFocus()
        }
    }
    
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    if (uiState.profile == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Profile not found", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                val goBackButtonInteractionSource = remember { MutableInteractionSource() }
                val goBackButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                    interactionSource = goBackButtonInteractionSource,
                    defaultColor = MaterialTheme.colorScheme.primary
                )
                
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .hoverable(goBackButtonInteractionSource)
                        .focusable(interactionSource = goBackButtonInteractionSource),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = goBackButtonBackgroundColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(32.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = com.purestream.ui.theme.TextPrimary,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
            
            // Profile Name Input
            item {
                ProfileSection(title = "Profile Name") {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = editProfileViewModel::updateName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                            .focusProperties {
                                down = bearAvatarFocusRequester
                            },
                        placeholder = {
                            Text(
                                text = "Enter profile name",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            
            // Avatar Image Selection
            item {
                ProfileSection(title = "Choose Avatar") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(editProfileViewModel.avatarImages) { avatarImage ->
                            AvatarImageSelectionButton(
                                avatarImage = avatarImage,
                                isSelected = avatarImage == uiState.selectedAvatarImage,
                                onClick = { editProfileViewModel.updateSelectedAvatarImage(avatarImage) },
                                modifier = if (avatarImage == "bear_avatar") {
                                    Modifier.focusRequester(bearAvatarFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
            
            // Profile Type Selection
            item {
                ProfileSection(title = "Profile Type") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileTypeButton(
                            type = ProfileType.ADULT,
                            isSelected = uiState.profileType == ProfileType.ADULT,
                            onClick = { editProfileViewModel.updateProfileType(ProfileType.ADULT) },
                            isLocked = false,
                            isPremium = isPremium,
                            modifier = Modifier
                                .focusRequester(adultProfileTypeFocusRequester)
                                .focusProperties {
                                    down = defaultProfileFocusRequester
                                }
                        )
                        ProfileTypeButton(
                            type = ProfileType.CHILD,
                            isSelected = uiState.profileType == ProfileType.CHILD,
                            onClick = { editProfileViewModel.updateProfileType(ProfileType.CHILD) },
                            isLocked = true,
                            isPremium = isPremium,
                            modifier = Modifier
                                .focusProperties {
                                    down = defaultProfileFocusRequester
                                }
                        )
                    }
                }
            }

            // Default Profile Toggle
            item {
                ProfileSection(title = "Default Profile") {
                    val defaultProfileInteractionSource = remember { MutableInteractionSource() }
                    val isDefaultProfileFocused by defaultProfileInteractionSource.collectIsFocusedAsState()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(defaultProfileFocusRequester)
                            .focusProperties {
                                up = adultProfileTypeFocusRequester
                            }
                            .focusable(interactionSource = defaultProfileInteractionSource)
                            .then(
                                if (isDefaultProfileFocused) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = Color(0xFF8B5CF6),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = com.purestream.ui.theme.BackgroundSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Automatically sign into profile on startup",
                                fontSize = 14.sp,
                                color = com.purestream.ui.theme.TextSecondary,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Switch(
                                checked = uiState.isDefaultProfile,
                                onCheckedChange = { editProfileViewModel.updateIsDefaultProfile(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF8B5CF6),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFF4B5563)
                                )
                            )
                        }
                    }
                }
            }

            // Profanity Filter Level
            item {
                ProfileSection(title = "Content Filter Level") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Show explanation for child profiles
                        if (uiState.profileType == ProfileType.CHILD) {
                            Text(
                                text = "Child profiles are automatically set to Strict filtering for maximum safety.",
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        ProfanityFilterLevel.values().forEach { level ->
                            val isChildProfile = uiState.profileType == ProfileType.CHILD
                            val isFilterEditable = when {
                                isChildProfile -> level == ProfanityFilterLevel.STRICT // Only STRICT selectable for child
                                !isPremium -> level == ProfanityFilterLevel.MILD // Only MILD for free users
                                else -> true // All levels for premium adult profiles
                            }
                            
                            FilterLevelButton(
                                level = level,
                                isSelected = uiState.profanityFilterLevel == level,
                                onClick = { 
                                    if (isFilterEditable) {
                                        editProfileViewModel.updateProfanityFilterLevel(level) 
                                    }
                                },
                                isLocked = !isFilterEditable,
                                isPremium = isPremium
                            )
                        }
                    }
                }
            }
            
            // Library Selection
            item {
                ProfileSection(title = "Selected Libraries") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectAllButtonInteractionSource = remember { MutableInteractionSource() }
                            val selectAllButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                                interactionSource = selectAllButtonInteractionSource,
                                defaultColor = MaterialTheme.colorScheme.primary
                            )
                            
                            Button(
                                onClick = editProfileViewModel::selectAllLibraries,
                                modifier = Modifier
                                    .weight(1f)
                                    .hoverable(selectAllButtonInteractionSource)
                                    .focusable(interactionSource = selectAllButtonInteractionSource),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = selectAllButtonBackgroundColor,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Select All")
                            }
                            val clearAllButtonInteractionSource = remember { MutableInteractionSource() }
                            val clearAllButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                                interactionSource = clearAllButtonInteractionSource,
                                defaultColor = Color.Transparent
                            )
                            
                            OutlinedButton(
                                onClick = editProfileViewModel::clearLibrarySelection,
                                modifier = Modifier
                                    .weight(1f)
                                    .hoverable(clearAllButtonInteractionSource)
                                    .focusable(interactionSource = clearAllButtonInteractionSource),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = clearAllButtonBackgroundColor,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Clear All")
                            }
                        }
                        
                        if (uiState.availableLibraries.isEmpty()) {
                            Text(
                                text = "No libraries available. Make sure you're connected to your Plex server.",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            uiState.availableLibraries.forEach { library ->
                                LibrarySelectionItem(
                                    library = library,
                                    isSelected = uiState.selectedLibraries.contains(library.key),
                                    onClick = { editProfileViewModel.toggleLibrarySelection(library.key) }
                                )
                            }
                        }
                    }
                }
            }
            
            // Update Profile Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                val isEnabled = uiState.name.isNotBlank() && 
                               uiState.selectedLibraries.isNotEmpty() && 
                               !uiState.isUpdating
                
                val buttonText = when {
                    uiState.isUpdating -> "Updating..."
                    uiState.name.isBlank() -> "Enter Profile Name"
                    uiState.selectedLibraries.isEmpty() -> "Select Libraries"
                    else -> "Update Profile"
                }
                
                val updateButtonInteractionSource = remember { MutableInteractionSource() }
                val isFocused by updateButtonInteractionSource.collectIsFocusedAsState()

                // Define colors based on focus state (like Play button and Default button)
                val backgroundColor = if (isFocused) Color(0xFFF5B800) else Color(0xFF8B5CF6)
                val contentColor = if (isFocused) Color.Black else Color.White

                Button(
                    onClick = {
                        if (isEnabled) {
                            editProfileViewModel.updateProfile(
                                onSuccess = { profile ->
                                    onProfileUpdated(profile)
                                },
                                onError = { error ->
                                    // Error will be displayed in UI state via ViewModel
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .hoverable(updateButtonInteractionSource)
                        .focusable(interactionSource = updateButtonInteractionSource),
                    enabled = isEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEnabled) backgroundColor else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isEnabled) contentColor else MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (uiState.isUpdating) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Updating Profile...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // Error Display
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    val dismissButtonInteractionSource = remember { MutableInteractionSource() }
                    val dismissButtonBackgroundColor = getAnimatedButtonBackgroundColor(
                        interactionSource = dismissButtonInteractionSource,
                        defaultColor = Color.Transparent
                    )
                    
                    TextButton(
                        onClick = editProfileViewModel::clearError,
                        modifier = Modifier
                            .hoverable(dismissButtonInteractionSource)
                            .focusable(interactionSource = dismissButtonInteractionSource),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = dismissButtonBackgroundColor,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(
                            text = "Dismiss",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

