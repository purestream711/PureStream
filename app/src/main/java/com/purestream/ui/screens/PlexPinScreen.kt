package com.purestream.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.purestream.data.model.AuthenticationStatus

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexPinScreen(
    pinId: String,
    pinCode: String,
    onBackToSignIn: () -> Unit,
    onAuthenticationSuccess: () -> Unit,
    viewModel: PlexAuthViewModel = viewModel()
) {
    // Handle back button to return to sign-in page
    BackHandler {
        onBackToSignIn()
    }
    
    // Start monitoring the PIN authentication
    LaunchedEffect(pinId) {
        viewModel.monitorPinAuthentication(pinId)
    }
    
    // Handle authentication success
    LaunchedEffect(viewModel.authStatus) {
        if (viewModel.authStatus == AuthenticationStatus.SUCCESS) {
            onAuthenticationSuccess()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Dark blue-gray top
                        Color(0xFF0D0D0D), // Pure black middle
                        Color(0xFF0D0D0D)  // Pure black bottom
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "Plex Authentication",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Main content card
            Card(
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A2A2A)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // QR Code
                    val qrCodeBitmap = com.purestream.ui.screens.generateQRCode("https://plex.tv/link")
                    qrCodeBitmap?.let { bitmap ->
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code for plex.tv/link",
                            modifier = Modifier.size(250.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Instructions
                    Text(
                        text = "Scan the QR code with your phone",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Or visit plex.tv/link on your phone",
                        fontSize = 20.sp,
                        color = Color(0xFFB3B3B3),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // PIN Display
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Enter this PIN:",
                                color = Color(0xFFB3B3B3),
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = pinCode,
                                color = Color(0xFFE5A00D),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                letterSpacing = 8.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Status indicator
                    when (viewModel.authStatus) {
                        AuthenticationStatus.WAITING_FOR_PIN -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFFE5A00D),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Waiting for authentication...",
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 16.sp
                                )
                            }
                        }
                        
                        AuthenticationStatus.SUCCESS -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Authentication successful!",
                                    color = Color.Green,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        AuthenticationStatus.ERROR -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Authentication failed",
                                        color = Color(0xFFEF4444),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = onBackToSignIn,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE5A00D),
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                        
                        else -> {}
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Back Button
            Button(
                onClick = onBackToSignIn,
                modifier = Modifier
                    .width(200.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFFB3B3B3)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Back to Sign In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

