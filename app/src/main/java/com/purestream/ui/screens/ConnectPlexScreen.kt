package com.purestream.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import android.util.Log
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.ui.tooling.preview.Preview
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.purestream.data.model.AuthenticationStatus
import com.purestream.utils.rememberIsMobile
import com.purestream.ui.theme.NetflixDarkGray
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ConnectPlexScreen(
    onConnectPlexClick: () -> Unit,
    onNavigateToPin: (String, String) -> Unit,
    onNavigateToWebAuth: () -> Unit = {},
    viewModel: PlexAuthViewModel = viewModel()
) {
    val isMobile = rememberIsMobile()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Handle authentication success - dismiss keyboard before navigation
    // Only depend on authStatus, not justLoggedOut (to avoid re-triggering when flag is cleared)
    LaunchedEffect(viewModel.authStatus) {
        Log.d("ConnectPlexScreen", "Auth status changed to: ${viewModel.authStatus}, justLoggedOut: ${viewModel.justLoggedOut}")

        when (viewModel.authStatus) {
            AuthenticationStatus.SUCCESS -> {
                // Don't auto-navigate if user just logged out
                if (!viewModel.justLoggedOut) {
                    Log.d("ConnectPlexScreen", "Navigating to ProfileSelection (auth success)")
                    Log.d("ConnectPlexScreen", "CALLING onConnectPlexClick() from LaunchedEffect")
                    // Dismiss keyboard and clear focus before navigation
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    // Brief delay for smooth transition
                    kotlinx.coroutines.delay(100)
                    onConnectPlexClick()
                } else {
                    Log.d("ConnectPlexScreen", "Auth is SUCCESS but user just logged out - NOT navigating")
                    // Clear the flag now that we've confirmed we won't navigate
                    viewModel.clearLogoutFlag()
                }
            }
            else -> {
                // Do nothing for other states
                Log.d("ConnectPlexScreen", "Auth status is ${viewModel.authStatus} - no action")
            }
        }
    }

    // Check for existing authentication on screen load
    // Skip if user just logged out to prevent auto-navigation
    LaunchedEffect(Unit) {
        Log.d("ConnectPlexScreen", "Screen loaded - justLoggedOut: ${viewModel.justLoggedOut}")
        if (viewModel.justLoggedOut) {
            Log.d("ConnectPlexScreen", "User just logged out - skipping auto-check (will be cleared by SUCCESS handler)")
        } else {
            Log.d("ConnectPlexScreen", "Calling checkForStoredAuth()")
            viewModel.checkForStoredAuth()
        }
    }
    if (isMobile) {
        // Mobile Layout: OAuth-based login only
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Header
                    Text(
                        text = "Connect Your Plex Account",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    OAuthLoginLayout(
                        viewModel = viewModel,
                        onConnectPlexClick = onConnectPlexClick,
                        onNavigateToWebAuth = onNavigateToWebAuth
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    } else {
        // TV Layout: Side-by-side - OAuth button on left, QR code on right
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
                // Header for TV layout
                Text(
                    text = "Connect Your Plex Account",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // TV Layout: Side-by-side with OAuth and QR code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side - OAuth Login
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        OAuthLoginLayout(
                            viewModel = viewModel,
                            onConnectPlexClick = onConnectPlexClick,
                            onNavigateToWebAuth = onNavigateToWebAuth
                        )
                    }

                    // Right side - QR Code (TV only)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        QRCodeAuthSideLayout(
                            viewModel = viewModel,
                            onNavigateToPin = onNavigateToPin
                        )
                    }
                }
            }
        }
    }
    
    
}


