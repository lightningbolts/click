package compose.project.click.click.data

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.CachedAppSnapshot
import compose.project.click.click.data.models.LocationPreferences
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserAvailability
import compose.project.click.click.data.models.isResolvedDisplayName
import compose.project.click.click.data.models.resolveDisplayName
import compose.project.click.click.data.repository.NotificationPreferences
import compose.project.click.click.data.repository.NotificationPreferencesRepository
import compose.project.click.click.notifications.createPushNotificationService
import compose.project.click.click.notifications.NotificationRuntimeState
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.SupabaseChatRepository
import compose.project.click.click.data.repository.ConnectionRepository
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.createTokenStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Singleton app state manager that loads data once at app startup.
 * Prevents reloading when navigating between screens.
 */
object AppDataManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var presenceHeartbeatJob: Job? = null
    private var pendingSyncJob: Job? = null
    
    /** Single shared instance; lazy so JVM/Robolectric tests can reference [AppDataManager] before [initTokenStorage]. */
    private val tokenStorage by lazy { createTokenStorage() }
    private val authRepository by lazy { AuthRepository(tokenStorage = tokenStorage) }
    private val supabaseRepository by lazy { SupabaseRepository() }
    private val chatRepository by lazy { SupabaseChatRepository(tokenStorage = tokenStorage) }
    private val notificationPreferencesRepository by lazy { NotificationPreferencesRepository() }
    private val connectionRepository by lazy { ConnectionRepository() }
    private val pushNotificationService by lazy { createPushNotificationService() }
    private val json = Json { ignoreUnknownKeys = true }
    
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
    private const val PRESENCE_HEARTBEAT_MS = 30_000L
    private const val PENDING_SYNC_RETRY_MS = 15_000L
    private const val STARTUP_TIMEOUT_MS = 15_000L
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pendingConnectionsCount = MutableStateFlow(0)
    val pendingConnectionsCount: StateFlow<Int> = _pendingConnectionsCount.asStateFlow()

    private val _usingCachedData = MutableStateFlow(false)
    val usingCachedData: StateFlow<Boolean> = _usingCachedData.asStateFlow()
    
    // Ghost Mode state - privacy toggle to stop sharing location and halt network requests
    private val _ghostModeEnabled = MutableStateFlow(false)
    val ghostModeEnabled: StateFlow<Boolean> = _ghostModeEnabled.asStateFlow()

    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

    // Location privacy preferences (persisted to Supabase profile)
    private val _locationPreferences = MutableStateFlow(LocationPreferences())
    val locationPreferences: StateFlow<LocationPreferences> = _locationPreferences.asStateFlow()
    
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
            refreshPendingConnectionCount()
        }
        startPendingConnectionSync()
        
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
        val restoredFromCache = restoreCachedSnapshot()
        
        try {
            // Get current user from auth
            val authUser = authRepository.getCurrentUser()
            if (authUser == null) {
                println("AppDataManager: No auth user found")
                _isDataLoaded.value = true
                _isLoading.value = false
                return
            }
            
            println("AppDataManager: Loading data for user ${authUser.id}")

            withTimeout(STARTUP_TIMEOUT_MS) {
                val meta = authUser.userMetadata
                fun metaStr(key: String) =
                    meta?.get(key)?.toString()?.removeSurrounding("\"")?.trim()?.takeIf { it.isNotEmpty() }
                val metaFirst = metaStr("first_name")
                val metaLast = metaStr("last_name")
                val metaBirthday = metaStr("birthday")
                val authDisplay = authUser.displayNameFromMetadata()
                println(
                    "AppDataManager: Auth metadata — first/last: $metaFirst / $metaLast, display: $authDisplay"
                )

                // Fetch user data from database
                var user = supabaseRepository.fetchUserById(authUser.id)
                println("AppDataManager: Fetched user from DB: ${user?.name}")

                if (user == null) {
                    // Create user in database if not exists
                    val newUser = User(
                        id = authUser.id,
                        name = resolveDisplayName(
                            firstName = metaFirst,
                            lastName = metaLast,
                            fullName = metaStr("full_name") ?: authDisplay,
                            name = null,
                            email = authUser.email
                        ),
                        email = authUser.email,
                        image = null,
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        lastPolled = null,
                        firstName = metaFirst,
                        lastName = metaLast,
                        birthday = metaBirthday,
                        connections = emptyList(),
                        paired_with = emptyList(),
                        connection_today = 0,
                        last_paired = null
                    )
                    println("AppDataManager: Creating new user in DB: ${newUser.name}")
                    supabaseRepository.upsertUser(newUser)
                    user = newUser
                } else {
                    val desiredName = resolveDisplayName(
                        firstName = metaFirst ?: user.firstName,
                        lastName = metaLast ?: user.lastName,
                        fullName = metaStr("full_name") ?: authDisplay,
                        name = user.name,
                        email = authUser.email ?: user.email
                    )
                    val desiredEmail = authUser.email ?: user.email
                    val syncedUser = user.copy(
                        name = desiredName,
                        email = desiredEmail,
                        firstName = metaFirst ?: user.firstName,
                        lastName = metaLast ?: user.lastName,
                        birthday = metaBirthday ?: user.birthday
                    )
                    if (syncedUser != user) {
                        println("AppDataManager: Syncing current user profile to users table: ${syncedUser.name}")
                        supabaseRepository.upsertUser(syncedUser)
                        user = syncedUser
                    }
                }

                val interestTags = supabaseRepository.fetchUserInterests(user.id).getOrNull()?.tags.orEmpty()
                _currentUser.value = user.copy(tags = interestTags)
                println("AppDataManager: Current user set to: ${user.name}")
                startPresenceHeartbeat(user.id)

                // Load location preferences from Supabase
                runCatching { supabaseRepository.fetchLocationPreferences(user.id) }
                    .onSuccess { _locationPreferences.value = it }
                    .onFailure { println("AppDataManager: Failed to load location preferences: ${it.message}") }

                val localNotificationPreferences = NotificationPreferences(
                    messagePushEnabled = tokenStorage.getMessageNotificationsEnabled() ?: true,
                    callPushEnabled = tokenStorage.getCallNotificationsEnabled() ?: true,
                )
                _notificationPreferences.value = localNotificationPreferences
                NotificationRuntimeState.setNotificationPreferences(
                    messageEnabled = localNotificationPreferences.messagePushEnabled,
                    callEnabled = localNotificationPreferences.callPushEnabled,
                )

                // Push registration is non-critical for first paint. Keep it off the main
                // startup path so chats/connections do not wait on permission or token work.
                if (localNotificationPreferences.messagePushEnabled || localNotificationPreferences.callPushEnabled) {
                    scope.launch {
                        runCatching { pushNotificationService.requestPermission() }
                            .onFailure { println("AppDataManager: Push permission request failed: ${it.message}") }
                        runCatching { pushNotificationService.registerToken(user.id) }
                            .onFailure { println("AppDataManager: Push token registration failed: ${it.message}") }
                    }
                }

                scope.launch {
                    val remotePreferences = notificationPreferencesRepository.fetchPreferences(user.id)
                    _notificationPreferences.value = remotePreferences
                    NotificationRuntimeState.setNotificationPreferences(
                        messageEnabled = remotePreferences.messagePushEnabled,
                        callEnabled = remotePreferences.callPushEnabled,
                    )
                    tokenStorage.saveMessageNotificationsEnabled(remotePreferences.messagePushEnabled)
                    tokenStorage.saveCallNotificationsEnabled(remotePreferences.callPushEnabled)

                    if (remotePreferences.messagePushEnabled || remotePreferences.callPushEnabled) {
                        runCatching { pushNotificationService.requestPermission() }
                            .onFailure { println("AppDataManager: Push permission request failed after sync: ${it.message}") }
                        runCatching { pushNotificationService.registerToken(user.id) }
                            .onFailure { println("AppDataManager: Push token registration failed after sync: ${it.message}") }
                    }
                }

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

                coroutineScope {
                    val availabilityDeferred = async {
                        runCatching { supabaseRepository.fetchUserAvailability(user.id) }
                            .onFailure { println("AppDataManager: Availability fetch failed: ${it.message}") }
                            .getOrNull()
                    }

                    // Prioritize connections and connected-user hydration so the Home/Map/Chats
                    // screens are ready before slower auxiliary startup work completes.
                    val userConnections = supabaseRepository.fetchUserConnections(user.id)
                    _connections.value = userConnections
                    refreshConnectedUsers(userConnections, user.id)

                    _isDataLoaded.value = true
                    _usingCachedData.value = false
                    lastRefreshTime = Clock.System.now().toEpochMilliseconds()
                    persistSnapshot()

                    // Apply availability after the primary connection data is visible.
                    val availability = availabilityDeferred.await()
                    if (availability != null) {
                        _userAvailability.value = availability
                        tokenStorage.saveFreeThisWeek(availability.isFreeThisWeek)
                        println("AppDataManager: Synced availability from Supabase: isFreeThisWeek=${availability.isFreeThisWeek}")
                    } else if (localFreeThisWeek == null) {
                        println("AppDataManager: No availability found locally or on server")
                    }
                }
            }
            
        } catch (e: Exception) {
            println("Error loading app data: ${e.message}")
            e.printStackTrace()
            _error.value = e.message ?: "No internet connection"
            _isDataLoaded.value = true
            if (restoredFromCache) {
                _usingCachedData.value = true
            }
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
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
        _currentUser.value = null
        _connections.value = emptyList()
        _connectedUsers.value = emptyMap()
        _userAvailability.value = null
        _isDataLoaded.value = false
        _isLoading.value = false
        _error.value = null
        _notificationPreferences.value = NotificationPreferences()
        _locationPreferences.value = LocationPreferences()
        _pendingConnectionsCount.value = 0
        _usingCachedData.value = false
        NotificationRuntimeState.setNotificationPreferences(messageEnabled = true, callEnabled = true)
    }

    /**
     * Reset all cached data and reload from server.
     * Used after login/signup to ensure connection counts,
     * chats, and other data are fetched fresh.
     */
    fun resetAndReload() {
        clearData()
        scope.launch {
            loadAllData()
        }
    }
    
    /**
     * Update connections list (after making a new connection)
     */
    fun addConnection(connection: Connection, otherUser: User? = null) {
        _connections.value = (_connections.value + connection).distinctBy { it.id }

        val currentUserId = _currentUser.value?.id
        val otherUserId = currentUserId?.let { currentId ->
            connection.user_ids.firstOrNull { it != currentId }
        }

        if (otherUser != null && otherUserId != null && otherUser.id == otherUserId) {
            val existingUser = _connectedUsers.value[otherUser.id]
            val preferredUser = when {
                isResolvedDisplayName(otherUser.name) -> otherUser
                existingUser != null -> existingUser
                else -> otherUser
            }
            _connectedUsers.value = _connectedUsers.value + (otherUser.id to preferredUser)
        }

        if (currentUserId != null && otherUserId != null) {
            if (_connectedUsers.value[otherUserId] == null) {
                _connectedUsers.value = _connectedUsers.value + (
                    otherUserId to User(id = otherUserId, name = "Connection", createdAt = 0L)
                )
            }

            scope.launch {
                refreshConnectedUsers(_connections.value, currentUserId)
            }
        }

        scope.launch {
            persistSnapshot()
        }
    }
    
    /**
     * Remove a connection
     */
    fun removeConnection(connectionId: String) {
        _connections.value = _connections.value.filter { it.id != connectionId }
        scope.launch {
            persistSnapshot()
        }
    }

    fun replaceLocalConnection(localId: String, syncedConnection: Connection, otherUser: User? = null) {
        _connections.value = _connections.value
            .filterNot { it.id == localId }
            .plus(syncedConnection)
            .distinctBy { it.id }
        if (otherUser != null) {
            _connectedUsers.value = _connectedUsers.value + (otherUser.id to otherUser)
        }
        scope.launch {
            persistSnapshot()
        }
    }

    /**
     * Patch in-memory [Connection] rows when chat activity changes so [connections] consumers
     * see fresh [Connection.last_message_at] and optional preview text without a full reload.
     */
    fun updateConnectionChatActivity(
        connectionId: String,
        lastMessageAt: Long,
        lastMessagePreview: Message? = null
    ) {
        _connections.value = _connections.value.map { c ->
            if (c.id != connectionId) return@map c
            val mergedAt = listOfNotNull(c.last_message_at, lastMessageAt).maxOrNull()
            val cachedPreviewTs = c.chat.messages.lastOrNull()?.timeCreated
            val newChat = if (lastMessagePreview != null) {
                c.chat.copy(messages = listOf(lastMessagePreview))
            } else if (mergedAt != null && cachedPreviewTs != null && cachedPreviewTs < mergedAt) {
                // Keep state immutable and clear stale preview text when we only know activity advanced.
                c.chat.copy(messages = emptyList())
            } else {
                c.chat
            }
            c.copy(last_message_at = mergedAt, chat = newChat)
        }
        scope.launch { persistSnapshot() }
    }

    fun setPendingConnectionsCount(count: Int) {
        _pendingConnectionsCount.value = count
    }

    suspend fun refreshPendingConnectionCount() {
        val queueJson = tokenStorage.getPendingConnectionQueue()
        _pendingConnectionsCount.value = runCatching {
            if (queueJson.isNullOrBlank()) {
                0
            } else {
                json.decodeFromString<List<compose.project.click.click.data.models.PendingConnectionDraft>>(queueJson).size
            }
        }.getOrElse { 0 }
    }

    fun setMessageNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(messagePushEnabled = enabled)
        )
    }

    fun setCallNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(callPushEnabled = enabled)
        )
    }

    private fun updateNotificationPreferences(preferences: NotificationPreferences) {
        val userId = _currentUser.value?.id ?: return
        _notificationPreferences.value = preferences
        NotificationRuntimeState.setNotificationPreferences(
            messageEnabled = preferences.messagePushEnabled,
            callEnabled = preferences.callPushEnabled,
        )

        scope.launch {
            tokenStorage.saveMessageNotificationsEnabled(preferences.messagePushEnabled)
            tokenStorage.saveCallNotificationsEnabled(preferences.callPushEnabled)
            notificationPreferencesRepository.savePreferences(userId, preferences)

            if (preferences.messagePushEnabled || preferences.callPushEnabled) {
                runCatching { pushNotificationService.requestPermission() }
                    .onFailure { println("AppDataManager: Push permission request failed after settings update: ${it.message}") }
                runCatching { pushNotificationService.registerToken(userId) }
                    .onFailure { println("AppDataManager: Push token registration failed after settings update: ${it.message}") }
            }
        }
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
     * Whether we should capture GPS at tap/connection time.
     * False when Ghost Mode is on or when connection snap preference is off.
     */
    fun shouldCaptureLocationAtTap(): Boolean {
        if (_ghostModeEnabled.value) return false
        return _locationPreferences.value.connectionSnapEnabled
    }

    /**
     * Update location preferences and persist to Supabase.
     */
    fun updateLocationPreferences(prefs: LocationPreferences) {
        val userId = _currentUser.value?.id ?: return
        _locationPreferences.value = prefs
        scope.launch {
            runCatching { supabaseRepository.updateLocationPreferences(userId, prefs) }
                .onFailure { println("AppDataManager: Failed to save location preferences: ${it.message}") }
            persistSnapshot()
        }
    }

    fun setConnectionSnapEnabled(enabled: Boolean) {
        updateLocationPreferences(_locationPreferences.value.copy(connectionSnapEnabled = enabled))
    }

    fun setShowOnMapEnabled(enabled: Boolean) {
        updateLocationPreferences(_locationPreferences.value.copy(showOnMapEnabled = enabled))
    }

    fun setIncludeInInsightsEnabled(enabled: Boolean) {
        updateLocationPreferences(_locationPreferences.value.copy(includeInInsightsEnabled = enabled))
    }

    /**
     * Get the other user in a connection
     */
    fun getOtherUser(connection: Connection): User? {
        val currentUserId = _currentUser.value?.id ?: return null
        val otherUserId = connection.user_ids.firstOrNull { it != currentUserId } ?: return null
        return _connectedUsers.value[otherUserId]
    }

    private fun startPresenceHeartbeat(userId: String) {
        if (presenceHeartbeatJob?.isActive == true) return

        presenceHeartbeatJob = scope.launch {
            while (_currentUser.value?.id == userId) {
                if (!_ghostModeEnabled.value) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    supabaseRepository.updateUserLastPolled(userId, now)
                    refreshConnectedUsers(_connections.value, userId)
                    _currentUser.value = _currentUser.value?.copy(lastPolled = now)
                }
                delay(PRESENCE_HEARTBEAT_MS)
            }
        }
    }

    private suspend fun refreshConnectedUsers(connections: List<Connection>, currentUserId: String) {
        val otherUserIds = connections
            .flatMap { it.user_ids }
            .filter { it != currentUserId }
            .distinct()

        if (otherUserIds.isEmpty()) {
            _connectedUsers.value = emptyMap()
            return
        }

        val users = supabaseRepository.fetchUsersByIds(otherUserIds)
        val existingUsers = _connectedUsers.value
        val usersById = users.associateBy { it.id }

        _connectedUsers.value = otherUserIds.associateWith { userId ->
            val fetchedUser = usersById[userId]
            val existingUser = existingUsers[userId]

            when {
                fetchedUser != null && isResolvedDisplayName(fetchedUser.name) -> fetchedUser
                existingUser != null && isResolvedDisplayName(existingUser.name) -> existingUser
                fetchedUser != null -> fetchedUser
                existingUser != null -> existingUser
                else -> User(id = userId, name = "Connection", createdAt = 0L)
            }
        }

        // If any users are still unresolved after the fetch (e.g. Supabase cold start caused the
        // RPC to fail silently), schedule quick background retries so the UI updates within seconds
        // rather than waiting for the 30-second presence heartbeat.
        scheduleUnresolvedUserRetry()
    }

    /**
     * If the connected-users map still has any "Connection" placeholder names, retry name
     * resolution in the background at 2 s, then 8 s intervals so a cold-start RPC failure
     * doesn't leave placeholder names visible for a full heartbeat cycle (30 s).
     */
    private fun scheduleUnresolvedUserRetry() {
        val unresolvedIds = _connectedUsers.value
            .entries
            .filter { !isResolvedDisplayName(it.value.name) }
            .map { it.key }

        if (unresolvedIds.isEmpty()) return

        scope.launch {
            // More aggressive retry schedule to bridge the gap until the 30-s heartbeat.
            for (delayMs in listOf(2_000L, 5_000L, 12_000L, 25_000L)) {
                delay(delayMs)
                // Stop if nothing left to resolve
                val stillUnresolved = _connectedUsers.value
                    .entries
                    .filter { !isResolvedDisplayName(it.value.name) }
                    .map { it.key }

                if (stillUnresolved.isEmpty()) break

                val retried = supabaseRepository.fetchUsersByIds(stillUnresolved)
                val currentMap = _connectedUsers.value.toMutableMap()
                var anyResolved = false
                retried.forEach { user ->
                    if (isResolvedDisplayName(user.name)) {
                        currentMap[user.id] = user
                        anyResolved = true
                    }
                }
                if (anyResolved) {
                    _connectedUsers.value = currentMap
                }
            }
        }
    }
    
    /**
     * Update the current user's first and last name in auth metadata and [public.users].
     */
    fun updateProfileName(firstName: String, lastName: String) {
        val user = _currentUser.value ?: run {
            println("updateProfileName: No current user")
            return
        }

        val f = firstName.trim()
        val l = lastName.trim()
        if (f.isEmpty()) {
            println("updateProfileName: First name is required")
            return
        }

        val display = listOf(f, l).filter { it.isNotEmpty() }.joinToString(" ")
        println("updateProfileName: Changing profile to '$display' for user ${user.id}")

        val previousUser = user
        val updatedUser = user.copy(
            name = display,
            firstName = f,
            lastName = l.ifEmpty { null },
        )
        _currentUser.value = updatedUser

        scope.launch {
            try {
                val authResult = authRepository.updateUserProfileNames(f, l)
                if (authResult.isFailure) {
                    println("updateProfileName: Warning - failed to update auth metadata")
                }

                val upsertResult = supabaseRepository.upsertUser(updatedUser)
                println("updateProfileName: Upsert user result: $upsertResult")

                val success = supabaseRepository.updateUserProfileNames(user.id, f, l)
                if (!success) {
                    println("updateProfileName: Failed to update names in database")
                } else {
                    println("updateProfileName: Successfully updated profile to: $display")
                }
                persistSnapshot()
            } catch (e: Exception) {
                println("updateProfileName: Error updating profile: ${e.message}")
                e.printStackTrace()
                _currentUser.value = previousUser
            }
        }
    }

    private fun startPendingConnectionSync() {
        if (pendingSyncJob?.isActive == true) return

        pendingSyncJob = scope.launch {
            while (true) {
                delay(PENDING_SYNC_RETRY_MS)
                val currentUserId = _currentUser.value?.id
                if (currentUserId.isNullOrBlank()) continue

                runCatching { connectionRepository.syncPendingConnections() }
                    .onFailure { println("AppDataManager: Pending sync attempt failed: ${it.message}") }
                refreshPendingConnectionCount()
            }
        }
    }

    private suspend fun restoreCachedSnapshot(): Boolean {
        val snapshotJson = tokenStorage.getCachedAppSnapshot()
        if (snapshotJson.isNullOrBlank()) return false

        return runCatching {
            val snapshot = json.decodeFromString<CachedAppSnapshot>(snapshotJson)
            _currentUser.value = snapshot.currentUser
            _connections.value = snapshot.connections
            _connectedUsers.value = snapshot.connectedUsers.associateBy { it.id }
            _locationPreferences.value = snapshot.locationPreferences
            _isDataLoaded.value = snapshot.currentUser != null || snapshot.connections.isNotEmpty()
            _usingCachedData.value = _isDataLoaded.value
            snapshot
        }.onFailure {
            println("AppDataManager: Failed to restore cached snapshot: ${it.message}")
        }.isSuccess
    }

    private suspend fun persistSnapshot() {
        val snapshot = CachedAppSnapshot(
            currentUser = _currentUser.value,
            connections = _connections.value,
            connectedUsers = _connectedUsers.value.values.toList(),
            locationPreferences = _locationPreferences.value
        )
        runCatching {
            tokenStorage.saveCachedAppSnapshot(json.encodeToString(snapshot))
        }.onFailure {
            println("AppDataManager: Failed to persist cached snapshot: ${it.message}")
        }
    }
}

