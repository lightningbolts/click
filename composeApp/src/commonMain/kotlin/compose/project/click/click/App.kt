package compose.project.click.click

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.WindowInsets
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

import compose.project.click.click.viewmodel.ConnectionViewModel
import compose.project.click.click.data.models.User
import compose.project.click.click.qr.toQrPayloadOrNull
import compose.project.click.click.data.AppDataManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    // Initialize from system theme but default to dark for cyber aesthetic
    val systemDark = isSystemInDarkTheme()
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
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(tokenStorage = tokenStorage) }
    val connectionViewModel: ConnectionViewModel = viewModel { ConnectionViewModel() }

    val currentUser = when (val state = authViewModel.authState) {
        is AuthState.Success -> User(id = state.userId, name = state.name ?: state.email, createdAt = 0L)
        else -> User(id = "", name = "", createdAt = 0L)
    }

    fun connectWithUser(userId: String) {
        if (currentUser.id.isNotEmpty()) {
            connectionViewModel.connectWithUser(
                scannedUserId = userId,
                currentUserId = currentUser.id
            )
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
            
            var currentRoute by remember { mutableStateOf("home") }
            var showNfcScreen by remember { mutableStateOf(false) }
            var isSearchOpen by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }

            val focusManager = LocalFocusManager.current
            val mapViewModel: MapViewModel = viewModel { MapViewModel() }
            val chatViewModel: ChatViewModel = viewModel { ChatViewModel() }
            val focusRequester = remember { FocusRequester() }

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(PrimaryBlue.copy(alpha = 0.5f), Color.Transparent)
                            ),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        ),
                        containerColor = GlassDark,
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
                                        currentRoute = item.route
                                        isSearchOpen = false
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
                                onClick = { isSearchOpen = true },
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
                        when {
                            showMyQRCode -> {
                                MyQRCodeScreen(
                                    userId = currentUser.id,
                                    username = currentUser.name,
                                    onNavigateBack = { showMyQRCode = false }
                                )
                            }
                            showQRScanner -> {
                                QRScannerScreen(
                                    onQRCodeScanned = { qrData ->
                                        showQRScanner = false
                                        try {
                                            val payload = qrData.toQrPayloadOrNull()
                                            if (payload != null) {
                                                if (currentUser.id.isNotEmpty()) {
                                                    connectWithUser(payload.userId)
                                                    // Navigate to connections to see the result
                                                    currentRoute = NavigationItem.Connections.route
                                                } else {
                                                    // User not logged in
                                                    println("QR Scan: User not logged in, cannot connect")
                                                }
                                            } else {
                                                // Invalid QR code format
                                                println("QR Scan: Invalid QR code format - $qrData")
                                            }
                                        } catch (e: Exception) {
                                            println("QR Scan: Error processing QR code - ${e.message}")
                                        }
                                    },
                                    onNavigateBack = { showQRScanner = false }
                                )
                            }
                            showNfcScreen -> {
                                // Get userId from AuthState and authToken from TokenStorage
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
                                    onConnectionCreated = { connectionId ->
                                        // Navigate to connections and show the new connection
                                        showNfcScreen = false
                                        currentRoute = NavigationItem.Connections.route
                                    },
                                    onBackPressed = {
                                        showNfcScreen = false
                                    }
                                )
                            }
                            else -> {
                                when (currentRoute) {
                                    NavigationItem.Home.route -> HomeScreen()
                                    NavigationItem.AddClick.route -> AddClickScreen(
                                        currentUserId = currentUser.id,
                                        currentUsername = currentUser.name,
                                        onNavigateToNfc = { showNfcScreen = true },
                                        onShowMyQRCode = { showMyQRCode = true },
                                        onScanQRCode = { showQRScanner = true },
                                        onStartChatting = { currentRoute = NavigationItem.Connections.route }
                                    )
                                    NavigationItem.Connections.route -> {
                                        // Get userId from AuthState - use a placeholder for now
                                        val userId = when (val state = authViewModel.authState) {
                                            is AuthState.Success -> state.userId
                                            else -> ""
                                        }
                                        if (userId.isNotEmpty()) {
                                            ConnectionsScreen(
                                                userId = userId,
                                                searchQuery = searchQuery,
                                                viewModel = chatViewModel
                                            )
                                        } else {
                                            // Show loading or login prompt
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Please log in to view connections")
                                            }
                                        }
                                    }
                                    NavigationItem.Map.route -> MapScreen(viewModel = mapViewModel)
                                    NavigationItem.Settings.route -> SettingsScreen(
                                        isDarkMode = isDarkMode,
                                        onToggleDarkMode = { isDarkMode = !isDarkMode },
                                        onSignOut = { authViewModel.signOut() }
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isSearchOpen,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 12.dp // already above bottom bar due to Scaffold padding
                            )
                            .fillMaxWidth()
                    ) {
                        Surface(
                            modifier = Modifier.border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(PrimaryBlue.copy(alpha = 0.5f), Color.Transparent)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                            tonalElevation = 0.dp,
                            shape = RoundedCornerShape(16.dp),
                            color = GlassDark
                        ) {
                            TextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp) // Fixed height to prevent shrinking
                                    .focusRequester(focusRequester),
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                placeholder = { Text("Search") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        isSearchOpen = false
                                        searchQuery = ""
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        isSearchOpen = false
                                    }
                                )
                            )
                        }
                    }
                }
            }

            LaunchedEffect(isSearchOpen) {
                if (isSearchOpen) {
                    focusRequester.requestFocus()
                }
            }
        }
        } // End of Global Background Box
    }
}