@Composable
fun OAuthLoginLayout(
    viewModel: PlexAuthViewModel,
    onConnectPlexClick: () -> Unit,
    onNavigateToWebAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    val loginButtonFocusRequester = remember { FocusRequester() }

    // Auto-focus the login button for TV remote navigation
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Brief delay to ensure UI is ready
        loginButtonFocusRequester.requestFocus()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Login with Plex",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Authenticate securely through Plex's official authentication page.",
                fontSize = 12.sp,
                color = Color(0xFFB3B3B3),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Initiate WebView auth and navigate to WebView screen
                    viewModel.initiateWebViewAuth()
                    onNavigateToWebAuth()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(loginButtonFocusRequester),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE5A00D),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = viewModel.oauthStatus != AuthenticationStatus.LOADING &&
                         viewModel.oauthStatus != AuthenticationStatus.SUCCESS
            ) {
                if (viewModel.oauthStatus == AuthenticationStatus.LOADING ||
                    viewModel.oauthStatus == AuthenticationStatus.WAITING_FOR_PIN) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Opening Plex...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (viewModel.oauthStatus == AuthenticationStatus.SUCCESS) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Authenticated",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Login with Plex",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Error state
            if (viewModel.oauthStatus == AuthenticationStatus.ERROR && viewModel.oauthErrorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Error",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF6B6B)
                    )
                    Text(
                        text = viewModel.oauthErrorMessage ?: "Authentication failed",
                        fontSize = 11.sp,
                        color = Color(0xFFB3B3B3),
                        lineHeight = 16.sp
                    )
                }
            }

            // Success navigation
            LaunchedEffect(viewModel.oauthStatus) {
                if (viewModel.oauthStatus == AuthenticationStatus.SUCCESS) {
                    delay(500)
                    onConnectPlexClick()
                }
            }
        }
    }
}

@Composable
fun EmailPasswordAuthSideLayout(
    viewModel: PlexAuthViewModel,
    isMobile: Boolean = false
) {
    val connectButtonFocusRequester = remember { FocusRequester() }
    val passwordFieldFocusRequester = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Email & Password",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Sign in with your Plex credentials to access your media libraries.",
                fontSize = 12.sp,
                color = Color(0xFFB3B3B3),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            // Direct input fields instead of dialog
            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                label = { Text("Email", color = Color(0xFFB3B3B3)) },
                enabled = viewModel.authStatus != AuthenticationStatus.SUCCESS,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE5A00D),
                    unfocusedBorderColor = Color(0xFF555555),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFE5A00D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            key(passwordVisible) {
                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                label = { Text("Password", color = Color(0xFFB3B3B3)) },
                enabled = viewModel.authStatus != AuthenticationStatus.SUCCESS,
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = if (isMobile) {
                    {
                        IconButton(onClick = { 
                            passwordVisible = !passwordVisible
                        }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Color(0xFFE5A00D)
                            )
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        connectButtonFocusRequester.requestFocus()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE5A00D),
                    unfocusedBorderColor = Color(0xFF555555),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFE5A00D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(passwordFieldFocusRequester)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = { viewModel.signInWithCredentials() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(connectButtonFocusRequester),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE5A00D),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = viewModel.authStatus != AuthenticationStatus.LOADING && 
                         viewModel.authStatus != AuthenticationStatus.SUCCESS
            ) {
                if (viewModel.authStatus == AuthenticationStatus.LOADING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connect Plex Libraries",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            // Error dialog for invalid credentials
            if (viewModel.showErrorDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        viewModel.dismissErrorDialog()
                        passwordFieldFocusRequester.requestFocus()
                    },
                    title = {
                        Text(
                            text = "Authentication Failed",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = viewModel.errorMessage ?: "Invalid credentials. Please check your email and password and try again.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { 
                                viewModel.dismissErrorDialog()
                                passwordFieldFocusRequester.requestFocus()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE5A00D),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("OK")
                        }
                    },
                    containerColor = Color(0xFF2A2A2A),
                    tonalElevation = 8.dp
                )
            }
        }
    }
}

