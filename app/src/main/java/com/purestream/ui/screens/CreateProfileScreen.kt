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
import com.purestream.ui.viewmodel.ProfileViewModel
import com.purestream.ui.components.*
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.ui.theme.NetflixDarkGray
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    onNavigateBack: () -> Unit,
    onProfileCreated: (Profile) -> Unit,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {
    val context = LocalContext.current
    
    val profileViewModel: ProfileViewModel = viewModel { 
        ProfileViewModel(context = context)
    }
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val nameFocusRequester = remember { FocusRequester() }
    val bearAvatarFocusRequester = remember { FocusRequester() }
    val adultProfileTypeFocusRequester = remember { FocusRequester() }
    val defaultProfileFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }
    
    // Special handling to ensure bear avatar gets focus when needed
    LaunchedEffect(uiState.selectedAvatarImage) {
        // If bear is selected and we're in the initial state, make sure it can receive focus
        if (uiState.selectedAvatarImage == "bear_avatar") {
            // Small delay to ensure avatar components are composed
            kotlinx.coroutines.delay(200)
        }
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
                Text(
                    text = "Create New Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = com.purestream.ui.theme.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                )
            }
            
            // Profile Name Input
            item {
                ProfileSection(title = "Profile Name") {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = profileViewModel::updateName,
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
                        items(profileViewModel.avatarImages) { avatarImage ->
                            AvatarImageSelectionButton(
                                avatarImage = avatarImage,
                                isSelected = avatarImage == uiState.selectedAvatarImage,
                                onClick = { profileViewModel.updateSelectedAvatarImage(avatarImage) },
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
                            onClick = { profileViewModel.updateProfileType(ProfileType.ADULT) },
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
                            onClick = { profileViewModel.updateProfileType(ProfileType.CHILD) },
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
                                onCheckedChange = { profileViewModel.updateIsDefaultProfile(it) },
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
                                        profileViewModel.updateProfanityFilterLevel(level) 
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
                ProfileSection(title = "Select Libraries") {
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
                                onClick = profileViewModel::selectAllLibraries,
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
                                onClick = profileViewModel::clearLibrarySelection,
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
                        
                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (uiState.availableLibraries.isEmpty()) {
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
                                    onClick = { profileViewModel.toggleLibrarySelection(library.key) }
                                )
                            }
                        }
                    }
                }
            }
            
            // Create Profile Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                val isEnabled = uiState.name.isNotBlank() && 
                               uiState.selectedLibraries.isNotEmpty() && 
                               !uiState.isCreating
                
                val buttonText = when {
                    uiState.isCreating -> "Creating..."
                    uiState.name.isBlank() -> "Enter Profile Name"
                    uiState.selectedLibraries.isEmpty() -> "Select Libraries"
                    else -> "Create Profile"
                }
                
                val createButtonInteractionSource = remember { MutableInteractionSource() }
                val isFocused by createButtonInteractionSource.collectIsFocusedAsState()

                // Define colors based on focus state (like Play button and Default button)
                val backgroundColor = if (isFocused) Color(0xFFF5B800) else Color(0xFF8B5CF6)
                val contentColor = if (isFocused) Color.Black else Color.White

                Button(
                    onClick = {
                        android.util.Log.d("CreateProfileScreen", "Create button clicked. Enabled: $isEnabled")
                        if (isEnabled) {
                            android.util.Log.d("CreateProfileScreen", "Creating profile with name: ${uiState.name}")
                            profileViewModel.createProfile(
                                onSuccess = { profile ->
                                    android.util.Log.d("CreateProfileScreen", "Profile created successfully: ${profile.id}")
                                    onProfileCreated(profile)
                                },
                                onError = { error ->
                                    android.util.Log.e("CreateProfileScreen", "Profile creation failed: $error")
                                    // Error will be displayed in UI state via ViewModel
                                }
                            )
                        } else {
                            android.util.Log.d("CreateProfileScreen", "Button disabled. Name: '${uiState.name}', Libraries: ${uiState.selectedLibraries.size}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .hoverable(createButtonInteractionSource)
                        .focusable(interactionSource = createButtonInteractionSource),
                    enabled = isEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEnabled) backgroundColor else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isEnabled) contentColor else MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (uiState.isCreating) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text = "Creating Profile...",
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
                        onClick = profileViewModel::clearError,
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

