package com.purestream.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.hoverable
import com.purestream.data.model.*
import com.purestream.ui.theme.tvIconFocusIndicator
import com.purestream.ui.theme.tvCardFocusIndicator
import com.purestream.ui.theme.animatedProfileBorder
import com.purestream.ui.theme.animatedPosterBorder
import com.purestream.utils.rememberIsMobile

@Composable
fun ProfileSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = com.purestream.ui.theme.TextPrimary
        )
        content()
    }
}

@Composable
fun AvatarImageSelectionButton(
    avatarImage: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageName = context.packageName
    val avatarResourceId = remember(avatarImage, packageName) {
        try {
            context.resources.getIdentifier(
                avatarImage, 
                "drawable", 
                packageName
            )
        } catch (e: Exception) {
            0
        }
    }
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Box(
        modifier = modifier
            .size(64.dp)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null, 
                onClick = onClick
            )
            .border(
                width = 3.dp,
                color = when {
                    isSelected -> Color(0xFF8B5CF6)
                    isFocused -> Color(0xFF8B5CF6).copy(alpha = 0.8f)
                    else -> Color.White.copy(alpha = 0.1f)
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarResourceId != 0) {
            Image(
                painter = painterResource(id = avatarResourceId),
                contentDescription = "Avatar Option: $avatarImage",
                modifier = Modifier
                    .size(54.dp) 
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            AvatarFallback(avatarImage = avatarImage)
        }
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AvatarFallback(avatarImage: String) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(
                color = when {
                    avatarImage.contains("bear") -> Color(0xFF92400E)
                    avatarImage.contains("beaver") -> Color(0xFF8B5CF6)
                    avatarImage.contains("cat") -> Color(0xFFF59E0B)
                    avatarImage.contains("cheetah") -> Color(0xFFEA7317)
                    avatarImage.contains("deer") -> Color(0xFF10B981)
                    avatarImage.contains("dog") -> Color(0xFF8B5CF6)
                    avatarImage.contains("elephant") -> Color(0xFF6B7280)
                    avatarImage.contains("fox") -> Color(0xFFEF4444)
                    avatarImage.contains("giraffe") -> Color(0xFFF59E0B)
                    avatarImage.contains("hedgehog") -> Color(0xFF92400E)
                    avatarImage.contains("koala") -> Color(0xFF6B7280)
                    avatarImage.contains("lion") -> Color(0xFFF59E0B)
                    avatarImage.contains("monkey") -> Color(0xFF92400E)
                    avatarImage.contains("raccoon") -> Color(0xFF1E293B)
                    else -> MaterialTheme.colorScheme.primary
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                avatarImage.contains("bear") -> "🐻"
                avatarImage.contains("beaver") -> "🦫"
                avatarImage.contains("cat") -> "🐱"
                avatarImage.contains("cheetah") -> "🐆"
                avatarImage.contains("deer") -> "🦌"
                avatarImage.contains("dog") -> "🐶"
                avatarImage.contains("elephant") -> "🐘"
                avatarImage.contains("fox") -> "🦊"
                avatarImage.contains("giraffe") -> "🦒"
                avatarImage.contains("hedgehog") -> "🦔"
                avatarImage.contains("koala") -> "🐨"
                avatarImage.contains("lion") -> "🦁"
                avatarImage.contains("monkey") -> "🐒"
                avatarImage.contains("raccoon") -> "🦝"
                else -> avatarImage.take(2).uppercase()
            },
            fontSize = 24.sp,
            color = Color.White
        )
    }
}

@Composable
fun ProfileTypeButton(
    type: ProfileType,
    isSelected: Boolean,
    onClick: () -> Unit,
    isLocked: Boolean,
    isPremium: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = modifier
            .tvCardFocusIndicator() // Handles scale
            .border(
                width = 2.dp,
                color = if (isFocused) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isLocked || isPremium) {
                if (!isLocked || isPremium) onClick()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                          else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isLocked && !isPremium) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Premium Feature",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FilterLevelButton(
    level: ProfanityFilterLevel,
    isSelected: Boolean,
    onClick: () -> Unit,
    isLocked: Boolean,
    isPremium: Boolean = false
) {
    val isMobile = rememberIsMobile()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardFocusIndicator() // Handles scale
            .border(
                width = 2.dp,
                color = if (isFocused) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isLocked || isPremium) { 
                if (!isLocked || isPremium) onClick() 
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMobile) {
                // Mobile: Flexible layout with text wrapping and conditional icon placement
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = level.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Icon on the right side of the title
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isLocked && !isPremium) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Premium Feature",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Wrapped description text
                    Text(
                        text = when (level) {
                            ProfanityFilterLevel.NONE -> "No filtering applied"
                            ProfanityFilterLevel.MILD -> "Basic profanity filtering (f***, b**** +4 more)"
                            ProfanityFilterLevel.MODERATE -> "Moderate filtering (mild + s***, d*** +8 more)"
                            ProfanityFilterLevel.STRICT -> "Maximum filtering (moderate + g**, h*** +12 more)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp), // Add small padding to prevent text from getting too close to edge
                        maxLines = Int.MAX_VALUE // Allow unlimited lines for wrapping
                    )
                }
            } else {
                // TV: Original horizontal layout with icon on the right
                Column {
                    Text(
                        text = level.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (level) {
                            ProfanityFilterLevel.NONE -> "No filtering applied"
                            ProfanityFilterLevel.MILD -> "Basic profanity filtering (f***, b**** +4 more)"
                            ProfanityFilterLevel.MODERATE -> "Moderate filtering (mild + s***, d*** +8 more)"
                            ProfanityFilterLevel.STRICT -> "Maximum filtering (moderate + g**, h*** +12 more)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else if (isLocked && !isPremium) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Premium Feature",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun LibrarySelectionItem(
    library: PlexLibrary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvCardFocusIndicator() // Handles scale and focus state
            .border(
                width = 2.dp,
                color = if (isFocused) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = library.title,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = library.type.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}