@Composable
fun QRCodeAuthSideLayout(
    viewModel: PlexAuthViewModel,
    onNavigateToPin: (String, String) -> Unit
) {
    // Automatically generate QR code when component loads, but only if credentials auth hasn't succeeded
    LaunchedEffect(Unit) {
        if (viewModel.authStatus == AuthenticationStatus.IDLE && 
            viewModel.authenticationMethod != "credentials") {
            viewModel.createPinForQRCode()
        }
    }
    
    // Stop QR code generation if credentials authentication succeeds
    LaunchedEffect(viewModel.authenticationMethod, viewModel.authStatus) {
        if (viewModel.authenticationMethod == "credentials" && 
            viewModel.authStatus == AuthenticationStatus.SUCCESS) {
            // Credentials auth succeeded, so we don't need QR code auth anymore
            Log.d("QRCodeAuth", "Credentials authentication succeeded, stopping QR code generation")
        }
    }
    
    // Navigate to PIN page only when QR code is actually scanned (authentication starts)
    LaunchedEffect(viewModel.authStatus) {
        if (viewModel.authStatus == AuthenticationStatus.SUCCESS) {
            // User successfully authenticated, handled by parent component
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header section
            Text(
                text = "Scan QR Code",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // QR Code content section
            when {
                // Show success state if either method succeeded
                (viewModel.authenticationMethod == "credentials" && viewModel.authStatus == AuthenticationStatus.SUCCESS) ||
                (viewModel.authenticationMethod == "qr_code" && viewModel.qrAuthStatus == AuthenticationStatus.SUCCESS) -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Authentication Successful",
                            fontSize = 16.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (viewModel.authenticationMethod == "credentials") "Connected via email and password" else "Connected via QR code",
                            fontSize = 12.sp,
                            color = Color(0xFFB3B3B3),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> when (viewModel.qrAuthStatus) {
                AuthenticationStatus.LOADING -> {
                    Text(
                        text = "Generating QR code...",
                        fontSize = 12.sp,
                        color = Color(0xFFB3B3B3),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color(0xFFE5A00D)
                        )
                    }
                }
                
                AuthenticationStatus.WAITING_FOR_PIN -> {
                    // Show QR code with generated PIN
                    viewModel.pinResponse?.let { pin ->
                        // Instruction text at the top
                        Text(
                            text = "Use your phone to scan the QR code or visit plex.tv/link to authenticate using the code: ${pin.code}",
                            fontSize = 12.sp,
                            color = Color(0xFFB3B3B3),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        val qrCodeUrl = "https://plex.tv/link?pin=${pin.code}"
                        val qrBitmap = generateQRCode(qrCodeUrl)
                        
                        if (qrBitmap != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Failed to generate QR code",
                                color = Color(0xFFEF4444),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
                
                AuthenticationStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = viewModel.qrErrorMessage ?: "Authentication failed",
                        color = Color(0xFFEF4444),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            // Reset QR code state and retry
                            viewModel.resetQrAuth()
                            viewModel.createPinForQRCode()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE5A00D),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Try Again")
                    }
                }
                
                else -> {
                    // Show loading state while QR code is being generated
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color(0xFFE5A00D)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Generating QR code...",
                            color = Color(0xFFB3B3B3),
                            fontSize = 16.sp
                        )
                    }
                }
                }
            }
        }
    }
}



fun generateQRCode(text: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@Preview(showBackground = true, widthDp = 500, heightDp = 600)
@Composable
fun EmailPasswordPreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Email & Password",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = "user@example.com",
                onValueChange = { },
                label = { Text("Email", color = Color(0xFFB3B3B3)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE5A00D),
                    unfocusedBorderColor = Color(0xFF555555),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFE5A00D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = "••••••••",
                onValueChange = { },
                label = { Text("Password", color = Color(0xFFB3B3B3)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE5A00D),
                    unfocusedBorderColor = Color(0xFF555555),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFE5A00D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE5A00D),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect Plex Libraries",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 500, heightDp = 600)
@Composable  
fun QRCodePreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan QR Code",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Use your phone to scan the QR code or visit plex.tv/link to authenticate.",
                    color = Color(0xFFB3B3B3),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
            
            // QR Code mockup
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "QR Code",
                        color = Color.Black,
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Scan with your phone or visit plex.tv/link",
                    color = Color(0xFFB3B3B3),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "PIN: 1234",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            
            // Bottom spacer for alignment
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 1200, heightDp = 800)
@Composable
fun ConnectPlexLayoutPreview() {
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
            Text(
                text = "Connect Your Plex Account",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    EmailPasswordPreview()
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    QRCodePreview()
                }
            }
        }
    }
}