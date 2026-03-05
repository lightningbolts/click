package compose.project.click.click

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import compose.project.click.click.navigation.NavigationItem
import compose.project.click.click.navigation.bottomNavItems
import compose.project.click.click.ui.screens.*
import compose.project.click.click.ui.theme.*
import compose.project.click.click.viewmodel.AuthViewModel
import compose.project.click.click.viewmodel.AuthState
import compose.project.click.click.viewmodel.ChatViewModel
import compose.project.click.click.viewmodel.MapViewModel
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.nfc.rememberNfcManager
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.WindowInsets
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch

import compose.project.click.click.viewmodel.ConnectionViewModel
import compose.project.click.click.viewmodel.ConnectionState
import compose.project.click.click.data.models.User
import compose.project.click.click.data.AppDataManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    // Default to dark until persisted preference is loaded.
    var isDarkMode by remember { mutableStateOf(true) }

    // Ktor client
    val client = remember {
        HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    // Auth ViewModel with TokenStorage
    val tokenStorage = remember { createTokenStorage() }
    val appScope = rememberCoroutineScope()
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(tokenStorage = tokenStorage) }
    val connectionViewModel: ConnectionViewModel = viewModel { ConnectionViewModel() }

    // Location service for capturing GPS during QR scans
    val locationService = remember { compose.project.click.click.utils.LocationService() }

    val currentUser = when (val state = authViewModel.authState) {
        is AuthState.Success -> User(id = state.userId, name = state.name ?: state.email, createdAt = 0L)
        else -> User(id = "", name = "", createdAt = 0L)
    }

    LaunchedEffect(Unit) {
        val persisted = tokenStorage.getDarkModeEnabled()
        if (persisted != null) {
            isDarkMode = persisted
        }
    }

    // Coroutine scope for location-aware connection
    val connectionScope = rememberCoroutineScope()

    fun connectWithUser(userId: String, tokenAgeMs: Long? = null) {
        if (currentUser.id.isNotEmpty()) {
            connectionScope.launch {
                // Attempt to capture location for proximity verification + semantic location
                val location = try {
                    locationService.getCurrentLocation()
                } catch (e: Exception) {
                    println("App: Failed to get location: ${e.message}")
                    null
                }
                connectionViewModel.connectWithUser(
                    scannedUserId = userId,
                    currentUserId = currentUser.id,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    connectionMethod = "qr",
                    tokenAgeMs = tokenAgeMs
                )
            }
        }
    }

    // Navigation state
    var showMyQRCode by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }



    var showSignUp by remember { mutableStateOf(false) }

    val scheme = if (isDarkMode) {
        darkColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            primaryContainer = DeepBlue,
            onPrimaryContainer = NeonPurple,
            surfaceVariant = Color(0xFF2C2C2C)
        )
    } else {
        lightColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            primaryContainer = SoftBlue,
            onPrimaryContainer = DeepBlue,
            surfaceVariant = Color(0xFFE0E0E0)
        )
    }

    MaterialTheme(colorScheme = scheme) {
        // Global background with subtle purple gradient (only in dark mode)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkMode) BackgroundDark else BackgroundLight)
                .let { modifier ->
                    if (isDarkMode) {
                        modifier.background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    PrimaryBlue.copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(0f, 0f),
                                radius = 1000f
                            )
                        )
                    } else modifier
                }
        ) {
        // Show login/signup screens when not authenticated
        if (authViewModel.authState is AuthState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else if (!authViewModel.isAuthenticated) {
            if (showSignUp) {
                SignUpScreen(
                    onSignUpSuccess = {
                        // Success is handled by state change in viewModel
                    },
                    onLoginClick = {
                        showSignUp = false
                        authViewModel.resetAuthState()
                    },
                    onEmailSignUp = { name, email, password ->
                        authViewModel.signUpWithEmail(name, email, password)
                    },
                    isLoading = authViewModel.authState is AuthState.Loading,
                    errorMessage = if (authViewModel.authState is AuthState.Error) {
                        (authViewModel.authState as AuthState.Error).message
                    } else null
                )
            } else {
                LoginScreen(
                    onLoginSuccess = {
                        // Success is handled by state change in viewModel
                    },
                    onSignUpClick = {
                        showSignUp = true
                        authViewModel.resetAuthState()
                    },
                    onEmailSignIn = { email, password ->
                        authViewModel.signInWithEmail(email, password)
                    },
                    isLoading = authViewModel.authState is AuthState.Loading,
                    errorMessage = if (authViewModel.authState is AuthState.Error) {
                        (authViewModel.authState as AuthState.Error).message
                    } else null
                )
            }
        } else {
            // Main app content when authenticated
            // Initialize app data once when authenticated
            LaunchedEffect(Unit) {
                AppDataManager.initializeData()
            }

            // --- Interest tagging onboarding gate ---
            // Uses local cache (TokenStorage) + DB column for persistent state.
            // Local cache prevents re-showing the tagging screen on app resume / process recreation.
            val supabaseRepo = remember { compose.project.click.click.data.repository.SupabaseRepository() }
            var needsTagging by remember { mutableStateOf<Boolean?>(null) }
            // Hoist scope outside the conditional so it's always called at the same composable depth
            val onboardingScope = rememberCoroutineScope()

            LaunchedEffect(currentUser.id) {
                if (currentUser.id.isNotEmpty()) {
                    // 1. Check local cache first — avoids network call on app resume
                    val localCached = tokenStorage.getTagsInitialized()
                    if (localCached == true) {
                        needsTagging = false
                        return@LaunchedEffect
                    }

                    // 2. Fall back to network check
                    val initialized = supabaseRepo.fetchTagsInitialized(currentUser.id)
                    if (initialized != null) {
                        // Got a definitive answer from the server
                        if (initialized) tokenStorage.saveTagsInitialized(true)
                        needsTagging = !initialized
                    } else {
                        // Network error — keep null (show spinner), don't show tags screen
                        // The user can retry by reopening the app
                        needsTagging = null
                    }
                }
            }

            // Show tagging screen if the user hasn't completed or skipped onboarding
            if (needsTagging == true) {
                InterestTaggingScreen(
                    onTagsSelected = { tags ->
                        onboardingScope.launch {
                            supabaseRepo.updateUserTags(currentUser.id, tags)
                            supabaseRepo.setTagsInitialized(currentUser.id)
                            tokenStorage.saveTagsInitialized(true)
                            needsTagging = false
                        }
                    },
                    onSkip = {
                        // Immediately hide the screen; persist the skip in the background
                        needsTagging = false
                        onboardingScope.launch {
                            supabaseRepo.setTagsInitialized(currentUser.id)
                            tokenStorage.saveTagsInitialized(true)
                        }
                    },
                    canSkip = true
                )
            } else if (needsTagging == null) {
                // Still loading tag check
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
            // --- End onboarding gate ---
            
            var currentRoute by remember { mutableStateOf("home") }
            var previousRoute by remember { mutableStateOf("home") }
            // Route history stack for back navigation
            val routeHistory = remember { mutableStateListOf("home") }
            var showNfcScreen by remember { mutableStateOf(false) }
            var pendingChatId by remember { mutableStateOf<String?>(null) }

            // Helper: navigate to a route, pushing onto history stack
            fun navigateTo(route: String) {
                if (route != currentRoute) {
                    previousRoute = currentRoute
                    routeHistory.add(currentRoute)
                    currentRoute = route
                }
            }

            // Helper: go back to previous route
            fun navigateBack(): Boolean {
                if (routeHistory.size > 1) {
                    routeHistory.removeLastOrNull()
                    val target = routeHistory.lastOrNull() ?: "home"
                    previousRoute = currentRoute
                    currentRoute = target
                    return true
                }
                return false
            }

            val focusManager = LocalFocusManager.current
            val mapViewModel: MapViewModel = viewModel { MapViewModel() }
            val chatViewModel: ChatViewModel = viewModel { ChatViewModel() }

            LaunchedEffect(currentUser.id) {
                if (currentUser.id.isNotEmpty()) {
                    chatViewModel.setCurrentUser(currentUser.id)
                }
            }

            // Snackbar for connection success/error feedback
            val snackbarHostState = remember { SnackbarHostState() }
            val connectionState by connectionViewModel.connectionState.collectAsState()
            LaunchedEffect(connectionState) {
                when (val state = connectionState) {
                    is ConnectionState.Success ->  {
                        snackbarHostState.showSnackbar("Connected with ${state.connectedUser.name ?: "user"}!")
                        connectionViewModel.resetConnectionState()
                    }
                    is ConnectionState.Error -> {
                        snackbarHostState.showSnackbar(state.message)
                        connectionViewModel.resetConnectionState()
                    }
                    else -> {}
                }
            }

            // Platform back handler — intercepts Android back gesture/button
            compose.project.click.click.ui.components.PlatformBackHandler(
                enabled = showMyQRCode || showQRScanner || showNfcScreen || currentRoute != "home"
            ) {
                when {
                    showMyQRCode -> showMyQRCode = false
                    showQRScanner -> showQRScanner = false
                    showNfcScreen -> showNfcScreen = false
                    pendingChatId != null -> pendingChatId = null // close open chat first
                    else -> navigateBack()
                }
            }

            // Wrap Scaffold in a Box to allow search overlay to be positioned at true screen bottom
            Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    PrimaryBlue.copy(alpha = if (isDarkMode) 0.5f else 0.25f),
                                    Color.Transparent
                                )
                            ),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        ),
                        containerColor = if (isDarkMode) {
                            GlassDark
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        },
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                                bottomNavItems.forEach { item ->
                                NavigationBarItem(
                                    icon = { Icon(item.icon, contentDescription = item.title) },
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        navigateTo(item.route)
                                        // Reset overlay screens so we can navigate away
                                        showMyQRCode = false
                                        showQRScanner = false
                                        showNfcScreen = false
                                        focusManager.clearFocus()
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    alwaysShowLabel = false
                                )
                            }

                            // Search button at the end
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                                selected = false,
                                onClick = { navigateTo("search") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                alwaysShowLabel = false
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val screenKey = when {
                            showMyQRCode -> "my_qr"
                            showQRScanner -> "qr_scanner"
                            showNfcScreen -> "nfc"
                            currentRoute == "search" -> "search"
                            else -> currentRoute
                        }

                        AnimatedContent(
                            targetState = screenKey,
                            transitionSpec = {
                                val routeOrder = listOf(
                                    NavigationItem.Home.route,
                                    NavigationItem.AddClick.route,
                                    NavigationItem.Connections.route,
                                    NavigationItem.Map.route,
                                    NavigationItem.Settings.route,
                                    "search",
                                    "my_qr",
                                    "qr_scanner",
                                    "nfc"
                                )

                                val initialIndex = routeOrder.indexOf(initialState).let { if (it >= 0) it else 0 }
                                val targetIndex = routeOrder.indexOf(targetState).let { if (it >= 0) it else 0 }
                                val movingForward = targetIndex >= initialIndex

                                val slideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
                                val fadeSpec  = tween<Float>(220, easing = LinearOutSlowInEasing)

                                if (movingForward) {
                                    (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { it }) +
                                        fadeIn(animationSpec = fadeSpec))
                                        .togetherWith(
                                            slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { -it }) +
                                                fadeOut(animationSpec = fadeSpec)
                                        ).using(SizeTransform(clip = true))
                                } else {
                                    (slideInHorizontally(animationSpec = slideSpec, initialOffsetX = { -it }) +
                                        fadeIn(animationSpec = fadeSpec))
                                        .togetherWith(
                                            slideOutHorizontally(animationSpec = slideSpec, targetOffsetX = { it }) +
                                                fadeOut(animationSpec = fadeSpec)
                                        ).using(SizeTransform(clip = true))
                                }
                            },
                            label = "app_screen_transition"
                        ) { animatedScreen ->
                            when (animatedScreen) {
                                "my_qr" -> {
                                    MyQRCodeScreen(
                                        userId = currentUser.id,
                                        username = currentUser.name,
                                        onNavigateBack = { showMyQRCode = false }
                                    )
                                }
                                "qr_scanner" -> {
                                    QRScannerScreen(
                                        onQRCodeScanned = { userId ->
                                            showQRScanner = false
                                            if (userId.isNotEmpty() && currentUser.id.isNotEmpty()) {
                                                connectWithUser(userId)
                                                navigateTo(NavigationItem.Connections.route)
                                            }
                                        },
                                        onQRCodeScannedWithToken = { userId, tokenAgeMs ->
                                            showQRScanner = false
                                            if (userId.isNotEmpty() && currentUser.id.isNotEmpty()) {
                                                connectWithUser(userId, tokenAgeMs)
                                                navigateTo(NavigationItem.Connections.route)
                                            }
                                        },
                                        onNavigateBack = { showQRScanner = false }
                                    )
                                }
                                "nfc" -> {
                                    val userId = when (val state = authViewModel.authState) {
                                        is AuthState.Success -> state.userId
                                        else -> ""
                                    }
                                    val authToken by produceState(initialValue = "") {
                                        value = tokenStorage.getJwt() ?: ""
                                    }

                                    val nfcManager = rememberNfcManager()

                                    NfcScreen(
                                        userId = userId,
                                        authToken = authToken,
                                        nfcManager = nfcManager,
                                        onConnectionCreated = {
                                            showNfcScreen = false
                                            navigateTo(NavigationItem.Connections.route)
                                        },
                                        onBackPressed = {
                                            showNfcScreen = false
                                        }
                                    )
                                }
                                "search" -> {
                                    val userId = when (val state = authViewModel.authState) {
                                        is AuthState.Success -> state.userId
                                        else -> ""
                                    }
                                    GlobalSearchScreen(
                                        userId = userId,
                                        onNavigateToChat = { connectionId ->
                                            pendingChatId = connectionId
                                            navigateTo(NavigationItem.Connections.route)
                                        },
                                        onNavigateToMap = {
                                            navigateTo(NavigationItem.Map.route)
                                        },
                                        onBack = { navigateBack() }
                                    )
                                }
                                else -> {
                                    when (animatedScreen) {
                                        NavigationItem.Home.route -> HomeScreen(
                                            onNavigateToChat = { connectionId ->
                                                pendingChatId = connectionId
                                                navigateTo(NavigationItem.Connections.route)
                                            }
                                        )
                                        NavigationItem.AddClick.route -> AddClickScreen(
                                            currentUserId = currentUser.id,
                                            currentUsername = currentUser.name,
                                            onNavigateToNfc = { showNfcScreen = true },
                                            onShowMyQRCode = { showMyQRCode = true },
                                            onScanQRCode = { showQRScanner = true },
                                            onStartChatting = { navigateTo(NavigationItem.Connections.route) }
                                        )
                                        NavigationItem.Connections.route -> {
                                            val userId = when (val state = authViewModel.authState) {
                                                is AuthState.Success -> state.userId
                                                else -> ""
                                            }
                                            if (userId.isNotEmpty()) {
                                                ConnectionsScreen(
                                                    userId = userId,
                                                    searchQuery = "",
                                                    initialChatId = pendingChatId,
                                                    onChatDismissed = { pendingChatId = null },
                                                    viewModel = chatViewModel
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("Please log in to view connections")
                                                }
                                            }
                                        }
                                        NavigationItem.Map.route -> MapScreen(
                                            viewModel = mapViewModel,
                                            onNavigateToChat = { connectionId ->
                                                pendingChatId = connectionId
                                                navigateTo(NavigationItem.Connections.route)
                                            }
                                        )
                                        NavigationItem.Settings.route -> SettingsScreen(
                                            isDarkMode = isDarkMode,
                                            onToggleDarkMode = {
                                                val next = !isDarkMode
                                                isDarkMode = next
                                                appScope.launch {
                                                    tokenStorage.saveDarkModeEnabled(next)
                                                }
                                            },
                                            onSignOut = { authViewModel.signOut() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // End of Scaffold wrapper Box

            } // End of onboarding gate
        }
        } // End of Global Background Box
    }
}