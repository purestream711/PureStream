package com.purestream.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.purestream.data.model.PlexLibrary
import com.purestream.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun LibraryDropdown(
    libraries: List<PlexLibrary>,
    selectedLibraryId: String?,
    onLibrarySelected: (String) -> Unit,
    isMobile: Boolean = false,
    dropdownFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    isLocked: Boolean = false,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Only show if more than 1 library
    if (libraries.size <= 1) return

    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var tempSelectedLibraryId by remember(selectedLibraryId) { mutableStateOf(selectedLibraryId) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val soundManager = remember { com.purestream.utils.SoundManager.getInstance(context) }

    val selectedLibrary = libraries.find { it.key == selectedLibraryId }

    // Purple for unfocused, yellow for focused (TV only)
    val purpleColor = Color(0xFF8B5CF6)
    val yellowColor = Color(0xFFF5B800)

    // Animate visibility for mobile (TV always visible)
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible || !isMobile) 0f else -80f,
        animationSpec = tween(durationMillis = 300),
        label = "dropdown_offset"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible || !isMobile) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "dropdown_alpha"
    )

    // BackHandler to close dropdown when back button is pressed
    BackHandler(enabled = expanded) {
        expanded = false
        tempSelectedLibraryId = selectedLibraryId // Reset to original selection
    }

    Column(
        modifier = modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .alpha(alpha)
    ) {
        // Pill-shaped Dropdown Button
        Box(
            modifier = Modifier
                .let { mod ->
                    if (!isMobile && dropdownFocusRequester != null) {
                        mod.focusRequester(dropdownFocusRequester)
                    } else {
                        mod
                    }
                }
                .let { mod ->
                    if (!isMobile && (upFocusRequester != null || downFocusRequester != null)) {
                        mod.focusProperties {
                            upFocusRequester?.let { up = it }
                            downFocusRequester?.let { down = it }
                        }
                    } else {
                        mod
                    }
                }
                .onFocusChanged { isFocused = it.isFocused }
                .focusable() // Ensure remains focusable
                .clickable(enabled = !isLocked) { 
                    soundManager.playSound(com.purestream.utils.SoundManager.Sound.CLICK)
                    expanded = !expanded 
                }
                .wrapContentWidth() // Pill-shaped, not full width
                .background(
                    color = if (isFocused && !isMobile) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = if (isFocused && !isMobile) 2.dp else 1.dp,
                    color = if (isFocused && !isMobile) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp), // Padding applied here for Box
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = selectedLibrary?.title ?: "Select Library",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLocked) Color.White.copy(alpha = 0.5f) else Color.White
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = if (isLocked) Color.White.copy(alpha = 0.5f) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Dropdown Menu with focus trapping (only if not locked)
        if (expanded && !isLocked) {
            Popup(
                onDismissRequest = {
                    expanded = false
                    tempSelectedLibraryId = selectedLibraryId // Reset on dismiss
                },
                alignment = Alignment.TopStart,
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                LibraryDropdownMenu(
                    libraries = libraries,
                    selectedLibraryId = tempSelectedLibraryId,
                    onLibrarySelected = { libraryId ->
                        tempSelectedLibraryId = libraryId
                        onLibrarySelected(libraryId)
                        expanded = false
                    },
                    onDismiss = {
                        expanded = false
                        tempSelectedLibraryId = selectedLibraryId // Reset on dismiss
                    },
                    isMobile = isMobile
                )
            }
        }
    }
}

@Composable
private fun LibraryDropdownMenu(
    libraries: List<PlexLibrary>,
    selectedLibraryId: String?,
    onLibrarySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    isMobile: Boolean
) {
    // Create focus requesters for each library item
    val itemFocusRequesters = remember(libraries) {
        libraries.associate { it.key to FocusRequester() }
    }

    // Auto-focus the selected library when dropdown opens
    LaunchedEffect(Unit) {
        if (!isMobile) {
            selectedLibraryId?.let { selectedId ->
                itemFocusRequesters[selectedId]?.requestFocus()
            } ?: itemFocusRequesters[libraries.firstOrNull()?.key]?.requestFocus()
        }
    }

    // BackHandler to close menu without saving
    BackHandler(enabled = true) {
        onDismiss()
    }

    Card(
        modifier = Modifier
            .then(
                if (isMobile) {
                    Modifier.fillMaxWidth()  // Fill width on mobile to prevent overflow
                } else {
                    Modifier.widthIn(min = 200.dp, max = 400.dp)  // Fixed width on TV
                }
            )
            .heightIn(max = 400.dp),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundSecondary
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            libraries.forEachIndexed { index, library ->
                LibraryMenuItem(
                    library = library,
                    isSelected = library.key == selectedLibraryId,
                    onClick = { onLibrarySelected(library.key) },
                    isMobile = isMobile,
                    focusRequester = itemFocusRequesters[library.key],
                    isFirstItem = index == 0,
                    isLastItem = index == libraries.lastIndex
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LibraryMenuItem(
    library: PlexLibrary,
    isSelected: Boolean,
    onClick: () -> Unit,
    isMobile: Boolean,
    focusRequester: FocusRequester?,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val soundManager = remember { com.purestream.utils.SoundManager.getInstance(context) }
    val purpleColor = Color(0xFF8B5CF6)
    val yellowColor = Color(0xFFF5B800)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (!isMobile && focusRequester != null) {
                    mod.focusRequester(focusRequester)
                } else {
                    mod
                }
            }
            .focusProperties {
                // Trap focus within dropdown - prevent up from first item and down from last item
                if (isFirstItem) {
                    up = FocusRequester.Cancel
                }
                if (isLastItem) {
                    down = FocusRequester.Cancel
                }
            }
            .background(
                color = when {
                    isFocused && !isMobile -> yellowColor
                    isSelected -> purpleColor.copy(alpha = 0.3f)
                    (isHovered) && !isMobile -> Color.White.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { 
                soundManager.playSound(com.purestream.utils.SoundManager.Sound.CLICK)
                onClick() 
            }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = library.title,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isFocused && !isMobile -> Color.Black
                isSelected -> purpleColor
                else -> TextPrimary
            }
        )
    }
}
