package com.purestream

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.purestream.navigation.Destinations
import com.purestream.ui.screens.*
import com.purestream.ui.theme.PureStreamTheme
import com.purestream.ui.viewmodel.HomeViewModel
import com.purestream.ui.viewmodel.MoviesViewModel
import com.purestream.ui.viewmodel.TvShowsViewModel
import com.purestream.ui.viewmodel.ProfileSelectionViewModel
import com.purestream.data.manager.PremiumStatusManager
import com.purestream.data.manager.PremiumStatusState
import com.purestream.data.model.Profile
import com.purestream.data.model.AuthenticationStatus
import com.purestream.data.manager.ProfileManager
import com.purestream.data.repository.ProfileRepository
import com.purestream.data.repository.SubtitleAnalysisRepository
import com.purestream.data.repository.OpenSubtitlesRepository
import com.purestream.data.database.AppDatabase
import com.purestream.profanity.ProfanityFilter
import com.purestream.utils.SoundManager

class MainActivity : ComponentActivity() {
    
    private lateinit var soundManager: SoundManager
    private var speechRecognizer: SpeechRecognizer? = null
    
    // Background reset functionality
    private var backgroundTimestamp: Long = 0
    private val BACKGROUND_RESET_TIMEOUT = 10 * 60 * 1000L // 10 minutes - full app reset
    private val DATA_REFRESH_TIMEOUT = 60 * 60 * 1000L // 1 hour - refresh content data
    private var onResetRequired: (() -> Unit)? = null
    private var onDataRefreshRequired: (() -> Unit)? = null
    private var currentSearchViewModel: com.purestream.ui.viewmodel.SearchViewModel? = null
    
    // Modern permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("VoiceSearch", "RECORD_AUDIO permission granted, retrying voice search")
            currentSearchViewModel?.let { viewModel ->
                startVoiceSearchWithPermission(viewModel)
            }
        } else {
            android.util.Log.w("VoiceSearch", "RECORD_AUDIO permission denied - voice search unavailable")
            // Do nothing - don't perform a meaningless search
        }
    }
    
    
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ApiClient with context to fetch the real device name
        com.purestream.data.api.ApiClient.init(this)

        // Initialize sound manager
        try {
            soundManager = SoundManager.getInstance(this)
            android.util.Log.d("MainActivity", "SoundManager initialized successfully")
            
            // Play startup sound when it's ready (no arbitrary delay)
            soundManager.playStartupSoundWhenReady {
                android.util.Log.d("MainActivity", "Playing startup sound")
                soundManager.playSound(SoundManager.Sound.STARTUP)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize SoundManager: ${e.message}", e)
        }
        
        // Initialize premium status management early to prevent cross-platform sync issues
        initializePremiumStatusEarly()

        // Schedule weekly Gemini refresh for AI-curated dashboard
        scheduleGeminiRefresh()

        setContent {
            var shouldResetApp by remember { mutableStateOf(false) }
            var shouldRefreshData by remember { mutableStateOf(false) }

            // Detect if app was restored after process death
            // When Android kills the process and restores it, savedInstanceState will be non-null
            val wasRestoredAfterProcessDeath = savedInstanceState != null
            if (wasRestoredAfterProcessDeath) {
                android.util.Log.w("MainActivity", "App was restored after process death - will redirect to profile selection")
            }

            // Set up the reset callbacks
            LaunchedEffect(Unit) {
                onResetRequired = { shouldResetApp = true }
                onDataRefreshRequired = { shouldRefreshData = true }
            }

            PureStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    PureStreamApp(
                        shouldResetApp = shouldResetApp,
                        onResetHandled = { shouldResetApp = false },
                        shouldRefreshData = shouldRefreshData,
                        onDataRefreshHandled = { shouldRefreshData = false },
                        wasRestoredAfterProcessDeath = wasRestoredAfterProcessDeath
                    )
                }
            }
        }
    }
    
    /**
     * Initialize premium status early to prevent cross-platform synchronization issues
     * This ensures premium status is properly synced before users navigate to settings
     */
    private fun initializePremiumStatusEarly() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("MainActivity", "Early premium status initialization...")
                val premiumManager = PremiumStatusManager.getInstance(this@MainActivity)

                // Trigger a refresh to ensure cross-platform sync
                // This runs in background and won't block UI
                premiumManager.refreshPremiumStatus()

                android.util.Log.d("MainActivity", "Premium status initialization triggered successfully")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error during early premium status initialization", e)
                // Continue app startup even if premium status initialization fails
            }
        }
    }

    /**
     * Schedules weekly Gemini API refresh for AI-curated dashboard collections
     * This runs every 7 days to fetch trending/top-rated movies and match them against user libraries
     */
    private fun scheduleGeminiRefresh() {
        // Periodic refresh disabled - now triggered on-demand from Settings
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        android.util.Log.i("MainActivity", "*** KEY EVENT: keyCode=$keyCode ***")
        
        // Handle sounds with fallback approach - try component-level first, then global
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Give component-level sound handling priority, but add global fallback
                android.util.Log.i("MainActivity", "D-pad movement detected: $keyCode")
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                android.util.Log.i("MainActivity", "Select key detected: $keyCode")
                // Let component-level handle this, don't add fallback to avoid double sounds
                return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
    }
    
    fun startVoiceSearch(searchViewModel: com.purestream.ui.viewmodel.SearchViewModel) {
        android.util.Log.d("VoiceSearch", "Starting voice search...")
        currentSearchViewModel = searchViewModel
        
        // Check if we have permission to record audio
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.w("VoiceSearch", "RECORD_AUDIO permission not granted, requesting permission")
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        
        startVoiceSearchWithPermission(searchViewModel)
    }
    
    private fun startVoiceSearchWithPermission(searchViewModel: com.purestream.ui.viewmodel.SearchViewModel) {
        // Check if speech recognition is available on this device
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            android.util.Log.w("VoiceSearch", "Speech recognition not available on this device")
            // Do nothing - don't perform a meaningless search
            return
        }
        
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }
        
        speechRecognizer?.let { recognizer ->
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something to search...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    android.util.Log.d("VoiceSearch", "Ready for speech input")
                }
                
                override fun onBeginningOfSpeech() {
                    android.util.Log.d("VoiceSearch", "Speech input detected")
                }
                
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    android.util.Log.d("VoiceSearch", "Speech input ended")
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error $error"
                    }
                    android.util.Log.e("VoiceSearch", "Speech recognition error: $errorMessage (code: $error)")
                    
                    // Do nothing - don't perform a meaningless search on error
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    android.util.Log.d("VoiceSearch", "Speech recognition results: $matches")
                    
                    if (!matches.isNullOrEmpty()) {
                        val query = matches[0]
                        android.util.Log.d("VoiceSearch", "Using voice search result: '$query'")
                        currentSearchViewModel?.updateSearchQuery(query)
                        currentSearchViewModel?.submitSearch(query)
                    } else {
                        android.util.Log.w("VoiceSearch", "No speech recognition results")
                        // Do nothing - don't perform a meaningless search
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    android.util.Log.d("VoiceSearch", "Partial results: $matches")
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    android.util.Log.d("VoiceSearch", "Recognition event: $eventType")
                }
            })
            
            android.util.Log.d("VoiceSearch", "Starting speech recognition listening...")
            recognizer.startListening(intent)
        } ?: run {
            // Speech recognition not available
            android.util.Log.w("VoiceSearch", "Speech recognizer is null - voice search unavailable")
            // Do nothing - don't perform a meaningless search
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        soundManager.release()
    }
    
    private fun openPlexTv() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://plex.tv/web"))
        startActivity(intent)
    }
    
    override fun onPause() {
        super.onPause()
        backgroundTimestamp = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "App backgrounded - timestamp recorded")
    }
    
    override fun onResume() {
        super.onResume()
        if (backgroundTimestamp > 0) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            android.util.Log.d("MainActivity", "App resumed - elapsed time: ${elapsed / 1000}s")

            // Force refresh premium status to detect subscription changes (like cancellations)
            // that might have happened while app was in background
            val premiumManager = PremiumStatusManager.getInstance(this@MainActivity)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    premiumManager.handleAppResume()
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to refresh premium status on resume: ${e.message}")
                }
            }

            // Check timeouts
            when {
                elapsed > BACKGROUND_RESET_TIMEOUT -> {
                    android.util.Log.d("MainActivity", "Background timeout exceeded (${elapsed / 1000}s) - resetting to GetStarted")
                    onResetRequired?.invoke()
                }
                elapsed > DATA_REFRESH_TIMEOUT -> {
                    android.util.Log.d("MainActivity", "Data refresh timeout exceeded (${elapsed / 1000}s) - refreshing content")
                    onDataRefreshRequired?.invoke()
                }
                else -> {
                    android.util.Log.d("MainActivity", "Background time within acceptable limits - no reset needed")
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        android.util.Log.d("MainActivity", "onNewIntent called with action: ${intent.action}, data: ${intent.data}")

        // Handle OAuth callback from purestream://auth-callback
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            if (uri.scheme == "purestream" && uri.host == "auth-callback") {
                android.util.Log.d("MainActivity", "OAuth callback received: $uri")
                handleOAuthCallback(uri)
            }
        }
    }

    private fun handleOAuthCallback(uri: Uri) {
        val token = extractTokenFromUri(uri)
        if (token != null) {
            android.util.Log.d("MainActivity", "Extracted OAuth token successfully (length: ${token.length})")
            // Token will be used by the app navigation logic
            // The token is already saved by PlexAuthRepository
        } else {
            android.util.Log.e("MainActivity", "Failed to extract token from OAuth callback URI")
        }
    }

    private fun extractTokenFromUri(uri: Uri): String? {
        // Try to get token from query parameter: ?token=XXX
        val tokenFromQuery = uri.getQueryParameter("token")
        if (tokenFromQuery != null) {
            android.util.Log.d("MainActivity", "Token found in query parameter")
            return tokenFromQuery
        }

        // Try to get from fragment: #token=XXX
        val fragment = uri.fragment
        if (fragment != null && fragment.contains("token=")) {
            val token = fragment.substringAfter("token=").substringBefore("&")
            if (token.isNotEmpty()) {
                android.util.Log.d("MainActivity", "Token found in fragment")
                return token
            }
        }

        // Try to get from path parameter: /auth-callback/TOKEN
        val lastSegment = uri.lastPathSegment
        if (lastSegment != null && lastSegment != "auth-callback") {
            android.util.Log.d("MainActivity", "Token found in path segment")
            return lastSegment
        }

        android.util.Log.w("MainActivity", "No token found in URI: $uri")
        return null
    }
}

