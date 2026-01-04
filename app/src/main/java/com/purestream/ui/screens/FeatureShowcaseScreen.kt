package com.purestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.focusable
import com.purestream.ui.theme.*
import com.purestream.ui.theme.getAnimatedButtonBackgroundColor
import com.purestream.utils.rememberIsMobile
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeatureShowcaseScreen(
    onLetsGoClick: () -> Unit
) {
    val isMobile = rememberIsMobile()
    val letsGoButtonFocusRequester = remember { FocusRequester() }
    
    // Auto-focus the Let's Go button when screen loads (TV only)
    LaunchedEffect(Unit) {
        if (!isMobile) {
            delay(500) // Longer delay to ensure all UI is fully composed
            try {
                letsGoButtonFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // Retry once more after additional delay if first attempt fails
                delay(300)
                try {
                    letsGoButtonFocusRequester.requestFocus()
                } catch (e2: Exception) {
                    android.util.Log.w("FeatureShowcaseScreen", "Failed to request focus: ${e2.message}")
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixDarkGray)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Dark blue-gray top
                        Color(0xFF0D0D0D), // Pure black middle
                        Color(0xFF0D0D0D)  // Pure black bottom
                    )
                )
            )
            .padding(WindowInsets.navigationBars.asPaddingValues())
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isMobile) 16.dp else 24.dp, 
                    vertical = if (isMobile) 12.dp else 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 12.dp)
        ) {
            // Header Section
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = if (isMobile) 48.dp else 0.dp)
                ) {
                    Text(
                        text = "Welcome to Pure Stream",
                        fontSize = if (isMobile) 28.sp else 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "The world's first content filtering platform that transforms your existing${if (isMobile) " " else "\n"}Plex library into a safe, family-friendly streaming experience",
                        fontSize = if (isMobile) 14.sp else 12.sp,
                        color = Color(0xFFB3B3B3),
                        textAlign = TextAlign.Center,
                        lineHeight = if (isMobile) 18.sp else 16.sp
                    )
                }
            }
            
            // Features Title
            item {
                Text(
                    text = "Why Pure Stream Changes Everything",
                    fontSize = if (isMobile) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA), // Light blue
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            // Feature Cards - Responsive Layout
            if (isMobile) {
                // Mobile: Single column layout
                items(6) { index ->
                    val featureData = listOf(
                        Triple(Icons.Default.Shield, Color(0xFF8B5CF6), "Your Content, Protected" to "Unlike Netflix or Disney+, you own your content. We just make it family-safe with real-time filtering that works on YOUR existing Plex library."),
                        Triple(Icons.Default.Psychology, Color(0xFF06B6D4), "Smart Filtering" to "Actual smart profanity filtering that allows families to experience the big screen without worrying about harsh language."),
                        Triple(Icons.Default.Visibility, Color(0xFF10B981), "Real-Time Content Analysis" to "Every movie and show is analyzed for profanity and provides smart insight into all your favorites."),
                        Triple(Icons.Default.People, Color(0xFFF59E0B), "True Family Profiles" to "Adults and children have two tiers perfectly suited to their age groups so they never hear something they shouldn't."),
                        Triple(Icons.Default.MoneyOff, Color(0xFFEC4899), "No Monthly Fees (Free Tier)" to "Unlike streaming services that charge $15-20/month, our free tier gives you essential filtering for your existing content at zero cost."),
                        Triple(Icons.Default.AutoAwesome, Color(0xFFFBBF24), "Custom Content Curation" to "Curated Dashboard delivers trending movies and shows that match with your own personal Plex libraries.")
                    )[index]
                    
                    FeatureCard(
                        icon = featureData.first,
                        iconColor = featureData.second,
                        title = featureData.third.first,
                        description = featureData.third.second,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        isMobile = true
                    )
                }
            } else {
                // TV: Row layout (existing)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        FeatureCard(
                            icon = Icons.Default.Shield,
                            iconColor = Color(0xFF8B5CF6), // Purple
                            title = "Your Content, Protected",
                            description = "Unlike Netflix or Disney+, you own your content. We just make it family-safe with real-time filtering that works on YOUR existing Plex library.",
                            modifier = Modifier.weight(1f)
                        )
                        
                        FeatureCard(
                            icon = Icons.Default.Psychology,
                            iconColor = Color(0xFF06B6D4), // Cyan
                            title = "Smart Filtering",
                            description = "Actual smart profanity filtering that allows families to experience the big screen without worrying about harsh language.",
                            modifier = Modifier.weight(1f)
                        )
                        
                        FeatureCard(
                            icon = Icons.Default.Visibility,
                            iconColor = Color(0xFF10B981), // Green
                            title = "Real-Time Content Analysis",
                            description = "Every movie and show is analyzed for profanity and provides smart insight into all your favorites.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        FeatureCard(
                            icon = Icons.Default.People,
                            iconColor = Color(0xFFF59E0B), // Amber
                            title = "True Family Profiles",
                            description = "Adults and children have two tiers perfectly suited to their age groups so they never hear something they shouldn't.",
                            modifier = Modifier.weight(1f)
                        )
                        
                        FeatureCard(
                            icon = Icons.Default.MoneyOff,
                            iconColor = Color(0xFFEC4899), // Pink
                            title = "No Monthly Fees (Free Tier)",
                            description = "Unlike streaming services that charge $15-20/month, our free tier gives you essential filtering for your existing content at zero cost.",
                            modifier = Modifier.weight(1f)
                        )
                        
                        FeatureCard(
                            icon = Icons.Default.AutoAwesome,
                            iconColor = Color(0xFFFBBF24), // Yellow
                            title = "Custom Content Curation",
                            description = "Curated Dashboard delivers trending movies and shows that match with your own personal Plex libraries.",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Let's Go Button
            item {
                val letsGoInteractionSource = remember { MutableInteractionSource() }
                val letsGoBackgroundColor = getAnimatedButtonBackgroundColor(
                    interactionSource = letsGoInteractionSource,
                    defaultColor = Color(0xFF8B5CF6) // Purple
                )
                
                Button(
                    onClick = onLetsGoClick,
                    modifier = Modifier
                        .let { mod ->
                            if (isMobile) {
                                mod
                            } else {
                                mod.focusRequester(letsGoButtonFocusRequester)
                                    .hoverable(letsGoInteractionSource)
                                    .focusable(interactionSource = letsGoInteractionSource)
                            }
                        }
                        .padding(top = if (isMobile) 8.dp else 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = letsGoBackgroundColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Let's Go",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    isMobile: Boolean = false
) {
    Card(
        modifier = modifier.height(if (isMobile) 140.dp else 120.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1C2E).copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isMobile) 12.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isMobile) 6.dp else 4.dp)
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(if (isMobile) 20.dp else 18.dp)
            )
            
            // Title
            Text(
                text = title,
                fontSize = if (isMobile) 14.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Description
            Text(
                text = description,
                fontSize = if (isMobile) 11.sp else 10.sp,
                color = Color(0xFFB3B3B3),
                lineHeight = if (isMobile) 14.sp else 13.sp
            )
        }
    }
}