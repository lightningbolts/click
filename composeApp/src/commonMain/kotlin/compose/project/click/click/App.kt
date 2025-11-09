package compose.project.click.click

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import compose.project.click.click.data.storage.createTokenStorage
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    // Initialize from system theme so dark mode applies on main page immediately
    val systemDark = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(systemDark) }

    // Auth ViewModel with TokenStorage
    val tokenStorage = remember { createTokenStorage() }
    val authViewModel: AuthViewModel = viewModel { AuthViewModel(tokenStorage = tokenStorage) }
    var showSignUp by remember { mutableStateOf(false) }
    var skipLogin by remember { mutableStateOf(false) }  // For development/testing

    val scheme = if (isDarkMode) {
        darkColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            primaryContainer = SoftBlue,
            onPrimaryContainer = DeepBlue
        )
    } else {
        lightColorScheme(
            primary = PrimaryBlue,
            secondary = AccentBlue,
            background = BackgroundLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            primaryContainer = SoftBlue,
            onPrimaryContainer = DeepBlue
        )
    }

    MaterialTheme(colorScheme = scheme) {
        // Show login/signup screens when not authenticated and not skipped
        if (!authViewModel.isAuthenticated && !skipLogin) {
            if (showSignUp) {
                SignUpScreen(
                    onSignUpSuccess = {
                        // Success is handled by state change in viewModel
                    },
                    onLoginClick = {
                        showSignUp = false
                        authViewModel.resetAuthState()
                    },
                    onGoogleSignUp = {
                        // TODO: Implement platform-specific Google Sign-In to get token
                        // For now, this is a placeholder
                        // authViewModel.signInWithGoogle(googleToken)
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
                    onGoogleSignIn = {
                        // TODO: Implement platform-specific Google Sign-In to get token
                        // For now, this is a placeholder
                        // authViewModel.signInWithGoogle(googleToken)
                    },
                    onEmailSignIn = { email, password ->
                        authViewModel.signInWithEmail(email, password)
                    },
                    onSkipLogin = {
                        skipLogin = true  // Skip authentication for development
                    },
                    isLoading = authViewModel.authState is AuthState.Loading,
                    errorMessage = if (authViewModel.authState is AuthState.Error) {
                        (authViewModel.authState as AuthState.Error).message
                    } else null
                )
            }
        } else {
            // Main app content when authenticated
            var currentRoute by remember { mutableStateOf("home") }
            var isSearchOpen by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }

            val focusManager = LocalFocusManager.current
            val focusRequester = remember { FocusRequester() }

            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 8.dp
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
                        when (currentRoute) {
                            NavigationItem.Home.route -> HomeScreen()
                            NavigationItem.AddClick.route -> AddClickScreen()
                            NavigationItem.Connections.route -> {
                                // Get userId from AuthState - use a placeholder for now
                                val userId = when (val state = authViewModel.authState) {
                                    is AuthState.Success -> state.userId
                                    else -> ""
                                }
                                if (userId.isNotEmpty()) {
                                    ConnectionsScreen(userId = userId)
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
                            NavigationItem.Map.route -> MapScreen()
                            NavigationItem.Settings.route -> SettingsScreen(
                                isDarkMode = isDarkMode,
                                onToggleDarkMode = { isDarkMode = !isDarkMode },
                                onSignOut = { authViewModel.signOut() }
                            )
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
                            tonalElevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            TextField(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surface,
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
    }
}