@Composable
fun PureStreamApp(
    shouldResetApp: Boolean = false,
    onResetHandled: () -> Unit = {},
    shouldRefreshData: Boolean = false,
    onDataRefreshHandled: () -> Unit = {},
    wasRestoredAfterProcessDeath: Boolean = false
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val plexRepository = remember { com.purestream.data.repository.PlexRepository(context) }
    var libraryRefreshRequired by remember { mutableStateOf(false) }
    
    // Shared repositories
    val sharedPlexAuthRepository = remember { com.purestream.data.repository.PlexAuthRepository(context) }
    val sharedProfileRepository = remember { ProfileRepository(context) }
    val database = remember { AppDatabase.getDatabase(context) }
    val sharedWatchProgressRepository = remember { com.purestream.data.repository.WatchProgressRepository(database, context) }
    val subtitleAnalysisRepository = remember { SubtitleAnalysisRepository(database, context) }
    val profanityFilter = remember { ProfanityFilter() }
    val openSubtitlesRepository = remember { OpenSubtitlesRepository(profanityFilter, subtitleAnalysisRepository) }
    
    // Shared ViewModels to prevent recreation and improve performance
    val sharedHomeViewModel: HomeViewModel = viewModel { 
        HomeViewModel(
            context = context, 
            plexRepository = plexRepository,
            profileRepository = sharedProfileRepository
        ) 
    }
    val sharedMoviesViewModel: com.purestream.ui.viewmodel.MoviesViewModel = viewModel { 
        com.purestream.ui.viewmodel.MoviesViewModel(
            context = context,
            plexRepository = plexRepository
        ) 
    }
    val sharedTvShowsViewModel: com.purestream.ui.viewmodel.TvShowsViewModel = viewModel { 
        com.purestream.ui.viewmodel.TvShowsViewModel(
            context = context,
            plexRepository = plexRepository
        ) 
    }
    val sharedHeroMovieDetailsViewModel: com.purestream.ui.viewmodel.MovieDetailsViewModel = viewModel {
        com.purestream.ui.viewmodel.MovieDetailsViewModel(
            context = context,
            plexRepository = plexRepository,
            openSubtitlesRepository = openSubtitlesRepository,
            subtitleAnalysisRepository = subtitleAnalysisRepository
        )
    }
    val sharedSettingsViewModel: com.purestream.ui.viewmodel.SettingsViewModel = viewModel {
        com.purestream.ui.viewmodel.SettingsViewModel(context)
    }
    val sharedPlexAuthViewModel: PlexAuthViewModel = viewModel {
        PlexAuthViewModel(context.applicationContext as Application)
    }
    val sharedMediaPlayerViewModel: com.purestream.ui.viewmodel.MediaPlayerViewModel = viewModel {
        com.purestream.ui.viewmodel.MediaPlayerViewModel(
            context = context,
            plexRepository = plexRepository
        )
    }
    val sharedTvShowDetailsViewModel: com.purestream.ui.viewmodel.TvShowDetailsViewModel = viewModel {
        com.purestream.ui.viewmodel.TvShowDetailsViewModel(
            context = context,
            plexRepository = plexRepository,
            openSubtitlesRepository = openSubtitlesRepository,
            subtitleAnalysisRepository = subtitleAnalysisRepository
        )
    }

    // Handle process death restoration
    // When Android kills the app process and restores it (e.g., after TV is turned off for hours),
    // ViewModels are recreated with empty state but navigation is restored to the previous screen.
    // This causes broken screens with no data. We redirect to ProfileSelection to properly reinitialize.
    LaunchedEffect(wasRestoredAfterProcessDeath) {
        if (wasRestoredAfterProcessDeath) {
            android.util.Log.w("MainActivity", "Process death detected - redirecting to ProfileSelection to reinitialize app state")

            // Clear any stale ViewModel data
            sharedHomeViewModel.clearData()

            // Navigate to ProfileSelection (Plex auth token should still be valid)
            navController.navigate(Destinations.PROFILE_SELECTION) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Handle background reset
    LaunchedEffect(shouldResetApp) {
        if (shouldResetApp) {
            android.util.Log.d("MainActivity", "Performing app reset to GetStarted")
            navController.navigate(Destinations.GET_STARTED) {
                popUpTo(0) { inclusive = true }
            }
            onResetHandled()
        }
    }

    // Handle data refresh after prolonged background time
    LaunchedEffect(shouldRefreshData) {
        if (shouldRefreshData) {
            android.util.Log.d("MainActivity", "Performing data refresh - reloading profile and content")

            // Refresh profile data from database to get latest changes
            sharedHomeViewModel.refreshCurrentProfile()

            // Reload home screen content (dashboard sections)
            sharedHomeViewModel.loadContent()

            // Note: Movies and TV Shows will refresh automatically when user navigates to those screens
            // since they have LaunchedEffect that loads content when profile changes

            onDataRefreshHandled()
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Destinations.GET_STARTED
    ) {
        composable(Destinations.GET_STARTED) {
            android.util.Log.d("MainActivity", "=== GET_STARTED COMPOSABLE RENDERED ===")

            // Skip GetStartedScreen's auto-navigation if user just logged out
            val skipAutoNavigation = sharedPlexAuthViewModel.justLoggedOut
            android.util.Log.d("MainActivity", "GET_STARTED: skipAutoNavigation = $skipAutoNavigation")

            // Auto-login for default profile
            val context = LocalContext.current
            val profileRepository = remember { ProfileRepository(context) }
            val profileManager = remember { ProfileManager.getInstance(context) }

            LaunchedEffect(skipAutoNavigation) {
                if (!skipAutoNavigation) {
                    android.util.Log.d("MainActivity", "GET_STARTED: Checking for default profile...")
                    val defaultProfile = profileRepository.getDefaultProfile()

                    if (defaultProfile != null) {
                        android.util.Log.d("MainActivity", "GET_STARTED: Found default profile '${defaultProfile.name}', auto-logging in...")
                        profileManager.setCurrentProfile(defaultProfile)

                        // Navigate to loading screen, bypassing profile selection
                        navController.navigate(Destinations.loading()) {
                            popUpTo(Destinations.GET_STARTED) { inclusive = true }
                        }
                    } else {
                        android.util.Log.d("MainActivity", "GET_STARTED: No default profile found")
                    }
                }
            }

            GetStartedScreen(
                onNavigateToProfileSelection = {
                    navController.navigate(Destinations.PROFILE_SELECTION) {
                        popUpTo(Destinations.GET_STARTED) { inclusive = true }
                    }
                },
                onNavigateToConnectPlex = {
                    android.util.Log.d("MainActivity", "GET_STARTED: onNavigateToConnectPlex called")
                    navController.navigate(Destinations.CONNECT_PLEX) {
                        popUpTo(Destinations.GET_STARTED) { inclusive = true }
                    }
                },
                onNavigateToFeatureShowcase = {
                    navController.navigate(Destinations.FEATURE_SHOWCASE) {
                        popUpTo(Destinations.GET_STARTED) { inclusive = true }
                    }
                },
                skipAutoNavigation = skipAutoNavigation
            )
        }
        
        composable(Destinations.FEATURE_SHOWCASE) {
            val context = LocalContext.current
            FeatureShowcaseScreen(
                onLetsGoClick = {
                    // Mark feature showcase as shown
                    val sharedPrefs = context.getSharedPreferences("pure_stream_prefs", android.content.Context.MODE_PRIVATE)
                    sharedPrefs.edit().putBoolean("has_shown_feature_showcase", true).apply()
                    
                    // Check for existing Plex token and navigate accordingly
                    val authRepository = com.purestream.data.repository.PlexAuthRepository(context)
                    val storedToken = authRepository.getAuthToken()
                    
                    if (storedToken != null) {
                        navController.navigate(Destinations.PROFILE_SELECTION) {
                            popUpTo(Destinations.FEATURE_SHOWCASE) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Destinations.CONNECT_PLEX) {
                            popUpTo(Destinations.FEATURE_SHOWCASE) { inclusive = true }
                        }
                    }
                }
            )
        }
        
        composable(Destinations.CONNECT_PLEX) {
            android.util.Log.d("MainActivity", "=== CONNECT_PLEX COMPOSABLE RENDERED ===")
            android.util.Log.d("MainActivity", "CONNECT_PLEX: justLoggedOut = ${sharedPlexAuthViewModel.justLoggedOut}, authStatus = ${sharedPlexAuthViewModel.authStatus}")
            
            val coroutineScope = rememberCoroutineScope()

            ConnectPlexScreen(
                onConnectPlexClick = {
                    android.util.Log.d("MainActivity", "CONNECT_PLEX: onConnectPlexClick called - navigating to PROFILE_SELECTION")
                    navController.navigate(Destinations.PROFILE_SELECTION)
                },
                onNavigateToPin = { pinId, pinCode ->
                    navController.navigate(Destinations.plexPin(pinId, pinCode))
                },
                onNavigateToWebAuth = {
                    navController.navigate(Destinations.PLEX_WEB_AUTH)
                },
                onDemoModeClick = {
                    android.util.Log.d("MainActivity", "Demo Mode button clicked - clearing ViewModels and deleting ALL profiles")
                    coroutineScope.launch {
                        try {
                            // Clear all ViewModel states to prevent data leakage
                            sharedTvShowDetailsViewModel.clearState()
                            sharedHeroMovieDetailsViewModel.clearState()
                            android.util.Log.d("MainActivity", "Cleared TvShowDetails and MovieDetails ViewModels")

                            // Delete all profiles
                            sharedProfileRepository.deleteAllProfiles()
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to delete all profiles: ${e.message}")
                        }
                    }
                },
                viewModel = sharedPlexAuthViewModel  // CRITICAL FIX: Use shared ViewModel instance
            )
        }

        composable(Destinations.PLEX_WEB_AUTH) {
            // Debug logging to diagnose blank screen issue
            android.util.Log.d("MainActivity", "PLEX_WEB_AUTH composable - webAuthUrl: ${sharedPlexAuthViewModel.webAuthUrl != null}, oauthStatus: ${sharedPlexAuthViewModel.oauthStatus}")

            // Handle system back button to reset oauth status
            androidx.activity.compose.BackHandler {
                android.util.Log.d("MainActivity", "PLEX_WEB_AUTH - Back button pressed, cancelling auth")
                sharedPlexAuthViewModel.cancelWebViewAuth()
                navController.popBackStack()
            }

            // Handle different states: loading, ready, or error
            when {
                sharedPlexAuthViewModel.webAuthUrl != null -> {
                    // WebView is ready with auth URL
                    PlexWebAuthScreen(
                        authUrl = sharedPlexAuthViewModel.webAuthUrl!!,
                        onTokenReceived = { token ->
                            sharedPlexAuthViewModel.handleWebViewToken(token)
                        },
                        onCancel = {
                            sharedPlexAuthViewModel.cancelWebViewAuth()
                            navController.popBackStack()
                        }
                    )
                }
                sharedPlexAuthViewModel.oauthStatus == AuthenticationStatus.LOADING -> {
                    // Show loading screen while generating PIN and URL
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFFE5A00D)
                            )
                            Text(
                                text = "Preparing authentication...",
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                sharedPlexAuthViewModel.oauthStatus == AuthenticationStatus.ERROR -> {
                    // Show error state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "Authentication Error",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B6B)
                            )
                            Text(
                                text = sharedPlexAuthViewModel.oauthErrorMessage ?: "Failed to initialize authentication",
                                fontSize = 14.sp,
                                color = Color(0xFFB3B3B3),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { navController.popBackStack() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE5A00D),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
                else -> {
                    // Fallback case - should not happen, but prevents blank screen
                    android.util.Log.w("MainActivity", "PLEX_WEB_AUTH - unexpected state, showing loading")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color(0xFFE5A00D)
                            )
                            Text(
                                text = "Loading...",
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Navigate to ProfileSelection on success
            LaunchedEffect(sharedPlexAuthViewModel.oauthStatus) {
                if (sharedPlexAuthViewModel.oauthStatus == AuthenticationStatus.SUCCESS) {
                    navController.navigate(Destinations.PROFILE_SELECTION) {
                        popUpTo(Destinations.PLEX_WEB_AUTH) { inclusive = true }
                    }
                }
            }
        }

        composable(
            route = Destinations.PLEX_PIN,
            arguments = listOf(
                navArgument("pinId") { type = NavType.StringType },
                navArgument("pinCode") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val pinId = backStackEntry.arguments?.getString("pinId") ?: ""
            val pinCode = backStackEntry.arguments?.getString("pinCode") ?: ""
            
            PlexPinScreen(
                pinId = pinId,
                pinCode = pinCode,
                onBackToSignIn = {
                    navController.popBackStack(Destinations.CONNECT_PLEX, false)
                },
                onAuthenticationSuccess = {
                    navController.navigate(Destinations.PROFILE_SELECTION) {
                        popUpTo(Destinations.CONNECT_PLEX) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Destinations.PROFILE_SELECTION) {
            android.util.Log.d("MainActivity", "=== PROFILE_SELECTION COMPOSABLE RENDERED ===")

            val context = LocalContext.current
            val profileRepository = ProfileRepository(context)
            val profileSelectionViewModel: ProfileSelectionViewModel = viewModel {
                ProfileSelectionViewModel(profileRepository, context)
            }
            val profileSelectionState by profileSelectionViewModel.uiState.collectAsStateWithLifecycle()
            val profileManager = ProfileManager.getInstance(context)

            // Refresh profiles from database when returning to this screen (e.g., after editing)
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        android.util.Log.d("MainActivity", "ProfileSelection resumed - reloading profiles")
                        profileSelectionViewModel.loadProfiles()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Get premium status directly from PremiumStatusManager (includes temporary premium from cheat code)
            val premiumStatusManager = remember { com.purestream.data.manager.PremiumStatusManager.getInstance(context) }
            val premiumStatusState by premiumStatusManager.premiumStatus.collectAsStateWithLifecycle()
            val isPremium = premiumStatusState is com.purestream.data.manager.PremiumStatusState.Premium

            ProfileSelectionScreen(
                profiles = profileSelectionState.profiles,
                onProfileSelect = { profile: Profile ->
                    profileSelectionViewModel.selectProfile(profile)
                    navController.navigate(Destinations.loading()) {
                        popUpTo(Destinations.PROFILE_SELECTION) { inclusive = true }
                    }
                },
                onCreateProfile = {
                    navController.navigate(Destinations.PROFILE_CREATE)
                },
                onDeleteProfile = { profile: Profile ->
                    profileSelectionViewModel.deleteProfile(profile)
                },
                onEditProfile = { profile: Profile ->
                    navController.navigate(Destinations.profileEdit(profile.id))
                },
                onBackClick = {
                    // Explicitly logout when going back from profile selection to prevent auto-redirect loop
                    android.util.Log.d("MainActivity", "Back clicked on Profile Selection - logging out to prevent loop")
                    sharedPlexAuthViewModel.logout()
                    navController.popBackStack()
                },
                isPremium = isPremium
            )
            
            // Show error if any
            profileSelectionState.error?.let { error ->
                LaunchedEffect(error) {
                    android.util.Log.e("MainActivity", "Profile selection error: $error")
                }
            }
        }
        
        composable(Destinations.PROFILE_CREATE) {
            val context = LocalContext.current
            val profileManager = ProfileManager.getInstance(context)
            
            // Create SettingsViewModel to get premium status
            val createProfileSettingsViewModel: com.purestream.ui.viewmodel.SettingsViewModel = viewModel { 
                com.purestream.ui.viewmodel.SettingsViewModel(context) 
            }
            val createProfileSettingsState by createProfileSettingsViewModel.uiState.collectAsStateWithLifecycle()
            
            CreateProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onProfileCreated = { profile: Profile ->
                    // Navigate to loading screen to properly initialize content
                    // (ProfileManager is already set in ProfileViewModel.createProfile)
                    navController.navigate(Destinations.loading()) {
                        // Clear the back stack so user can't go back to profile creation
                        popUpTo(Destinations.PROFILE_SELECTION) { inclusive = true }
                    }
                },
                isPremium = createProfileSettingsState.appSettings.isPremium
            )
        }
        
        composable(
            route = Destinations.PROFILE_EDIT,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: return@composable
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            // Create SettingsViewModel to get premium status
            val editProfileSettingsViewModel: com.purestream.ui.viewmodel.SettingsViewModel = viewModel {
                com.purestream.ui.viewmodel.SettingsViewModel(context)
            }
            val editProfileSettingsState by editProfileSettingsViewModel.uiState.collectAsStateWithLifecycle()

            EditProfileScreen(
                profileId = profileId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onProfileUpdated = { profile: Profile ->
                    // Profile is already saved to database by EditProfileScreen
                    // ProfileSelectionScreen will reload fresh data when we navigate back
                    navController.popBackStack()
                },
                isPremium = editProfileSettingsState.appSettings.isPremium
            )
        }
        
        composable(
            route = Destinations.LOADING,
            arguments = listOf(navArgument("workRequestId") {
                type = NavType.StringType
                nullable = true
            })
        ) { backStackEntry ->
            val workRequestId = backStackEntry.arguments?.getString("workRequestId")
            val profileManager = ProfileManager.getInstance(context)
            val currentProfile by profileManager.currentProfile.collectAsStateWithLifecycle()

            // CRITICAL: Reset dashboard collections to defaults for Free users BEFORE loading home screen
            LaunchedEffect(currentProfile?.id) {
                currentProfile?.let { profile ->
                    val premiumManager = PremiumStatusManager.getInstance(context)
                    val premiumStatus = premiumManager.premiumStatus.value
                    val isPremium = premiumStatus is PremiumStatusState.Premium

                    if (!isPremium) {
                        val currentCollections = profile.dashboardCollections
                        val defaultCollections = Profile.getDefaultCollections()

                        // Check if collections differ from defaults
                        val needsReset = currentCollections.size != defaultCollections.size ||
                            currentCollections.any { current ->
                                val default = defaultCollections.find { it.id == current.id }
                                default == null || current.isEnabled != default.isEnabled || current.order != default.order
                            }

                        if (needsReset) {
                            android.util.Log.d("MainActivity", "Free user with custom collections on loading - resetting to defaults")
                            val updatedProfile = profile.copy(dashboardCollections = defaultCollections)
                            sharedProfileRepository.updateProfile(updatedProfile)
                        }
                    }
                }
            }

            LoadingScreen(
                onLoadingComplete = {
                    navController.navigate(Destinations.HOME) {
                        // Clear the loading screen from back stack
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                homeViewModel = sharedHomeViewModel,
                moviesViewModel = sharedMoviesViewModel,
                tvShowsViewModel = sharedTvShowsViewModel,
                currentProfile = currentProfile,
                workRequestId = workRequestId,
                profileRepository = sharedProfileRepository
            )
        }
        
        composable(Destinations.UPGRADE) {
            UpgradeScreen(
                onUpgradeClick = {
                    // Handle premium upgrade
                    navController.navigate(Destinations.loading())
                },
                onContinueFreeClick = {
                    navController.navigate(Destinations.loading())
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Destinations.HOME) {
            val context = LocalContext.current
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val profileManager = ProfileManager.getInstance(context)
            val currentProfile by profileManager.currentProfile.collectAsStateWithLifecycle()

            // Use shared MovieDetailsViewModel for hero section play functionality
            val heroMovieDetailsState by sharedHeroMovieDetailsViewModel.uiState.collectAsStateWithLifecycle()

            // Refresh profile from database on each navigation to Home
            LaunchedEffect(Unit) {
                sharedHomeViewModel.refreshCurrentProfile()
            }

            // Consolidated initialization and profile management
            LaunchedEffect(currentProfile?.id) {
                // Update home view model when profile changes
                currentProfile?.let { profile ->
                    android.util.Log.i("MainActivity", "*** PROFILE CHANGED *** ID: ${profile.id}, Name: '${profile.name}', Filter Level: ${profile.profanityFilterLevel}")

                    sharedHomeViewModel.setCurrentProfile(profile)

                    // Setup hero movie details ViewModel with proper auth for each profile
                    setupPlexConnection(sharedPlexAuthRepository, sharedHeroMovieDetailsViewModel, profile)

                    // Update ProfanityFilter with profile data
                    android.util.Log.d("MainActivity", "Updating ProfanityFilter with profile data: ${profile.customFilteredWords.size} custom words, ${profile.whitelistedWords.size} whitelist words")
                    profanityFilter.setCustomFilteredWords(profile.customFilteredWords)
                    profanityFilter.setWhitelistedWords(profile.whitelistedWords)

                    // Check if free users have unauthorized premium collections
                    val premiumManager = PremiumStatusManager.getInstance(context)
                    val premiumStatus = premiumManager.premiumStatus.value
                    val isPremium = premiumStatus is PremiumStatusState.Premium

                    if (!isPremium) {
                        val defaultCollections = Profile.getDefaultCollections()

                        // Check for PLEX collections (custom Plex collections - premium only)
                        val hasPlexCollections = profile.dashboardCollections.any {
                            it.type == com.purestream.data.model.CollectionType.PLEX
                        }

                        // Check for GEMINI collections (AI curation - premium only)
                        val hasGeminiCollections = profile.dashboardCollections.any {
                            it.type == com.purestream.data.model.CollectionType.GEMINI
                        }

                        if (hasPlexCollections || hasGeminiCollections) {
                            android.util.Log.d("MainActivity", "Free user with premium collections - removing them")

                            // Free users can only have default HARDCODED collections
                            val updatedProfile = profile.copy(
                                dashboardCollections = defaultCollections,
                                aiCuratedEnabled = false,  // Disable AI curation flag
                                aiFeaturedMovieRatingKey = null  // Clear AI featured movie
                            )
                            sharedProfileRepository.updateProfile(updatedProfile)
                            sharedHomeViewModel.refreshCurrentProfile()
                        }
                    }
                }
            }
            
            // Fetch progress for featured content (refreshes when navigating back to HOME)
            var featuredContentProgress by remember { mutableStateOf<Float?>(null) }
            var featuredContentPosition by remember { mutableStateOf<Long?>(null) }
            var progressRefreshTrigger by remember { mutableStateOf(0) }

            // Trigger progress refresh when returning to HOME
            DisposableEffect(Unit) {
                val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                    if (destination.route == Destinations.HOME) {
                        progressRefreshTrigger++
                    }
                }
                navController.addOnDestinationChangedListener(listener)
                onDispose {
                    navController.removeOnDestinationChangedListener(listener)
                }
            }

            // Load featured movie into hero ViewModel to check for existing analysis
            LaunchedEffect(homeState.featuredMovie?.id) {
                val featuredMovie = homeState.featuredMovie
                if (featuredMovie != null && featuredMovie.type == com.purestream.data.model.ContentType.MOVIE) {
                    // Fetch full movie details and load into hero ViewModel
                    coroutineScope.launch {
                        val movieResult = plexRepository.getMovieById(featuredMovie.id)
                        movieResult.getOrNull()?.let { movie ->
                            sharedHeroMovieDetailsViewModel.setMovie(movie)
                            sharedHeroMovieDetailsViewModel.checkCanAnalyzeProfanity()
                        }
                    }
                }
            }

            LaunchedEffect(homeState.featuredMovie?.id, currentProfile?.id, progressRefreshTrigger) {
                val profile = currentProfile
                val movie = homeState.featuredMovie
                if (movie != null && profile != null) {
                    val progress = sharedWatchProgressRepository.getProgress(profile.id, movie.id)
                    if (progress != null && progress.duration > 0) {
                        featuredContentPosition = progress.position
                        featuredContentProgress = (progress.position.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        featuredContentProgress = null
                        featuredContentPosition = null
                    }
                } else {
                    featuredContentProgress = null
                    featuredContentPosition = null
                }
            }
            
            HomeScreen(
                currentProfile = homeState.currentProfile,
                contentSections = homeState.contentSections,
                featuredContent = homeState.featuredMovie,
                isLoading = homeState.isLoading,
                error = homeState.error,
                featuredContentProgress = featuredContentProgress,
                featuredContentPosition = featuredContentPosition,
                canAnalyzeProfanity = heroMovieDetailsState.canAnalyzeProfanity,
                onSearchClick = { 
                    navController.navigate(Destinations.SEARCH)
                },
                onContentClick = { content ->
                    when (content.type) {
                        com.purestream.data.model.ContentType.MOVIE -> {
                            navController.navigate(Destinations.movieDetails(content.id))
                        }
                        com.purestream.data.model.ContentType.TV_SHOW -> {
                            navController.navigate(Destinations.tvShowDetails(content.id))
                        }
                        com.purestream.data.model.ContentType.EPISODE -> {
                            navController.navigate(Destinations.episodeDetails(content.id))
                        }
                    }
                },
                onSwitchUser = {
                    navController.navigate(Destinations.PROFILE_SELECTION)
                },
                onSettings = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onMoviesClick = {
                    coroutineScope.launch { sharedMoviesViewModel.gridState.scrollToItem(0) }
                    sharedMoviesViewModel.clearLastFocusedMovieId()
                    navController.navigate(Destinations.MOVIES)
                },
                onTvShowsClick = {
                    coroutineScope.launch { sharedTvShowsViewModel.gridState.scrollToItem(0) }
                    sharedTvShowsViewModel.clearLastFocusedTvShowId()
                    navController.navigate(Destinations.TV_SHOWS)
                },
                onPlayFeatured = { startPosition ->
                    // Directly play the featured movie
                    homeState.featuredMovie?.let { featuredContent ->
                        if (featuredContent.type == com.purestream.data.model.ContentType.MOVIE) {
                            // Use the movie from heroMovieDetailsViewModel which has analysis info
                            val heroState = sharedHeroMovieDetailsViewModel.uiState.value
                            heroState.movie?.let { movie ->
                                // Auto-trigger analysis in background if not done
                                if (heroState.canAnalyzeProfanity) {
                                    // Use current profile's filter level as priority for faster padlock update
                                    val priorityLevel = homeState.currentProfile?.profanityFilterLevel ?: com.purestream.data.model.ProfanityFilterLevel.MILD
                                    sharedHeroMovieDetailsViewModel.analyzeMovieProfanityAllLevels(movie, priorityLevel)
                                }

                                // Set start position
                                sharedMediaPlayerViewModel.setStartPosition(startPosition)

                                // Store media and navigate instantly
                                sharedMediaPlayerViewModel.setCurrentMedia(movie = movie)
                                navController.navigate(Destinations.mediaPlayer(movie.ratingKey, "movie"))
                            }
                        } else {
                            // Handle episodes if featured content is an episode
                            navController.navigate(Destinations.movieDetails(featuredContent.id))
                        }
                    }
                },
                onRetry = {
                    sharedHomeViewModel.clearError()
                    sharedHomeViewModel.loadContent()
                },
                onNavigateToLevelUpStats = {
                    navController.navigate(Destinations.LEVEL_UP_STATS)
                }
            )
        }
        
        composable(Destinations.MOVIES) {
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val profileManager = ProfileManager.getInstance(context)
            val currentProfile by profileManager.currentProfile.collectAsStateWithLifecycle()
            val moviesState by sharedMoviesViewModel.uiState.collectAsStateWithLifecycle()
            val lastFocusedMovieId by sharedMoviesViewModel.lastFocusedMovieId.collectAsStateWithLifecycle()

            // Initialize Movies ViewModel with profile-based library filtering (optimized)
            LaunchedEffect(currentProfile?.id, currentProfile?.selectedLibraries) { // Trigger when profile ID OR selected libraries change
                currentProfile?.let { profile ->
                    // Only reload if libraries are empty or profile/libraries changed
                    val needsReload = moviesState.libraries.isEmpty() ||
                                     sharedMoviesViewModel.getCurrentProfile()?.id != profile.id ||
                                     sharedMoviesViewModel.getCurrentProfile()?.selectedLibraries != profile.selectedLibraries

                    if (needsReload) {
                        // Set current profile for library preference tracking
                        sharedMoviesViewModel.setCurrentProfile(profile)
                        // Initialize progress tracking FIRST before loading any items
                        sharedMoviesViewModel.initializeProgressTracking(context, profile.id)
                        // Then setup Plex connection which will trigger loadItems
                        setupPlexConnection(sharedPlexAuthRepository, sharedMoviesViewModel, profile)
                    }
                }
            }

            MoviesScreen(
                currentProfile = currentProfile,
                movies = moviesState.items,
                libraries = moviesState.libraries,
                selectedLibraryId = moviesState.selectedLibraryId,
                isLoading = moviesState.isLoading,
                isLoadingMore = moviesState.isLoadingMore,
                canLoadMore = moviesState.canLoadMore,
                error = moviesState.error,
                gridState = sharedMoviesViewModel.gridState,
                lastFocusedMovieId = lastFocusedMovieId,
                progressMap = moviesState.progressMap,  // Pass progress data
                onSearchClick = {
                    navController.navigate(Destinations.SEARCH)
                },
                onHomeClick = {
                    navController.navigate(Destinations.HOME)
                },
                onTvShowsClick = {
                    coroutineScope.launch { sharedTvShowsViewModel.gridState.scrollToItem(0) }
                    sharedTvShowsViewModel.clearLastFocusedTvShowId()
                    navController.navigate(Destinations.TV_SHOWS)
                },
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onSwitchUser = {
                    navController.navigate(Destinations.PROFILE_SELECTION)
                },
                onMovieClick = { movie ->
                    // Save the focused movie ID for restoration when returning
                    sharedMoviesViewModel.setLastFocusedMovieId(movie.id)
                    navController.navigate(Destinations.movieDetails(movie.id))
                },
                onRetry = {
                    sharedMoviesViewModel.refreshMovies()
                },
                onLoadMore = {
                    sharedMoviesViewModel.loadMoreMovies()
                },
                onLibrarySelected = { libraryId ->
                    homeState.currentProfile?.let { profile ->
                        sharedMoviesViewModel.selectLibrary(libraryId)
                        sharedMoviesViewModel.setPreferredLibrary(
                            libraryId,
                            sharedProfileRepository,
                            profile.id
                        )
                    }
                }
            )
        }
        
        composable(Destinations.TV_SHOWS) {
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val profileManager = ProfileManager.getInstance(context)
            val currentProfile by profileManager.currentProfile.collectAsStateWithLifecycle()
            val tvShowsState by sharedTvShowsViewModel.uiState.collectAsStateWithLifecycle()
            val lastFocusedTvShowId by sharedTvShowsViewModel.lastFocusedTvShowId.collectAsStateWithLifecycle()

            // Initialize TV Shows ViewModel with profile-based library filtering (optimized)
            LaunchedEffect(currentProfile?.id, currentProfile?.selectedLibraries) { // Trigger when profile ID OR selected libraries change
                currentProfile?.let { profile ->
                    // Only reload if libraries are empty or profile/libraries changed
                    val needsReload = tvShowsState.libraries.isEmpty() ||
                                     sharedTvShowsViewModel.getCurrentProfile()?.id != profile.id ||
                                     sharedTvShowsViewModel.getCurrentProfile()?.selectedLibraries != profile.selectedLibraries

                    if (needsReload) {
                        // Set current profile for library preference tracking
                        sharedTvShowsViewModel.setCurrentProfile(profile)
                        setupPlexConnection(sharedPlexAuthRepository, sharedTvShowsViewModel, profile)
                    }
                }
            }

            TvShowsScreen(
                currentProfile = currentProfile,
                tvShows = tvShowsState.items,
                libraries = tvShowsState.libraries,
                selectedLibraryId = tvShowsState.selectedLibraryId,
                isLoading = tvShowsState.isLoading,
                error = tvShowsState.error,
                gridState = sharedTvShowsViewModel.gridState,
                lastFocusedTvShowId = lastFocusedTvShowId,
                onSearchClick = {
                    navController.navigate(Destinations.SEARCH)
                },
                onHomeClick = {
                    navController.navigate(Destinations.HOME)
                },
                onMoviesClick = {
                    coroutineScope.launch { sharedMoviesViewModel.gridState.scrollToItem(0) }
                    sharedMoviesViewModel.clearLastFocusedMovieId()
                    navController.navigate(Destinations.MOVIES)
                },
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onSwitchUser = {
                    navController.navigate(Destinations.PROFILE_SELECTION)
                },
                onTvShowClick = { tvShow ->
                    // Save the focused TV show ID for restoration when returning
                    sharedTvShowsViewModel.setLastFocusedTvShowId(tvShow.id)
                    navController.navigate(Destinations.tvShowDetails(tvShow.id))
                },
                onRetry = {
                    sharedTvShowsViewModel.refreshTvShows()
                },
                onLoadMore = {
                    sharedTvShowsViewModel.loadMoreTvShows()
                },
                onLibrarySelected = { libraryId ->
                    homeState.currentProfile?.let { profile ->
                        sharedTvShowsViewModel.selectLibrary(libraryId)
                        sharedTvShowsViewModel.setPreferredLibrary(
                            libraryId,
                            sharedProfileRepository,
                            profile.id
                        )
                    }
                },
                isLoadingMore = tvShowsState.isLoadingMore
            )
        }
        
        composable(Destinations.LEVEL_UP_STATS) {
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val currentProfile = homeState.currentProfile
            
            var filthiestMovieTitle by remember { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val repo = SubtitleAnalysisRepository(db, context)
                        val title = repo.getFilthiestMovieTitle()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            filthiestMovieTitle = title
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            
            if (currentProfile != null) {
                com.purestream.ui.components.LevelUpStatCard(
                    profile = currentProfile,
                    filthiestMovieTitle = filthiestMovieTitle,
                    onDismiss = {
                        navController.popBackStack()
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable(Destinations.SETTINGS) {
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            
            // Optimized initialization
            LaunchedEffect(Unit) {
                if (homeState.currentProfile == null) {
                    val demoProfile = Profile(
                        id = "demo",
                        name = "Demo User",
                        avatarImage = "cat_avatar",
                        profileType = com.purestream.data.model.ProfileType.ADULT,
                        profanityFilterLevel = com.purestream.data.model.ProfanityFilterLevel.MILD,
                        selectedLibraries = listOf("demo_library_movies", "demo_library_shows")
                    )
                    sharedHomeViewModel.setCurrentProfile(demoProfile)
                }
            }
            
            // Create SettingsViewModel to manage app settings
            val settingsViewModel: com.purestream.ui.viewmodel.SettingsViewModel = viewModel { 
                com.purestream.ui.viewmodel.SettingsViewModel(context) 
            }
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            
            // Observe premium status changes and refresh profile data when it changes
            val premiumStatusManager = remember { PremiumStatusManager.getInstance(context) }
            val premiumStatus by premiumStatusManager.premiumStatus.collectAsStateWithLifecycle()
            
            val watchProgressRepository = remember { com.purestream.data.repository.WatchProgressRepository(database, context) }
            val achievementManager = remember { com.purestream.data.manager.AchievementManager(context, sharedProfileRepository, watchProgressRepository, database) }

            LaunchedEffect(premiumStatus) {
                // Check Power User achievement when becoming Premium
                if (premiumStatus is PremiumStatusState.Premium) {
                    homeState.currentProfile?.let { profile ->
                        achievementManager.checkPowerUser(profile.id)
                    }
                }

                // When premium status changes (especially from Premium to Free), refresh profile data
                if (premiumStatus is PremiumStatusState.Free || premiumStatus is PremiumStatusState.Premium) {
                    android.util.Log.w("MainActivity", "Premium status changed to $premiumStatus, syncing profile data...")

                    // Add a small delay to ensure PremiumStatusManager has completed database updates
                    delay(500)

                    // Force refresh both HomeViewModel and ProfileManager to stay in sync
                    try {
                        val profileManager = ProfileManager.getInstance(context)
                        val currentProfile = profileManager.currentProfile.value

                        if (currentProfile != null) {
                            android.util.Log.d("MainActivity", "Current profile before sync: '${currentProfile.name}' (Filter: ${currentProfile.profanityFilterLevel})")

                            // Refresh HomeViewModel from database first
                            sharedHomeViewModel.refreshCurrentProfile()

                            // Small delay to let HomeViewModel refresh complete
                            delay(200)

                            // Then ensure ProfileManager has the latest data
                            val updatedProfile = sharedProfileRepository.getProfileById(currentProfile.id)
                            if (updatedProfile != null) {
                                android.util.Log.w("MainActivity", "Syncing ProfileManager with updated profile: '${updatedProfile.name}' (Filter: ${updatedProfile.profanityFilterLevel})")
                                profileManager.setCurrentProfile(updatedProfile)
                            }
                        } else {
                            android.util.Log.w("MainActivity", "No current profile to sync")
                            sharedHomeViewModel.refreshCurrentProfile()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error syncing profile after premium status change", e)
                        // Fallback to just HomeViewModel refresh
                        sharedHomeViewModel.refreshCurrentProfile()
                    }

                    android.util.Log.i("MainActivity", "Profile sync completed for premium status: $premiumStatus")
                }
            }
            
            val demoLibraries = listOf(
                com.purestream.data.model.PlexLibrary(
                    key = "demo_library_movies",
                    title = "Demo Movies",
                    type = "movie",
                    agent = "com.plexapp.agents.imdb",
                    scanner = "Plex Movie Scanner",
                    language = "en",
                    uuid = "demo-movies-uuid",
                    updatedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis() - 86400000
                ),
                com.purestream.data.model.PlexLibrary(
                    key = "demo_library_shows",
                    title = "Demo TV Shows", 
                    type = "show",
                    agent = "com.plexapp.agents.thetvdb",
                    scanner = "Plex Series Scanner",
                    language = "en",
                    uuid = "demo-tv-uuid",
                    updatedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis() - 86400000
                ),
                com.purestream.data.model.PlexLibrary(
                    key = "kids_movies",
                    title = "Kids Movies",
                    type = "movie",
                    agent = "com.plexapp.agents.imdb",
                    scanner = "Plex Movie Scanner",
                    language = "en",
                    uuid = "demo-kids-movies-uuid",
                    updatedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis() - 172800000
                ),
                com.purestream.data.model.PlexLibrary(
                    key = "documentaries",
                    title = "Documentaries",
                    type = "movie",
                    agent = "com.plexapp.agents.imdb",
                    scanner = "Plex Movie Scanner",
                    language = "en",
                    uuid = "demo-docs-uuid",
                    updatedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis() - 172800000
                )
            )
            
            SettingsScreen(
                currentProfile = homeState.currentProfile,
                appSettings = settingsState.appSettings,
                availableLibraries = demoLibraries,
                onSearchClick = {
                    navController.navigate(Destinations.SEARCH)
                },
                onHomeClick = {
                    // Refresh profile before navigating to ensure fresh state
                    sharedHomeViewModel.refreshCurrentProfile()
                    navController.navigate(Destinations.HOME)
                },
                onMoviesClick = {
                    coroutineScope.launch { sharedMoviesViewModel.gridState.scrollToItem(0) }
                    sharedMoviesViewModel.clearLastFocusedMovieId()
                    navController.navigate(Destinations.MOVIES)
                },
                onTvShowsClick = {
                    coroutineScope.launch { sharedTvShowsViewModel.gridState.scrollToItem(0) }
                    sharedTvShowsViewModel.clearLastFocusedTvShowId()
                    navController.navigate(Destinations.TV_SHOWS)
                },
                onSwitchUser = {
                    navController.navigate(Destinations.PROFILE_SELECTION)
                },
                onProfanityFilterChange = { newLevel ->
                    homeState.currentProfile?.let { profile ->
                        val updatedProfile = profile.copy(profanityFilterLevel = newLevel)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                        }
                        
                        val profileRepository = com.purestream.data.repository.ProfileRepository(context)
                        coroutineScope.launch {
                            try {
                                profileRepository.updateProfile(updatedProfile)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update profile: ${e.message}")
                            }
                        }
                    }
                },
                onLibrarySelectionChange = { newLibraries ->
                    homeState.currentProfile?.let { profile ->
                        val updatedProfile = profile.copy(selectedLibraries = newLibraries)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                            sharedProfileRepository.updateProfile(updatedProfile)

                            // Force-reset the ViewModels to ensure they reload on next navigation
                            sharedMoviesViewModel.reset()
                            sharedTvShowsViewModel.reset()
                        }
                    }
                },
                onSettingToggle = { setting, value ->
                    // App setting toggles not yet implemented - handled by SettingsViewModel
                },
                onAddCustomProfanity = { word ->
                    homeState.currentProfile?.let { profile ->
                        val updatedCustomWords = (profile.customFilteredWords + word).distinct()
                        val updatedProfile = profile.copy(customFilteredWords = updatedCustomWords)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                        }
                        
                        val profileRepository = com.purestream.data.repository.ProfileRepository(context)
                        coroutineScope.launch {
                            try {
                                profileRepository.updateProfile(updatedProfile)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update profile: ${e.message}")
                            }
                        }
                    }
                },
                onAddWhitelistWord = { word ->
                    homeState.currentProfile?.let { profile ->
                        val updatedWhitelistWords = (profile.whitelistedWords + word).distinct()
                        val updatedProfile = profile.copy(whitelistedWords = updatedWhitelistWords)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                        }
                        
                        val profileRepository = com.purestream.data.repository.ProfileRepository(context)
                        coroutineScope.launch {
                            try {
                                profileRepository.updateProfile(updatedProfile)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update profile: ${e.message}")
                            }
                        }
                    }
                },
                onRemoveCustomProfanity = { word ->
                    homeState.currentProfile?.let { profile ->
                        val updatedCustomWords = profile.customFilteredWords.filter { it != word }
                        val updatedProfile = profile.copy(customFilteredWords = updatedCustomWords)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                        }
                        
                        val profileRepository = com.purestream.data.repository.ProfileRepository(context)
                        coroutineScope.launch {
                            try {
                                profileRepository.updateProfile(updatedProfile)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update profile: ${e.message}")
                            }
                        }
                    }
                },
                onRemoveWhitelistWord = { word ->
                    homeState.currentProfile?.let { profile ->
                        val updatedWhitelistWords = profile.whitelistedWords.filter { it != word }
                        val updatedProfile = profile.copy(whitelistedWords = updatedWhitelistWords)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                        }
                        
                        val profileRepository = com.purestream.data.repository.ProfileRepository(context)
                        coroutineScope.launch {
                            try {
                                profileRepository.updateProfile(updatedProfile)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update profile: ${e.message}")
                            }
                        }
                    }
                },
                onLogout = {
                    android.util.Log.d("MainActivity", "=== LOGOUT INITIATED ===")

                    // Check if we are in demo mode
                    val currentToken = sharedPlexAuthRepository.getAuthToken()
                    if (com.purestream.data.demo.DemoData.isDemoToken(currentToken)) {
                        android.util.Log.d("MainActivity", "Demo mode detected on logout - deleting ALL profiles")
                        coroutineScope.launch {
                            try {
                                sharedProfileRepository.deleteAllProfiles()
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to delete all profiles: ${e.message}")
                            }
                        }
                    }

                    // Use shared ViewModel instance to properly clear auth state
                    sharedPlexAuthViewModel.logout()

                    // Clear cached home data
                    sharedHomeViewModel.clearData()
                    
                    // Reset other shared view models to clear cached content
                    sharedMoviesViewModel.reset()
                    sharedTvShowsViewModel.reset()
                    sharedHeroMovieDetailsViewModel.reset()
                    // sharedMediaPlayerViewModel.reset() // Assuming MediaPlayerViewModel has a reset or clear method, otherwise skip

                    // Note: Don't clear profile cache - user keeps profile after re-login

                    android.util.Log.d("MainActivity", "Navigating to CONNECT_PLEX with popUpTo(0)")

                    // Clear the entire back stack and navigate to CONNECT_PLEX as the only screen
                    navController.navigate(Destinations.CONNECT_PLEX) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }

                    android.util.Log.d("MainActivity", "Navigation triggered - backstack should be cleared")
                },
                onMonthlyUpgradeClick = {
                    android.util.Log.d("MainActivity", "Upgrading to monthly premium...")
                    settingsViewModel.upgradeToPremium(context as android.app.Activity, "monthly")
                },
                onAnnualUpgradeClick = {
                    android.util.Log.d("MainActivity", "Upgrading to annual premium...")
                    settingsViewModel.upgradeToPremium(context as android.app.Activity, "annual")
                },
                onFreeVersionClick = {
                    android.util.Log.d("MainActivity", "Switching to free version...")
                    settingsViewModel.switchToFreeVersion()
                },
                onPreferredLibraryChange = {
                    android.util.Log.d("MainActivity", "Preferred library changed - refreshing home profile")
                    // Refresh HomeViewModel from database
                    sharedHomeViewModel.refreshCurrentProfile()

                    // Also update ProfileManager to keep it in sync
                    coroutineScope.launch {
                        try {
                            homeState.currentProfile?.let { currentProfile ->
                                val updatedProfile = sharedProfileRepository.getProfileById(currentProfile.id)
                                if (updatedProfile != null) {
                                    val profileManager = ProfileManager.getInstance(context)
                                    profileManager.setCurrentProfile(updatedProfile)
                                    android.util.Log.d("MainActivity", "Updated ProfileManager with new dashboard library")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to update ProfileManager: ${e.message}")
                        }
                    }
                },
                onNavigateToLoading = {
                    // Create a one-time work request for the Gemini worker
                    val refreshRequest = androidx.work.OneTimeWorkRequestBuilder<com.purestream.workers.GeminiRefreshWorker>()
                        .build()

                    // Enqueue the work
                    androidx.work.WorkManager.getInstance(context).enqueue(refreshRequest)

                    // Navigate to the loading screen with the work request ID
                    navController.navigate(Destinations.loading(refreshRequest.id.toString()))
                },
                onProfileUpdated = { updatedProfile ->
                    // CRITICAL: Update ProfileManager first to prevent LaunchedEffect from overwriting changes
                    android.util.Log.d("MainActivity", "onProfileUpdated - updating ProfileManager and reloading dashboard for ${updatedProfile.name}")
                    coroutineScope.launch {
                        val profileManager = ProfileManager.getInstance(context)
                        profileManager.setCurrentProfile(updatedProfile)

                        // Then update HomeViewModel (which triggers loadContent internally)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                    }
                },
                onNavigateToLevelUpStats = {
                    navController.navigate(Destinations.LEVEL_UP_STATS)
                }
            )
        }
        
        composable(Destinations.SEARCH) {
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val activity = LocalContext.current as MainActivity
            
            // Create SearchViewModel instance
            val searchViewModel: com.purestream.ui.viewmodel.SearchViewModel = viewModel {
                com.purestream.ui.viewmodel.SearchViewModel(context = activity)
            }
            val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
            
            // Consolidated SearchViewModel initialization and profile setup
            LaunchedEffect(homeState.currentProfile?.id) {
                // Set demo profile if needed
                if (homeState.currentProfile == null) {
                    val demoProfile = Profile(
                        id = "demo",
                        name = "Demo User",
                        avatarImage = "cat_avatar",
                        profileType = com.purestream.data.model.ProfileType.ADULT,
                        profanityFilterLevel = com.purestream.data.model.ProfanityFilterLevel.MILD,
                        selectedLibraries = listOf("movies", "tv_shows")
                    )
                    sharedHomeViewModel.setCurrentProfile(demoProfile)
                }
                
                // Initialize SearchViewModel with Plex connection
                try {
                    val authRepository = com.purestream.data.repository.PlexAuthRepository(context)
                    val authToken = authRepository.getAuthToken()
                    val selectedLibraries = homeState.currentProfile?.selectedLibraries ?: emptyList()
                    
                    if (authToken != null) {
                        searchViewModel.setPlexConnectionWithAuth(authToken, selectedLibraries)
                    } else {
                        android.util.Log.w("MainActivity", "No auth token found for search - using demo connection")
                        searchViewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error setting up Plex connection for search", e)
                    searchViewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                }
            }
            
            SearchScreen(
                currentProfile = homeState.currentProfile,
                searchQuery = searchState.searchQuery,
                searchResults = searchState.searchResults,
                isSearching = searchState.isSearching,
                isLoading = searchState.isSearching,
                hasSearched = searchState.searchQuery.isNotEmpty(),
                onSearchQueryChange = { newQuery ->
                    searchViewModel.updateSearchQuery(newQuery)
                },
                onSearchSubmit = { query ->
                    searchViewModel.submitSearch(query)
                },
                onVoiceSearchStart = {
                    // Use the actual speech recognition functionality
                    activity.startVoiceSearch(searchViewModel)
                },
                onSearchResultClick = { result ->
                    when (result.type.lowercase()) {
                        "movie" -> navController.navigate(Destinations.movieDetails(result.ratingKey))
                        "show" -> navController.navigate(Destinations.tvShowDetails(result.ratingKey))
                        "episode" -> navController.navigate(Destinations.episodeDetails(result.ratingKey))
                    }
                },
                onLoadMore = {
                    searchViewModel.loadMoreResults()
                },
                onHomeClick = {
                    navController.navigate(Destinations.HOME)
                },
                onMoviesClick = {
                    coroutineScope.launch { sharedMoviesViewModel.gridState.scrollToItem(0) }
                    sharedMoviesViewModel.clearLastFocusedMovieId()
                    navController.navigate(Destinations.MOVIES)
                },
                onTvShowsClick = {
                    coroutineScope.launch { sharedTvShowsViewModel.gridState.scrollToItem(0) }
                    sharedTvShowsViewModel.clearLastFocusedTvShowId()
                    navController.navigate(Destinations.TV_SHOWS)
                },
                onSettingsClick = {
                    navController.navigate(Destinations.SETTINGS)
                },
                onSwitchUser = {
                    navController.navigate(Destinations.PROFILE_SELECTION)
                }
            )
            
            // Show error message if search fails
            searchState.error?.let { error ->
                LaunchedEffect(error) {
                    android.util.Log.e("MainActivity", "Search error: $error")
                    // Error handling via SearchScreen UI - dialog not currently implemented
                }
            }
        }
        
        
        // Movie Details Screen
        composable(
            route = Destinations.MOVIE_DETAILS,
            arguments = listOf(navArgument("movieId") { type = NavType.StringType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getString("movieId") ?: return@composable
            val context = LocalContext.current
            
            // Create shared MoviesViewModel to get the movie data
            val moviesCacheViewModel = sharedMoviesViewModel

            // Create MovieDetailsViewModel instance with shared repositories
            val movieDetailsViewModel: com.purestream.ui.viewmodel.MovieDetailsViewModel = viewModel {
                com.purestream.ui.viewmodel.MovieDetailsViewModel(
                    context = context,
                    plexRepository = plexRepository,
                    openSubtitlesRepository = openSubtitlesRepository,
                    subtitleAnalysisRepository = subtitleAnalysisRepository
                )
            }
            val movieDetailsState by movieDetailsViewModel.uiState.collectAsStateWithLifecycle()

            // Pre-populate with movie from cache for instant loading
            LaunchedEffect(movieId) {
                val movieFromCache = moviesCacheViewModel.uiState.value.items.find { movieItem -> movieItem.ratingKey == movieId }
                if (movieFromCache != null && movieDetailsViewModel.uiState.value.movie == null) {
                    android.util.Log.d("MainActivity", "Pre-populating movie details from cache for: ${movieFromCache.title}")
                    movieDetailsViewModel.setMovie(movieFromCache)
                }
            }

            // Get premium status directly from PremiumStatusManager (source of truth)
            val premiumStatusManager = remember { com.purestream.data.manager.PremiumStatusManager.getInstance(context) }
            val premiumStatusState by premiumStatusManager.premiumStatus.collectAsStateWithLifecycle()
            val isPremium = premiumStatusState is com.purestream.data.manager.PremiumStatusState.Premium

            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val activeProfile: com.purestream.data.model.Profile? = homeState.currentProfile

            // Initialize progress tracking FIRST before loading any content
            LaunchedEffect(activeProfile?.id) {
                activeProfile?.let { profile ->
                    movieDetailsViewModel.initializeProgressTracking(context, profile.id)
                }
            }

            // Setup Plex connection and load movie details
            LaunchedEffect(Unit) {
                try {
                    val authRepository = com.purestream.data.repository.PlexAuthRepository(context)
                    val authToken = authRepository.getAuthToken()
                    
                    if (authToken != null) {
                        movieDetailsViewModel.setPlexConnectionWithAuth(authToken)
                        movieDetailsViewModel.loadMovieDetails(movieId)
                    } else {
                        movieDetailsViewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                        movieDetailsViewModel.loadMovieDetails(movieId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error setting up Plex connection for movie details", e)
                    movieDetailsViewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                    movieDetailsViewModel.loadMovieDetails(movieId)
                }
            }
            
            // Refresh progress when returning to this screen
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        android.util.Log.d("MainActivity", "MovieDetails resumed - refreshing progress")
                        movieDetailsViewModel.refreshProgress()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Get the movie from the details view model
            val movie = movieDetailsState.movie
            
            if (movie != null) {
                MovieDetailsScreen(
                    movie = movie,
                    progressPercentage = movieDetailsState.progressPercentage,
                    progressPosition = movieDetailsState.progressPosition,
                    onPlayClick = { startPosition ->
                        // Store start position and media before navigation
                        sharedMediaPlayerViewModel.setStartPosition(startPosition)
                        sharedMediaPlayerViewModel.setCurrentMedia(movie = movie)
                        
                        // Auto-trigger analysis in background if not done
                        if (movieDetailsState.canAnalyzeProfanity) {
                            // Use current profile's filter level as priority for faster padlock update
                            val priorityLevel = activeProfile?.profanityFilterLevel ?: com.purestream.data.model.ProfanityFilterLevel.MILD
                            movieDetailsViewModel.analyzeMovieProfanityAllLevels(movie, priorityLevel)
                        }

                        // NAVIGATE INSTANTLY - the player will fetch the URL while the screen is opening
                        navController.navigate(Destinations.mediaPlayer(movie.ratingKey, "movie"))
                    },
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAnalyzeProfanityClick = { movieToAnalyze ->
                        // Use current profile's filter level as priority for faster padlock update
                        val priorityLevel = activeProfile?.profanityFilterLevel ?: com.purestream.data.model.ProfanityFilterLevel.MILD
                        movieDetailsViewModel.analyzeMovieProfanityAllLevels(movieToAnalyze, priorityLevel)
                    },
                    isAnalyzingSubtitles = movieDetailsState.isAnalyzingSubtitles,
                    subtitleAnalysisResult = movieDetailsState.subtitleAnalysisResult,
                    subtitleAnalysisError = movieDetailsState.subtitleAnalysisError,
                    onClearAnalysisError = {
                        movieDetailsViewModel.clearSubtitleAnalysisError()
                    },
                    canAnalyzeProfanity = movieDetailsState.canAnalyzeProfanity,
                    currentProfile = activeProfile,
                    isPremium = isPremium,
                    isDemoMode = com.purestream.data.demo.DemoData.isDemoToken(sharedPlexAuthRepository.getAuthToken())
                )
                
                // Show video error if any
                movieDetailsState.videoError?.let { error ->
                    LaunchedEffect(error) {
                        android.util.Log.e("MainActivity", "Video URL error: $error")
                        // Error handling via MovieDetailsScreen UI - dialog not currently implemented
                    }
                }
                
                // Show loading indicator for video URL generation
                if (movieDetailsState.isLoadingVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFF5B800)
                            )
                            Text(
                                text = "Getting video URL...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            } else {
                // Show loading or error state while movie data loads
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (movieDetailsState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFF5B800)
                            )
                            Text(
                                text = "Loading movie details...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    } else if (movieDetailsState.error != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Error loading movie",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = movieDetailsState.error!!,
                                color = Color(0xFFB3B3B3),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { 
                                    movieDetailsViewModel.loadMovieDetails(movieId)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5B800),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    } else {
                        // Movie not found, show fallback
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Movie not found",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This movie may not be available.",
                                color = Color(0xFFB3B3B3),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { navController.popBackStack() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5B800),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }
        
        // TV Show Details Screen  
        composable(
            route = Destinations.TV_SHOW_DETAILS,
            arguments = listOf(navArgument("showId") { type = NavType.StringType })
        ) { backStackEntry ->
            val showId = backStackEntry.arguments?.getString("showId") ?: return@composable
            val context = LocalContext.current
            
            // Use shared ViewModels
            val tvShowsCacheViewModel = sharedTvShowsViewModel
            val tvShowDetailsViewModel = sharedTvShowDetailsViewModel
            val tvShowDetailsState by tvShowDetailsViewModel.uiState.collectAsStateWithLifecycle()
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val activeProfile: com.purestream.data.model.Profile? = homeState.currentProfile

            // Pre-populate with TV show from cache for instant loading
            LaunchedEffect(showId) {
                val showFromCache = tvShowsCacheViewModel.uiState.value.items.find { show -> show.ratingKey == showId }
                if (showFromCache != null && tvShowDetailsViewModel.uiState.value.tvShow == null) {
                    android.util.Log.d("MainActivity", "Pre-populating TV show details from cache for: ${showFromCache.title}")
                    tvShowDetailsViewModel.setTvShow(showFromCache)
                }
            }

            // Initialize progress tracking FIRST before loading any content
            LaunchedEffect(activeProfile?.id) {
                activeProfile?.let { profile ->
                    tvShowDetailsViewModel.initializeProgressTracking(context, profile.id)
                }
            }

            // Setup Plex connection and load TV show details
            LaunchedEffect(Unit) {
                try {
                    val authRepository = com.purestream.data.repository.PlexAuthRepository(context)
                    val authToken = authRepository.getAuthToken()

                    if (authToken != null) {
                        tvShowDetailsViewModel.setPlexConnectionWithAuth(authToken)
                        tvShowDetailsViewModel.loadTvShowDetails(showId)
                    } else {
                        tvShowDetailsViewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                        tvShowDetailsViewModel.loadTvShowDetails(showId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error setting up Plex connection for TV show details", e)
                    tvShowDetailsViewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                    tvShowDetailsViewModel.loadTvShowDetails(showId)
                }
            }

            // Get the TV show from the details view model
            val tvShow = tvShowDetailsState.tvShow

            if (tvShow != null) {

                TvShowDetailsScreen(
                    tvShow = tvShow,
                    seasons = tvShowDetailsState.seasons,
                    selectedSeason = tvShowDetailsState.selectedSeason,
                    episodes = tvShowDetailsState.episodes,
                    isLoadingSeasons = tvShowDetailsState.isLoadingSeasons,
                    isLoadingEpisodes = tvShowDetailsState.isLoadingEpisodes,
                    seasonsError = tvShowDetailsState.seasonsError,
                    episodesError = tvShowDetailsState.episodesError,
                    episodeProgressMap = tvShowDetailsState.episodeProgressMap,  // Pass progress data
                    onSeasonSelect = { season ->
                        tvShowDetailsViewModel.selectSeason(season)
                    },
                    onEpisodeClick = { episode ->
                        // Pre-populate the shared details VM with the episode object for instant next screen
                        tvShowDetailsViewModel.setEpisode(episode)
                        navController.navigate(Destinations.episodeDetails(episode.ratingKey))
                    },
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onRetrySeasons = {
                        tvShow?.let { show ->
                            tvShowDetailsViewModel.loadSeasons(show.ratingKey)
                        }
                    },
                    onRetryEpisodes = {
                        tvShowDetailsState.selectedSeason?.let { season ->
                            tvShowDetailsViewModel.loadEpisodes(season.ratingKey)
                        }
                    },
                    currentProfile = activeProfile
                )
            } else {
                // Show loading or error state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (tvShowDetailsState.isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFF5B800)
                            )
                            Text(
                                text = "Loading TV show details...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    } else if (tvShowDetailsState.error != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Error loading TV show",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = tvShowDetailsState.error!!,
                                color = Color(0xFFB3B3B3),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { 
                                    tvShowDetailsViewModel.loadTvShowDetails(showId)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5B800),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    } else {
                        // TV show not found, show fallback
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "TV show not found",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This show may not be available.",
                                color = Color(0xFFB3B3B3),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { navController.popBackStack() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF5B800),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }
        
        // Media Player Screen (Instant navigation)
        composable(
            route = Destinations.MEDIA_PLAYER,
            arguments = listOf(
                navArgument("contentId") { type = NavType.StringType },
                navArgument("contentType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val contentId = backStackEntry.arguments?.getString("contentId") ?: "unknown"
            val contentType = backStackEntry.arguments?.getString("contentType") ?: "movie"
            val coroutineScope = rememberCoroutineScope()
            
            val isDemoMode = com.purestream.data.demo.DemoData.isDemoToken(sharedPlexAuthRepository.getAuthToken())
            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val settingsState by sharedSettingsViewModel.uiState.collectAsStateWithLifecycle()

            // Initialize word tracking and setup connection
            LaunchedEffect(homeState.currentProfile?.id) {
                homeState.currentProfile?.let { profile ->
                    sharedMediaPlayerViewModel.setProfileForWordTracking(profile.id)
                    if (!isDemoMode) {
                        sharedPlexAuthRepository.getAuthToken()?.let { token ->
                            sharedMediaPlayerViewModel.setPlexConnectionWithAuth(token)
                        }
                    } else {
                        sharedMediaPlayerViewModel.setDemoSubtitleAnalysis(com.purestream.data.demo.DemoData.DEMO_MUTING_TIMESTAMPS)
                    }
                }
            }

            MediaPlayerScreen(
                videoUrl = "", // Explicitly empty to trigger loadVideoUrl in MediaPlayerScreen
                title = sharedMediaPlayerViewModel.getStoredMovie()?.title 
                        ?: sharedMediaPlayerViewModel.getStoredEpisode()?.title ?: "Loading Media...",
                currentFilterLevel = homeState.currentProfile?.profanityFilterLevel ?: com.purestream.data.model.ProfanityFilterLevel.MILD,
                isPremium = settingsState.appSettings.isPremium,
                currentProfile = homeState.currentProfile,
                contentId = contentId,
                mediaPlayerViewModel = sharedMediaPlayerViewModel,
                onFilterLevelChange = { newLevel ->
                    homeState.currentProfile?.let { profile ->
                        val updatedProfile = profile.copy(profanityFilterLevel = newLevel)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                        }
                        
                        val profileRepository = com.purestream.data.repository.ProfileRepository(context)
                        coroutineScope.launch {
                            try {
                                profileRepository.updateProfile(updatedProfile)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update profile: ${e.message}")
                            }
                        }
                    }
                },
                onSubtitleAlignmentChange = { /* Handle alignment */ },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // Media Player Screen with URL
        composable(
            route = Destinations.MEDIA_PLAYER_WITH_URL,
            arguments = listOf(
                navArgument("videoUrl") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("contentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoUrl = backStackEntry.arguments?.getString("videoUrl") ?: return@composable
            val title = backStackEntry.arguments?.getString("title") ?: "Unknown Movie"
            val contentId = backStackEntry.arguments?.getString("contentId") ?: "unknown"
            val coroutineScope = rememberCoroutineScope()
            
            // Check if we're in demo mode
            val isDemoMode = com.purestream.data.demo.DemoData.isDemoToken(sharedPlexAuthRepository.getAuthToken())

            // Decode the URL (PlexRepository handles demo video file preparation)
            val decodedUrl = Uri.decode(videoUrl)
            val decodedTitle = Uri.decode(title)

            android.util.Log.d("MainActivity", "MediaPlayer - Received URL: $videoUrl")
            android.util.Log.d("MainActivity", "MediaPlayer - Decoded URL: $decodedUrl")
            android.util.Log.d("MainActivity", "MediaPlayer - Is Demo Mode: $isDemoMode")
            android.util.Log.d("MainActivity", "MediaPlayer - Title: $decodedTitle")
            android.util.Log.d("MainActivity", "MediaPlayer - Content ID: $contentId")

            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val settingsState by sharedSettingsViewModel.uiState.collectAsStateWithLifecycle()

            // Set up Plex connection for MediaPlayerViewModel (skip in demo mode)
            LaunchedEffect(Unit) {
                if (!isDemoMode) {
                    try {
                        val authRepository = com.purestream.data.repository.PlexAuthRepository(context)
                        val authToken = authRepository.getAuthToken()

                        if (authToken != null) {
                            sharedMediaPlayerViewModel.setPlexConnectionWithAuth(authToken)
                            android.util.Log.d("MainActivity", "MediaPlayer Plex connection initialized with auth token")
                        } else {
                            android.util.Log.w("MainActivity", "No auth token available for MediaPlayer")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error setting up Plex connection for MediaPlayer", e)
                    }
                } else {
                    // In demo mode, set the demo muting timestamps
                    sharedMediaPlayerViewModel.setDemoSubtitleAnalysis(com.purestream.data.demo.DemoData.DEMO_MUTING_TIMESTAMPS)
                    android.util.Log.d("MainActivity", "MediaPlayer - Demo mode: Using demo video with profanity muting")
                }
            }

            // Initialize word tracking for level-up system
            LaunchedEffect(homeState.currentProfile?.id) {
                homeState.currentProfile?.let { profile ->
                    sharedMediaPlayerViewModel.setProfileForWordTracking(profile.id)
                    android.util.Log.d("MainActivity", "MediaPlayer - Initialized word tracking for profile: ${profile.name} (${profile.id})")
                }
            }

            MediaPlayerScreen(
                videoUrl = decodedUrl,
                title = decodedTitle,
                currentFilterLevel = homeState.currentProfile?.profanityFilterLevel ?: com.purestream.data.model.ProfanityFilterLevel.MILD,
                isPremium = settingsState.appSettings.isPremium,
                currentProfile = homeState.currentProfile,
                contentId = contentId,
                mediaPlayerViewModel = sharedMediaPlayerViewModel,
                onFilterLevelChange = { newLevel ->
                    homeState.currentProfile?.let { profile ->
                        val updatedProfile = profile.copy(profanityFilterLevel = newLevel)
                        sharedHomeViewModel.setCurrentProfile(updatedProfile)
                        
                        val profileManager = ProfileManager.getInstance(context)
                        coroutineScope.launch {
                            profileManager.setCurrentProfile(updatedProfile)
                        }
                        
                        val profileRepository = com.purestream.data.repository.ProfileRepository(context)
                        coroutineScope.launch {
                            try {
                                profileRepository.updateProfile(updatedProfile)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to update profile: ${e.message}")
                            }
                        }
                    }
                },
                onSubtitleAlignmentChange = { /* Handle subtitle alignment change */ },
                onBackClick = {
                    // Clear hero movie state if returning to home screen
                    val previousDestination = navController.previousBackStackEntry?.destination?.route
                    if (previousDestination == Destinations.HOME) {
                        android.util.Log.d("MainActivity", "Returning to home from MediaPlayer - clearing hero movie state immediately")
                        sharedHeroMovieDetailsViewModel.clearVideoUrl()
                        sharedHeroMovieDetailsViewModel.clearMovieDetails()
                    }
                    navController.popBackStack()
                }
            )
        }
        
        // Episode Details Screen
        composable(
            route = Destinations.EPISODE_DETAILS,
            arguments = listOf(navArgument("episodeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: return@composable
            val context = LocalContext.current
            
            // Use shared details VM
            val tvShowDetailsViewModel: com.purestream.ui.viewmodel.TvShowDetailsViewModel = sharedTvShowDetailsViewModel
            val tvShowDetailsState by tvShowDetailsViewModel.uiState.collectAsStateWithLifecycle()

            // Get premium status directly from PremiumStatusManager (source of truth)
            val premiumStatusManager = remember { com.purestream.data.manager.PremiumStatusManager.getInstance(context) }
            val premiumStatusState by premiumStatusManager.premiumStatus.collectAsStateWithLifecycle()
            val isPremium = premiumStatusState is com.purestream.data.manager.PremiumStatusState.Premium

            val homeState by sharedHomeViewModel.uiState.collectAsStateWithLifecycle()
            val activeProfile: com.purestream.data.model.Profile? = homeState.currentProfile

            // Initialize progress tracking FIRST before loading any content
            LaunchedEffect(activeProfile?.id) {
                activeProfile?.let { profile ->
                    tvShowDetailsViewModel.initializeProgressTracking(context, profile.id)
                }
            }

            // Setup Plex connection and load episode - only if not already pre-populated
            LaunchedEffect(Unit) {
                try {
                    val authRepository = com.purestream.data.repository.PlexAuthRepository(context)
                    val authToken = authRepository.getAuthToken()

                    if (authToken != null) {
                        tvShowDetailsViewModel.setPlexConnectionWithAuth(authToken)
                    } else {
                        tvShowDetailsViewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                    }

                    // Check if we need to load or if already there
                    if (tvShowDetailsViewModel.uiState.value.currentEpisode?.ratingKey != episodeId) {
                        tvShowDetailsViewModel.loadEpisodeById(episodeId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error setting up Plex connection for episode details", e)
                    tvShowDetailsViewModel.loadEpisodeById(episodeId)
                }
            }

            // Refresh progress when returning to this screen
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        android.util.Log.d("MainActivity", "EpisodeDetails resumed - refreshing progress")
                        tvShowDetailsViewModel.refreshProgress()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Use currentEpisode from state instead of searching episodes list
            val episode = tvShowDetailsState.currentEpisode
            val tvShow = tvShowDetailsState.tvShow

            if (episode != null && tvShow != null) {
                val currentFilterLevel = activeProfile?.profanityFilterLevel
                    ?: com.purestream.data.model.ProfanityFilterLevel.MILD

                // Check if analysis can be performed for current filter level
                LaunchedEffect(episode.ratingKey, currentFilterLevel) {
                    tvShowDetailsViewModel.checkCanAnalyzeProfanity(episode, currentFilterLevel)
                }


                EpisodeDetailsScreen(
                    episode = episode,
                    tvShowTitle = tvShow.title,
                    showBackgroundUrl = tvShow.artUrl,
                    progressPercentage = tvShowDetailsState.episodeProgressMap[episode.ratingKey],  // Pass episode progress
                    progressPosition = tvShowDetailsState.episodeProgressPositionMap[episode.ratingKey],
                    onPlayClick = { startPosition ->
                        // Store media and startPosition before navigation
                        sharedMediaPlayerViewModel.setStartPosition(startPosition)
                        sharedMediaPlayerViewModel.setCurrentMedia(episode = episode, tvShow = tvShow)

                        // Auto-trigger analysis in background if not done
                        if (tvShowDetailsState.canAnalyzeProfanity) {
                            // Use current profile's filter level as priority for faster padlock update
                            val priorityLevel = activeProfile?.profanityFilterLevel ?: com.purestream.data.model.ProfanityFilterLevel.MILD
                            tvShowDetailsViewModel.analyzeEpisodeProfanityAllLevels(episode, priorityLevel)
                        }

                        // Navigate instantly
                        val contentIdForPlayer = "${tvShow.ratingKey}_${episode.ratingKey}"
                        navController.navigate(Destinations.mediaPlayer(contentIdForPlayer, "episode"))
                    },
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onAnalyzeProfanityClick = { episodeToAnalyze ->
                        // Use progressive analysis to analyze all filter levels with current level as priority
                        tvShowDetailsViewModel.analyzeEpisodeProfanityAllLevels(episodeToAnalyze, currentFilterLevel)
                    },
                    isAnalyzingSubtitles = tvShowDetailsState.isAnalyzingSubtitles,
                    subtitleAnalysisResult = tvShowDetailsState.subtitleAnalysisResult,
                    subtitleAnalysisError = tvShowDetailsState.subtitleAnalysisError,
                    onClearAnalysisError = {
                        tvShowDetailsViewModel.clearSubtitleAnalysisError()
                    },
                    canAnalyzeProfanity = tvShowDetailsState.canAnalyzeProfanity,
                    currentProfile = activeProfile,
                    isPremium = isPremium,
                    isDemoMode = com.purestream.data.demo.DemoData.isDemoToken(sharedPlexAuthRepository.getAuthToken())
                )
                
                // Show video error if any
                tvShowDetailsState.videoError?.let { error ->
                    LaunchedEffect(error) {
                        android.util.Log.e("MainActivity", "Episode video URL error: $error")
                        // Error handling via TvShowDetailsScreen UI - dialog not currently implemented
                    }
                }
                
                // Show loading indicator for video URL generation
                if (tvShowDetailsState.isLoadingVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFF5B800)
                            )
                            Text(
                                text = "Getting episode video...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            } else {
                // Show loading or error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        tvShowDetailsState.isLoadingCurrentEpisode -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFF5B800)
                                )
                                Text(
                                    text = "Loading episode...",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        tvShowDetailsState.currentEpisodeError != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Error loading episode",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tvShowDetailsState.currentEpisodeError!!,
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { 
                                        tvShowDetailsViewModel.loadEpisodeById(episodeId)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5B800),
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Retry")
                                }
                                Button(
                                    onClick = { navController.popBackStack() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6B7280),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Go Back")
                                }
                            }
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Episode not found",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { navController.popBackStack() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5B800),
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Consolidated helper function for Plex connection setup with optional profile support
private suspend fun setupPlexConnection(
    authRepository: com.purestream.data.repository.PlexAuthRepository,
    viewModel: Any,
    profile: Profile? = null
) {
    try {
        val authToken = authRepository.getAuthToken()
        val selectedLibraries = profile?.selectedLibraries ?: emptyList()
        
        if (authToken != null) {
            when (viewModel) {
                is com.purestream.ui.viewmodel.MoviesViewModel -> {
                    if (profile != null) {
                        viewModel.setPlexConnectionWithAuth(authToken, selectedLibraries)
                    } else {
                        viewModel.setPlexConnectionWithAuth(authToken)
                    }
                }
                is com.purestream.ui.viewmodel.TvShowsViewModel -> {
                    if (profile != null) {
                        viewModel.setPlexConnectionWithAuth(authToken, selectedLibraries)
                    } else {
                        viewModel.setPlexConnectionWithAuth(authToken)
                    }
                }
                is com.purestream.ui.viewmodel.MovieDetailsViewModel -> viewModel.setPlexConnectionWithAuth(authToken)
                is com.purestream.ui.viewmodel.TvShowDetailsViewModel -> viewModel.setPlexConnectionWithAuth(authToken)
            }
        } else {
            android.util.Log.w("MainActivity", "No auth token found - using demo connection")
            when (viewModel) {
                is com.purestream.ui.viewmodel.MoviesViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                is com.purestream.ui.viewmodel.TvShowsViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                is com.purestream.ui.viewmodel.MovieDetailsViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
                is com.purestream.ui.viewmodel.TvShowDetailsViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error setting up Plex connection", e)
        when (viewModel) {
            is com.purestream.ui.viewmodel.MoviesViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
            is com.purestream.ui.viewmodel.TvShowsViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
            is com.purestream.ui.viewmodel.MovieDetailsViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
            is com.purestream.ui.viewmodel.TvShowDetailsViewModel -> viewModel.setPlexConnection("http://demo.server:32400", "demo_token")
        }
    }
}

