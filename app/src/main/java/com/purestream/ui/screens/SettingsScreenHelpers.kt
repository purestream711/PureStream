package com.purestream.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FreePlanCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(400.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F2937)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Free Plan Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Free Plan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Text(
                text = "Perfect for getting started with content filtering",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            // Price
            Text(
                text = "$0/month",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Features
            val freeFeatures = listOf(
                "1 Adult Profile",
                "Basic Profanity Filtering (Mild Level)",
                "Content Analysis & Warnings",
                "Access to Your Full Plex Library",
                "Smart Recommendations",
                "No Ads. Ever."
            )
            
            freeFeatures.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = feature,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Free Plan Status
            Box(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Current Plan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProPlanCard(
    onUpgradeClick: () -> Unit,
    upgradeButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(400.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Color(0xFF8B5CF6))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            
            // Pro Plan Header with Most Popular Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Pure Stream Pro",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                // Most Popular Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFFBBF24),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "MOST POPULAR",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
            
            Text(
                text = "The complete family streaming solution",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            // Price
            Text(
                text = "$4.99/month or $49.99/year (17% annual savings)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // Everything in Free plus text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(6.dp)
            ) {
                Text(
                    text = "Everything in Free, plus:",
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Pro Features
            val proFeatures = listOf(
                "Unlimited User Profiles (Adult & Child)",
                "Customizable Filtering Levels (None to Strict)",
                "Custom Profanity Filter & Whitelist",
                "Advanced Audio Mute Settings",
                "Priority Support"
            )
            
            proFeatures.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = feature,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Upgrade Button
            Button(
                onClick = onUpgradeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .focusRequester(upgradeButtonFocusRequester),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Upgrade to Pro",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}