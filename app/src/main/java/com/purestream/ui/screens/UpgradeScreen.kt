package com.purestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpgradeScreen(
    onUpgradeClick: () -> Unit,
    onContinueFreeClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Choose Your Plan",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Unlock the full Pure Stream experience",
                fontSize = 20.sp,
                color = Color(0xFFB3B3B3),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            // Comparison Cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Free Plan Card
                PlanCard(
                    modifier = Modifier.weight(1f),
                    title = "Free",
                    price = "$0",
                    subtitle = "Perfect to get started",
                    features = listOf(
                        PlanFeature("1 Adult Profile", true),
                        PlanFeature("Mild Profanity Filter", true, note = "Fixed level"),
                        PlanFeature("Basic Content Access", true),
                        PlanFeature("Standard Streaming Quality", true),
                        PlanFeature("Multiple Profiles", false),
                        PlanFeature("Custom Filter Levels", false),
                        PlanFeature("Child Profiles", false),
                        PlanFeature("Custom Word Lists", false),
                        PlanFeature("Premium Support", false)
                    ),
                    buttonText = "Continue Free",
                    buttonColor = Color(0xFF6B7280),
                    onButtonClick = onContinueFreeClick,
                    isRecommended = false
                )
                
                // Pro Plan Card
                PlanCard(
                    modifier = Modifier.weight(1f),
                    title = "Pro",
                    price = "$9.99",
                    subtitle = "Full family protection",
                    features = listOf(
                        PlanFeature("Up to 5 Profiles", true),
                        PlanFeature("All Filter Levels", true, note = "None to Strict"),
                        PlanFeature("Adult & Child Profiles", true),
                        PlanFeature("Custom Filtered Words", true),
                        PlanFeature("Whitelist Specific Words", true),
                        PlanFeature("Adjustable Mute Duration", true),
                        PlanFeature("Premium Content Access", true),
                        PlanFeature("4K Streaming Quality", true),
                        PlanFeature("Priority Support", true)
                    ),
                    buttonText = "Upgrade to Pro",
                    buttonColor = Color(0xFF6366F1),
                    onButtonClick = onUpgradeClick,
                    isRecommended = true
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Back Button
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFFB3B3B3)
                )
            ) {
                Text("Back", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun PlanCard(
    modifier: Modifier = Modifier,
    title: String,
    price: String,
    subtitle: String,
    features: List<PlanFeature>,
    buttonText: String,
    buttonColor: Color,
    onButtonClick: () -> Unit,
    isRecommended: Boolean
) {
    Card(
        modifier = modifier.fillMaxHeight(0.8f),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended) Color(0xFF2A2A2A) else Color(0xFF1F1F1F)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isRecommended) 12.dp else 6.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recommended Badge
            if (isRecommended) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF6366F1),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "RECOMMENDED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Plan Title
            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Price
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = price,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecommended) Color(0xFF6366F1) else Color.White
                )
                if (price != "$0") {
                    Text(
                        text = "/month",
                        fontSize = 16.sp,
                        color = Color(0xFFB3B3B3),
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }
            
            // Subtitle
            Text(
                text = subtitle,
                fontSize = 16.sp,
                color = Color(0xFFB3B3B3),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Features List
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                features.forEach { feature ->
                    FeatureRow(feature = feature)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Action Button
            Button(
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = buttonText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun FeatureRow(
    feature: PlanFeature
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (feature.included) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (feature.included) Color(0xFF10B981) else Color(0xFF6B7280),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.name,
                fontSize = 14.sp,
                color = if (feature.included) Color.White else Color(0xFF6B7280),
                fontWeight = if (feature.included) FontWeight.Medium else FontWeight.Normal
            )
            
            feature.note?.let { note ->
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF),
                    style = androidx.compose.ui.text.TextStyle(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                )
            }
        }
    }
}

data class PlanFeature(
    val name: String,
    val included: Boolean,
    val note: String? = null
)