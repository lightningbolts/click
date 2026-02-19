package compose.project.click.click.data

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserAvailability
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.createTokenStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Singleton app state manager that loads data once at app startup.
 * Prevents reloading when navigating between screens.
 */
object AppDataManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val authRepository = AuthRepository()
    private val supabaseRepository = SupabaseRepository()
    private val chatRepository = ChatRepository(tokenStorage = createTokenStorage())
    private val tokenStorage = createTokenStorage() // For local preferences storage
    
    // Current user state
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    // User's connections
    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()
    
    // Connected users info
    private val _connectedUsers = MutableStateFlow<Map<String, User>>(emptyMap())
    val connectedUsers: StateFlow<Map<String, User>> = _connectedUsers.asStateFlow()

    // Current user's availability
    private val _userAvailability = MutableStateFlow<UserAvailability?>(null)
    val userAvailability: StateFlow<UserAvailability?> = _userAvailability.asStateFlow()
    
    // Loading state - start as false, set to true in initializeData
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Data loaded flag - prevents reloading
    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()
    
    // Last refresh time
    private var lastRefreshTime: Long = 0
    private const val REFRESH_COOLDOWN_MS = 30_000 // 30 seconds minimum between refreshes
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Ghost Mode state - privacy toggle to stop sharing location and halt network requests
    private val _ghostModeEnabled = MutableStateFlow(false)
    val ghostModeEnabled: StateFlow<Boolean> = _ghostModeEnabled.asStateFlow()
    
    /**
     * Toggle Ghost Mode on/off.
     * When Ghost Mode is enabled:
     * - Background data refresh is halted
     * - No new location data is sent to the server
     * - Existing cached data remains visible but stale
     * 
     * Ghost mode intentionally resets on app restart for safer privacy defaults.
     */
    fun toggleGhostMode() {
        val newValue = !_ghostModeEnabled.value
        _ghostModeEnabled.value = newValue
        println("AppDataManager: Ghost Mode ${if (newValue) "ENABLED - halting background sync" else "DISABLED - resuming background sync"}")
    }
    
    /**
     * Initialize app data - call this once when the app starts
     */
    fun initializeData() {
        if (_isDataLoaded.value || _isLoading.value) return // Already loaded or loading
        
        scope.launch {
            loadAllData()
        }
    }
    
    /**
     * Load all app data
     */
    private suspend fun loadAllData() {
        _isLoading.value = true
        _error.value = null
        
        try {
            // Get current user from auth
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                println("AppDataManager: No auth user found")
                _isLoading.value = false
                return
            }
            
            println("AppDataManager: Loading data for user ${authUser.id}")
            
            // Extract name from auth metadata (prefer full_name over name, set during signup/update)
            val fullName = authUser.userMetadata?.get("full_name")?.toString()?.removeSurrounding("\"")
            val legacyName = authUser.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
            val authName = fullName ?: legacyName
            println("AppDataManager: Auth metadata - full_name: $fullName, name: $legacyName, using: $authName")
            
            // Fetch user data from database
            var user = supabaseRepository.fetchUserById(authUser.id)
            println("AppDataManager: Fetched user from DB: ${user?.name}")
            
            if (user == null) {
                // Create user in database if not exists
                val newUser = User(
                    id = authUser.id,
                    name = authName ?: authUser.email?.substringBefore('@') ?: "User",
                    email = authUser.email,
                    image = null,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    lastPolled = null,
                    connections = emptyList(),
                    paired_with = emptyList(),
                    connection_today = 0,
                    last_paired = null
                )
                println("AppDataManager: Creating new user in DB: ${newUser.name}")
                supabaseRepository.upsertUser(newUser)
                user = newUser
            } else if (user.name == null && authName != null) {
                // Update user name from auth metadata if DB name is null
                println("AppDataManager: Updating user name from auth metadata: $authName")
                supabaseRepository.updateUserName(user.id, authName)
                user = user.copy(name = authName)
            }
            
            _currentUser.value = user
            println("AppDataManager: Current user set to: ${user.name}")
            
            // Load availability from local storage first for immediate display
            val localFreeThisWeek = tokenStorage.getFreeThisWeek()
            if (localFreeThisWeek != null) {
                // Use local value immediately
                _userAvailability.value = UserAvailability(
                    userId = user.id,
                    isFreeThisWeek = localFreeThisWeek,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                println("AppDataManager: Loaded local availability: isFreeThisWeek=$localFreeThisWeek")
            }
            
            // Fetch availability from Supabase (may update the local value)
            val availability = supabaseRepository.fetchUserAvailability(user.id)
            if (availability != null) {
                _userAvailability.value = availability
                // Sync local storage with server value
                tokenStorage.saveFreeThisWeek(availability.isFreeThisWeek)
                println("AppDataManager: Synced availability from Supabase: isFreeThisWeek=${availability.isFreeThisWeek}")
            } else if (localFreeThisWeek == null) {
                println("AppDataManager: No availability found locally or on server")
            }
            
            // Fetch connections
            val userConnections = supabaseRepository.fetchUserConnections(user.id)
            _connections.value = userConnections
            
            // Fetch connected users info
            val otherUserIds = userConnections.flatMap { it.user_ids }.filter { it != user.id }.distinct()
            if (otherUserIds.isNotEmpty()) {
                val users = supabaseRepository.fetchUsersByIds(otherUserIds)
                _connectedUsers.value = users.associateBy { it.id }
            }
            
            _isDataLoaded.value = true
            lastRefreshTime = Clock.System.now().toEpochMilliseconds()
            
        } catch (e: Exception) {
            println("Error loading app data: ${e.message}")
            e.printStackTrace()
            _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Refresh data - respects cooldown to prevent excessive API calls
     */
    fun refresh(force: Boolean = false) {
        // Block all background refresh when Ghost Mode is active
        if (_ghostModeEnabled.value) {
            println("AppDataManager: Skipping refresh - Ghost Mode is active")
            return
        }
        
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastRefreshTime < REFRESH_COOLDOWN_MS) {
            println("Skipping refresh - cooldown not elapsed")
            return
        }
        
        scope.launch {
            loadAllData()
        }
    }
    
    /**
     * Clear all data (on logout)
     */
    fun clearData() {
        _currentUser.value = null
        _connections.value = emptyList()
        _connectedUsers.value = emptyMap()
        _userAvailability.value = null
        _isDataLoaded.value = false
        _error.value = null
    }
    
    /**
     * Update connections list (after making a new connection)
     */
    fun addConnection(connection: Connection) {
        _connections.value = _connections.value + connection
    }
    
    /**
     * Remove a connection
     */
    fun removeConnection(connectionId: String) {
        _connections.value = _connections.value.filter { it.id != connectionId }
    }
    
    /**
     * Get a connected user by their ID
     */
    fun getConnectedUser(userId: String): User? {
        return _connectedUsers.value[userId]
    }
    
    /**
     * Update user availability
     */
    fun updateUserAvailability(availability: UserAvailability) {
        _userAvailability.value = availability
    }

    /**
     * Toggle free this week status
     * This method:
     * 1. Updates local state for immediate UI feedback
     * 2. Saves to local storage immediately (persists even if app is killed)
     * 3. Syncs with backend in background
     */
    fun toggleFreeThisWeek() {
        val user = _currentUser.value ?: run {
            println("toggleFreeThisWeek: No current user")
            return
        }
        val current = _userAvailability.value
        val newStatus = !(current?.isFreeThisWeek ?: false)
        
        println("toggleFreeThisWeek: Toggling from ${current?.isFreeThisWeek} to $newStatus for user ${user.id}")
        
        val updated = current?.copy(
            isFreeThisWeek = newStatus,
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        ) ?: UserAvailability(
            userId = user.id,
            isFreeThisWeek = newStatus,
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        )
        
        // Update local state first for immediate UI feedback
        _userAvailability.value = updated
        
        // Save to local storage immediately (this is fast and synchronous-ish)
        // This ensures the value persists even if the app is killed before network call completes
        scope.launch(Dispatchers.Default) {
            try {
                tokenStorage.saveFreeThisWeek(newStatus)
                println("toggleFreeThisWeek: Saved to local storage: $newStatus")
            } catch (e: Exception) {
                println("toggleFreeThisWeek: Error saving to local storage: ${e.message}")
            }
        }
        
        // Sync with backend
        scope.launch(Dispatchers.Default) {
            try {
                val result = supabaseRepository.setFreeThisWeek(user.id, newStatus)
                println("toggleFreeThisWeek: Supabase update result: $result")
                // Note: We don't rollback on failure since local storage has the truth
                // Next app launch will attempt to sync again
            } catch (e: Exception) {
                println("toggleFreeThisWeek: Error updating Supabase: ${e.message}")
                e.printStackTrace()
                // Don't rollback - keep local state as truth, will sync later
            }
        }
    }
    
    /**
     * Get a specific connection by ID
     */
    fun getConnection(connectionId: String): Connection? {
        return _connections.value.find { it.id == connectionId }
    }
    
    /**
     * Get the other user in a connection
     */
    fun getOtherUser(connection: Connection): User? {
        val currentUserId = _currentUser.value?.id ?: return null
        val otherUserId = connection.user_ids.firstOrNull { it != currentUserId } ?: return null
        return _connectedUsers.value[otherUserId]
    }
    
    /**
     * Update the current user's full name
     * Updates local state immediately, syncs with Supabase Auth metadata AND database
     */
    fun updateUsername(newName: String) {
        val user = _currentUser.value ?: run {
            println("updateUsername: No current user")
            return
        }
        
        println("updateUsername: Changing name from '${user.name}' to '$newName' for user ${user.id}")
        
        val previousName = user.name
        val updatedUser = user.copy(name = newName)
        _currentUser.value = updatedUser
        
        scope.launch {
            try {
                // 1. Update auth metadata (this persists after app restart)
                val authResult = authRepository.updateUserMetadata(newName)
                if (authResult.isFailure) {
                    println("updateUsername: Warning - failed to update auth metadata")
                }
                
                // 2. Ensure user exists in database
                val upsertResult = supabaseRepository.upsertUser(updatedUser)
                println("updateUsername: Upsert user result: $upsertResult")
                
                // 3. Update user name in database  
                val success = supabaseRepository.updateUserName(user.id, newName)
                if (!success) {
                    println("updateUsername: Failed to update name in database, but auth metadata was updated")
                } else {
                    println("updateUsername: Successfully updated full name to: $newName")
                }
            } catch (e: Exception) {
                println("updateUsername: Error updating name: ${e.message}")
                e.printStackTrace()
                // Only revert if we couldn't update auth metadata either
                _currentUser.value = user.copy(name = previousName)
            }
        }
    }
}

