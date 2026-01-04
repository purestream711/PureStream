package com.purestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.purestream.data.model.ContentItem
import com.purestream.ui.theme.tvButtonFocus
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.purestream.utils.SoundManager

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HeroSection(
    featuredContent: ContentItem,
    backgroundImageUrl: String,
    onPlayClick: () -> Unit,
    onMoreInfoClick: () -> Unit,
    playButtonFocusRequester: FocusRequester,
    moreInfoButtonFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(800.dp)
    ) {
        // Background Image
        AsyncImage(
            model = backgroundImageUrl,
            contentDescription = "Featured background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Dark gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 800f
                    )
                )
        )
        
        // Bottom gradient for fade effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        
        // Content overlay - Netflix style positioning with title moved down
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 80.dp, end = 100.dp, top = 80.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title or Logo
            if (featuredContent.logoUrl != null) {
                AsyncImage(
                    model = featuredContent.logoUrl,
                    contentDescription = featuredContent.title,
                    modifier = Modifier
                        .height(120.dp)
                        .widthIn(max = 400.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                Text(
                    text = featuredContent.title,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Metadata row (Year, Duration, Rating)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Year
                featuredContent.year?.let { year ->
                    Text(
                        text = year.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB3B3B3)
                    )
                }
                
                // Duration
                featuredContent.duration?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB3B3B3)
                    )
                }
                
                // General Rating Badge (since ContentItem doesn't have contentRating)
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF374151),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "TV-14",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Star Rating
                featuredContent.rating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "★",
                            fontSize = 14.sp,
                            color = Color(0xFFFBBF24)
                        )
                        Text(
                            text = String.format("%.1f", rating),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB3B3B3)
                        )
                    }
                }
            }
            
            // Description - smaller size
            featuredContent.summary?.let { summary ->
                Text(
                    text = summary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    lineHeight = 22.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 24.dp)
            ) {
                // Play button with focus indicator
                FocusableHeroButton(
                    onClick = onPlayClick,
                    containerColor = Color(0xFF8B5CF6),  // Purple when not focused
                    contentColor = Color.White,
                    focusedContainerColor = Color(0xFFF5B800),  // Yellow when focused
                    focusedContentColor = Color.Black,
                    icon = Icons.Default.PlayArrow,
                    text = "Play",
                    focusRequester = playButtonFocusRequester,
                    lazyListState = lazyListState,
                    modifier = Modifier.focusProperties {
                        left = sidebarFocusRequester
                        right = moreInfoButtonFocusRequester
                        down = contentFocusRequester
                    }
                )
                
                // More Info button with focus indicator
                FocusableHeroButton(
                    onClick = onMoreInfoClick,
                    containerColor = Color(0xFF6B7280).copy(alpha = 0.7f),
                    contentColor = Color.White,
                    icon = Icons.Default.Info,
                    text = "More Info",
                    focusRequester = moreInfoButtonFocusRequester,
                    lazyListState = lazyListState,
                    modifier = Modifier.focusProperties {
                        left = playButtonFocusRequester
                        down = contentFocusRequester
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FocusableHeroButton(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    focusRequester: FocusRequester,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    focusedContainerColor: Color = containerColor,
    focusedContentColor: Color = contentColor
) {
    var isFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }

    // Scroll to top when this button gets focus (hovering)
    LaunchedEffect(isFocused) {
        if (isFocused) {
            coroutineScope.launch {
                lazyListState.scrollToItem(0, scrollOffset = -240)
            }
        }
    }

    Button(
        onClick = {
            android.util.Log.d("HeroSection", "Hero button clicked - playing CLICK sound")
            soundManager.playSound(SoundManager.Sound.CLICK)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) focusedContainerColor else containerColor,
            contentColor = if (isFocused) focusedContentColor else contentColor
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .height(48.dp)
            .tvButtonFocus()
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (icon == Icons.Default.PlayArrow) 24.dp else 20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
    
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}