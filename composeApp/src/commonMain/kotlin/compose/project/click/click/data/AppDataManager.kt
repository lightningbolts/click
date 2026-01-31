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
                _isLoading.value = false
                return
            }
            
            // Fetch user data from database
            var user = supabaseRepository.fetchUserById(authUser.id)
            if (user == null) {
                // Create fallback user if not in database
                user = User(
                    id = authUser.id,
                    name = authUser.email?.substringBefore('@') ?: "User",
                    email = authUser.email,
                    image = null,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    lastPolled = null,
                    connections = emptyList(),
                    paired_with = emptyList(),
                    connection_today = 0,
                    last_paired = null
                )
            }
            _currentUser.value = user
            
            // Fetch availability
            val availability = supabaseRepository.fetchUserAvailability(user.id)
            _userAvailability.value = availability
            
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
            _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Refresh data - respects cooldown to prevent excessive API calls
     */
    fun refresh(force: Boolean = false) {
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
     * Update user availability
     */
    fun updateUserAvailability(availability: UserAvailability) {
        _userAvailability.value = availability
    }

    /**
     * Toggle free this week status
     */
    fun toggleFreeThisWeek() {
        val user = _currentUser.value ?: return
        val current = _userAvailability.value
        val newStatus = !(current?.isFreeThisWeek ?: false)
        
        val updated = current?.copy(
            isFreeThisWeek = newStatus,
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        ) ?: UserAvailability(
            userId = user.id,
            isFreeThisWeek = newStatus,
            lastUpdated = Clock.System.now().toEpochMilliseconds()
        )
        
        _userAvailability.value = updated
        
        scope.launch {
            supabaseRepository.setFreeThisWeek(user.id, newStatus)
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
     * Update the current user's username
     */
    fun updateUsername(newUsername: String) {
        val user = _currentUser.value ?: return
        val updatedUser = user.copy(name = newUsername)
        _currentUser.value = updatedUser
        
        scope.launch {
            supabaseRepository.updateUserName(user.id, newUsername)
        }
    }
}
