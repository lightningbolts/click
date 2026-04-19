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
import compose.project.click.click.data.repository.ProximityHandshakeRecoveryPayload
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.coroutines.CancellationException
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
    private var pendingSyncPausedForAuth: Boolean = false
    
    /** Single shared instance; lazy so JVM/Robolectric tests can reference [AppDataManager] before [initTokenStorage]. */
    private val tokenStorage by lazy { createTokenStorage() }
    private val authRepository by lazy { AuthRepository(tokenStorage = tokenStorage) }
    private val supabaseRepository by lazy { SupabaseRepository() }
    private val chatRepository by lazy { SupabaseChatRepository(tokenStorage = tokenStorage) }

    /** Supabase Realtime Presence on channel `room:presence` (user IDs with an active app session). */
    val onlineUsers: StateFlow<Set<String>> get() = chatRepository.onlineUsers

    /** Coarse health of the shared presence channel; see [compose.project.click.click.data.repository.PresenceHealth]. */
    val presenceHealth: StateFlow<compose.project.click.click.data.repository.PresenceHealth>
        get() = chatRepository.presenceHealth
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

    /** Per-user archive rows ([connection_archives]); excludes these from Active surfaces. */
    private val _archivedConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedConnectionIds: StateFlow<Set<String>> = _archivedConnectionIds.asStateFlow()

    /** Per-user hidden rows ([connection_hidden]); excluded everywhere. */
    private val _hiddenConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenConnectionIds: StateFlow<Set<String>> = _hiddenConnectionIds.asStateFlow()

    /**
     * Incremented when a verified group ("click") is created elsewhere so [ConnectionsScreen]
     * can force-refresh its [ChatViewModel] chat list (separate repository instance).
     */
    private val _chatListRefreshEpoch = MutableStateFlow(0)
    val chatListRefreshEpoch: StateFlow<Int> = _chatListRefreshEpoch.asStateFlow()

    fun bumpChatListRefresh() {
        _chatListRefreshEpoch.value = _chatListRefreshEpoch.value + 1
    }
    
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
    private const val TOKEN_REFRESH_SKEW_MS = 60_000L
    private const val FOREGROUND_RECOVERY_DEBOUNCE_MS = 900L

    private var loadAllDataJob: Job? = null
    private var lastForegroundRecoveryMs: Long = 0L

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pendingConnectionsCount = MutableStateFlow(0)
    val pendingConnectionsCount: StateFlow<Int> = _pendingConnectionsCount.asStateFlow()

    private val _proximityHandshakeRecovered = MutableSharedFlow<ProximityHandshakeRecoveryPayload>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val proximityHandshakeRecovered: SharedFlow<ProximityHandshakeRecoveryPayload> =
        _proximityHandshakeRecovered.asSharedFlow()

    /** One-shot UI messages (e.g. profile or notification settings save failed). */
    private val _transientUserMessages = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val transientUserMessages: SharedFlow<String> = _transientUserMessages.asSharedFlow()

    /**
     * Emitted after [handleApplicationForegrounded] refreshes the Supabase session and Realtime
     * socket so [ChatViewModel] can re-attach Postgres channels without waiting for heartbeat timeouts.
     */
    private val _foregroundRealtimeRecovery = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val foregroundRealtimeRecovery: SharedFlow<Unit> = _foregroundRealtimeRecovery.asSharedFlow()

    /** Surfaces a one-shot message to UI collectors (e.g. onboarding before the main scaffold exists). */
    fun postTransientUserMessage(message: String) {
        if (message.isBlank()) return
        scope.launch { _transientUserMessages.emit(message.trim()) }
    }

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
     * OS resumed the UI (foreground). Cancels any in-flight [loadAllData] work, drops stale Ktor /
     * Realtime sockets, refreshes the GoTrue session, and starts a fresh load without waiting for
     * [STARTUP_TIMEOUT_MS] on half-open connections (common after iOS backgrounding).
     */
    fun handleApplicationForegrounded() {
        if (_ghostModeEnabled.value) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastForegroundRecoveryMs < FOREGROUND_RECOVERY_DEBOUNCE_MS) return
        lastForegroundRecoveryMs = now
        loadAllDataJob?.cancel()
        scope.launch {
            runCatching { SupabaseForegroundRecovery.recoverAfterBackground(SupabaseConfig.client) }
                .onFailure { e ->
                    println("AppDataManager: foreground Supabase recovery failed: ${e.redactedRestMessage()}")
                }
            _foregroundRealtimeRecovery.emit(Unit)
            lastRefreshTime = 0L
            startLoadAllDataJob()
        }
    }

    private fun startLoadAllDataJob() {
        loadAllDataJob?.cancel()
        loadAllDataJob = scope.launch {
            loadAllData()
        }
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
        
        startLoadAllDataJob()
    }
    
    /**
     * Load all app data
     */
    private suspend fun loadAllData() {
        _isLoading.value = true
        _error.value = null
        restoreCachedSnapshot()

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
                val snapshotDeferred = async {
                    runCatching { supabaseRepository.fetchUserConnectionsSnapshot(authUser.id) }
                        .onFailure { println("AppDataManager: Connection snapshot fetch failed: ${it.message}") }
                        .getOrNull()
                }

                val meta = authUser.userMetadata
                fun metaStr(key: String) =
                    meta?.get(key)?.toString()?.removeSurrounding("\"")?.trim()?.takeIf { it.isNotEmpty() }
                val metaFirst = metaStr("first_name")
                val metaLast = metaStr("last_name")
                val metaBirthday = metaStr("birthday")
                val cachedSessionUser = _currentUser.value?.takeIf { it.id == authUser.id }
                val cachedImage = cachedSessionUser?.image?.trim()?.takeIf { it.isNotEmpty() }
                val authDisplay = authUser.displayNameFromMetadata()
                println(
                    "AppDataManager: Auth metadata — first/last: $metaFirst / $metaLast, display: $authDisplay"
                )

                // Fetch user data from database
                var user = supabaseRepository.fetchUserById(authUser.id)
                println("AppDataManager: Fetched user from DB: ${user?.name}")

                if (user == null) {
                    val resolvedFirst = metaFirst ?: cachedSessionUser?.firstName
                    val resolvedLast = metaLast ?: cachedSessionUser?.lastName
                    val resolvedBirthday = metaBirthday ?: cachedSessionUser?.birthday
                    val resolvedImage = cachedImage
                    // Create user in database if not exists
                    val newUser = User(
                        id = authUser.id,
                        name = resolveDisplayName(
                            firstName = resolvedFirst,
                            lastName = resolvedLast,
                            fullName = metaStr("full_name") ?: authDisplay,
                            name = null,
                            email = authUser.email
                        ),
                        email = authUser.email,
                        image = resolvedImage,
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                        lastPolled = null,
                        firstName = resolvedFirst,
                        lastName = resolvedLast,
                        birthday = resolvedBirthday,
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
                    val resolvedImage = user.image?.trim()?.takeIf { it.isNotEmpty() } ?: cachedImage
                    val syncedUser = user.copy(
                        name = desiredName,
                        email = desiredEmail,
                        image = resolvedImage,
                        firstName = metaFirst ?: user.firstName ?: cachedSessionUser?.firstName,
                        lastName = metaLast ?: user.lastName ?: cachedSessionUser?.lastName,
                        birthday = metaBirthday ?: user.birthday ?: cachedSessionUser?.birthday,
                    )
                    if (syncedUser != user) {
                        println("AppDataManager: Syncing current user profile to users table: ${syncedUser.name}")
                        supabaseRepository.upsertUser(syncedUser)
                        user = syncedUser
                    }
                }

                _currentUser.value = user
                println("AppDataManager: Current user set to: ${user.name}")
                runCatching { chatRepository.startGlobalPresence(user.id) }
                    .onFailure { e -> println("AppDataManager: Global presence start failed: ${e.redactedRestMessage()}") }
                startPresenceHeartbeat(user.id)

                // Interest tags are not required for first Home paint.
                scope.launch {
                    runCatching { supabaseRepository.fetchUserInterests(user.id).getOrNull()?.tags.orEmpty() }
                        .onSuccess { tags ->
                            if (_currentUser.value?.id == user.id) {
                                _currentUser.value = _currentUser.value?.copy(tags = tags)
                            }
                        }
                        .onFailure { e ->
                            println("AppDataManager: Interest tags fetch failed: ${e.redactedRestMessage()}")
                        }
                }

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
                    val snapshot = snapshotDeferred.await()
                    if (snapshot != null) {
                        _connections.value = snapshot.connections
                        _archivedConnectionIds.value = snapshot.archivedConnectionIds
                        _hiddenConnectionIds.value = snapshot.hiddenConnectionIds
                    }

                    _isDataLoaded.value = true
                    lastRefreshTime = Clock.System.now().toEpochMilliseconds()
                    persistSnapshot()

                    // Keep first paint fast: hydrate connected users in background instead of
                    // blocking Home readiness on this network call.
                    scope.launch {
                        if (_currentUser.value?.id == user.id) {
                            runCatching { refreshConnectedUsers(_connections.value, user.id) }
                                .onFailure { e ->
                                    println("AppDataManager: Background connected-user hydration failed: ${e.redactedRestMessage()}")
                                }
                        }
                    }

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
            
        } catch (e: CancellationException) {
            // Replacing an in-flight load (foreground recovery, refresh) cancels this job; must not
            // treat that as an offline / sync failure or the banner shows until the next full load.
            throw e
        } catch (e: Exception) {
            println("Error loading app data: ${e.redactedRestMessage()}")
            // Do not printStackTrace() — RestException.message embeds Authorization/apikey headers.
            _error.value = mapStartupErrorMessage(e.redactedRestMessage())
            _isDataLoaded.value = true
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
        
        startLoadAllDataJob()
    }
    
    /**
     * Clear all data (on logout)
     */
    suspend fun clearData() {
        loadAllDataJob?.cancel()
        loadAllDataJob = null
        // R0.5: clearSessionCaches disposes all ephemeral channels AND zero-fills
        // group master keys AND stops global presence, so this single call
        // replaces the old stopGlobalPresence() + leaks derived keys into the
        // next signed-in user of the same device.
        runCatching { chatRepository.clearSessionCaches() }
            .onFailure { e -> println("AppDataManager: chat session cache clear failed: ${e.redactedRestMessage()}") }
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null
        _currentUser.value = null
        _connections.value = emptyList()
        _archivedConnectionIds.value = emptySet()
        _hiddenConnectionIds.value = emptySet()
        _connectedUsers.value = emptyMap()
        _userAvailability.value = null
        _isDataLoaded.value = false
        _isLoading.value = false
        _error.value = null
        _notificationPreferences.value = NotificationPreferences()
        _locationPreferences.value = LocationPreferences()
        _pendingConnectionsCount.value = 0
        NotificationRuntimeState.setNotificationPreferences(messageEnabled = true, callEnabled = true)
    }

    /**
     * Reset all cached data and reload from server.
     * Used after login/signup to ensure connection counts,
     * chats, and other data are fetched fresh.
     */
    fun resetAndReload() {
        loadAllDataJob?.cancel()
        loadAllDataJob = scope.launch {
            clearData()
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

    /** Optimistic hide: used after [connection_hidden] insert or when blocking. */
    fun hideConnectionLocally(connectionId: String) {
        _hiddenConnectionIds.value = _hiddenConnectionIds.value + connectionId
        removeConnection(connectionId)
    }

    fun unhideConnectionLocally(connectionId: String) {
        _hiddenConnectionIds.value = _hiddenConnectionIds.value - connectionId
        scope.launch { persistSnapshot() }
    }

    /**
     * Revert an optimistic [hideConnectionLocally]: removes the ID from hidden set and
     * restores the [Connection] back into the connections list. Used when the server call
     * to [connection_hidden] fails and we need to undo the local hide.
     */
    fun revertHideConnectionLocally(connectionId: String, connection: Connection) {
        _hiddenConnectionIds.value = _hiddenConnectionIds.value - connectionId
        _connections.value = (_connections.value + connection).distinctBy { it.id }
        scope.launch { persistSnapshot() }
    }

    fun markConnectionArchivedLocally(connectionId: String) {
        _archivedConnectionIds.value = _archivedConnectionIds.value + connectionId
        scope.launch { persistSnapshot() }
    }

    fun markConnectionUnarchivedLocally(connectionId: String) {
        _archivedConnectionIds.value = _archivedConnectionIds.value - connectionId
        scope.launch { persistSnapshot() }
    }

    /**
     * After QR/NFC reconnect: clear local junction bookkeeping and replace the in-memory row.
     */
    fun applyRestoredConnection(connection: Connection) {
        _archivedConnectionIds.value = _archivedConnectionIds.value - connection.id
        _hiddenConnectionIds.value = _hiddenConnectionIds.value - connection.id
        _connections.value = (_connections.value.filter { it.id != connection.id } + connection).distinctBy { it.id }
        scope.launch { persistSnapshot() }
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
        val previousPreferences = _notificationPreferences.value
        _notificationPreferences.value = preferences
        NotificationRuntimeState.setNotificationPreferences(
            messageEnabled = preferences.messagePushEnabled,
            callEnabled = preferences.callPushEnabled,
        )

        scope.launch {
            tokenStorage.saveMessageNotificationsEnabled(preferences.messagePushEnabled)
            tokenStorage.saveCallNotificationsEnabled(preferences.callPushEnabled)
            val saveResult = notificationPreferencesRepository.savePreferences(userId, preferences)
            if (saveResult.isFailure) {
                _notificationPreferences.value = previousPreferences
                NotificationRuntimeState.setNotificationPreferences(
                    messageEnabled = previousPreferences.messagePushEnabled,
                    callEnabled = previousPreferences.callPushEnabled,
                )
                tokenStorage.saveMessageNotificationsEnabled(previousPreferences.messagePushEnabled)
                tokenStorage.saveCallNotificationsEnabled(previousPreferences.callPushEnabled)
                val msg = saveResult.exceptionOrNull()?.message?.trim().orEmpty()
                    .ifBlank { "Couldn't save notification settings. Please try again." }
                _transientUserMessages.emit(msg)
                return@launch
            }

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
                println("toggleFreeThisWeek: Error saving to local storage: ${e.redactedRestMessage()}")
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
                println("toggleFreeThisWeek: Error updating Supabase: ${e.redactedRestMessage()}")
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

                val dbResult = supabaseRepository.updateUserProfileNames(user.id, f, l)
                if (dbResult.isFailure) {
                    println("updateProfileName: Failed to update names in database: ${dbResult.exceptionOrNull()?.message}")
                    _currentUser.value = previousUser
                    val msg = dbResult.exceptionOrNull()?.message?.trim().orEmpty()
                        .ifBlank { "Couldn't update your profile. Please try again." }
                    _transientUserMessages.emit(msg)
                } else {
                    println("updateProfileName: Successfully updated profile to: $display")
                    persistSnapshot()
                }
            } catch (e: Exception) {
                println("updateProfileName: Error updating profile: ${e.redactedRestMessage()}")
                e.printStackTrace()
                _currentUser.value = previousUser
                _transientUserMessages.emit(
                    e.message?.trim().orEmpty().ifBlank { "Couldn't update your profile. Please try again." },
                )
            }
        }
    }

    /**
     * Updates the in-memory current user avatar URL after a successful storage upload + DB update.
     */
    fun applyProfilePictureUrl(publicUrl: String) {
        val latest = _currentUser.value ?: return
        _currentUser.value = latest.copy(image = publicUrl)
        scope.launch {
            runCatching { persistSnapshot() }
                .onFailure { println("applyProfilePictureUrl: snapshot failed: ${it.message}") }
        }
    }

    private fun mapStartupErrorMessage(rawMessage: String): String {
        val trimmed = rawMessage.trim()
        val normalized = trimmed.lowercase()
        return when {
            normalized.contains("401") ||
                normalized.contains("403") ||
                normalized.contains("unauthorized") ||
                normalized.contains("not authorized") ||
                normalized.contains("invalid jwt") ->
                "Your session expired. Please sign in again to resume sync."
            trimmed.isBlank() -> "No internet connection"
            else -> trimmed
        }
    }

    private fun Throwable.isAuthorizationFailure(): Boolean {
        val normalized = message?.lowercase().orEmpty()
        return normalized.contains("401") ||
            normalized.contains("403") ||
            normalized.contains("unauthorized") ||
            normalized.contains("not authorized") ||
            normalized.contains("invalid jwt") ||
            normalized.contains("jwt expired")
    }

    private fun pausePendingSyncForAuth() {
        if (!pendingSyncPausedForAuth) {
            println("AppDataManager: Pending sync paused until a valid auth session is restored.")
        }
        pendingSyncPausedForAuth = true
    }

    private fun resumePendingSyncIfPaused() {
        if (pendingSyncPausedForAuth) {
            println("AppDataManager: Pending sync resumed after auth recovery.")
        }
        pendingSyncPausedForAuth = false
    }

    private suspend fun resolveJwtForPendingSync(forceRefresh: Boolean = false): String? {
        val now = Clock.System.now().toEpochMilliseconds()
        val existingJwt = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
        val expiresAt = tokenStorage.getExpiresAt()
        val needsRefresh = forceRefresh ||
            existingJwt == null ||
            (expiresAt != null && expiresAt <= now + TOKEN_REFRESH_SKEW_MS)

        if (!needsRefresh) return existingJwt

        authRepository.refreshSession()
            .onFailure { println("AppDataManager: Session refresh for pending sync failed: ${it.message}") }

        tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        val restoreResult = authRepository.restoreSession()
        if (restoreResult.isFailure) {
            println("AppDataManager: Session restore for pending sync failed: ${restoreResult.exceptionOrNull()?.message}")
            return null
        }

        authRepository.refreshSession()
            .onFailure { println("AppDataManager: Session refresh after restore failed: ${it.message}") }

        return tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun startPendingConnectionSync() {
        if (pendingSyncJob?.isActive == true) return

        pendingSyncJob = scope.launch {
            while (true) {
                delay(PENDING_SYNC_RETRY_MS)
                val currentUserId = _currentUser.value?.id
                if (currentUserId.isNullOrBlank()) continue

                val jwt = resolveJwtForPendingSync()
                if (jwt.isNullOrBlank()) {
                    pausePendingSyncForAuth()
                    refreshPendingConnectionCount()
                    continue
                }

                runCatching {
                    connectionRepository.syncPendingConnections()
                    var proximity = connectionRepository.syncPendingProximityHandshakes(jwt)
                    if (proximity.authorizationFailed) {
                        val refreshedJwt = resolveJwtForPendingSync(forceRefresh = true)
                        if (refreshedJwt.isNullOrBlank()) {
                            pausePendingSyncForAuth()
                            return@runCatching
                        }
                        proximity = connectionRepository.syncPendingProximityHandshakes(refreshedJwt)
                        if (proximity.authorizationFailed) {
                            pausePendingSyncForAuth()
                            return@runCatching
                        }
                    }

                    resumePendingSyncIfPaused()

                    val recovered = proximity.recoveredUsers
                    if (!recovered.isNullOrEmpty()) {
                        _proximityHandshakeRecovered.emit(
                            ProximityHandshakeRecoveryPayload(
                                users = recovered,
                                encounterLogged = proximity.recoveredEncounterLogged,
                                groupCliqueCandidateMemberIds = proximity.groupCliqueCandidateMemberIds,
                            ),
                        )
                    }
                }
                    .onFailure {
                        if (it.isAuthorizationFailure()) {
                            pausePendingSyncForAuth()
                        } else {
                            println("AppDataManager: Pending sync attempt failed: ${it.message}")
                        }
                    }
                refreshPendingConnectionCount()
            }
        }
    }

    suspend fun flushPendingProximityHandshakesFromBackgroundWorker() {
        val jwt = resolveJwtForPendingSync(forceRefresh = true)
        if (jwt.isNullOrBlank()) {
            pausePendingSyncForAuth()
            return
        }

        var proximity = connectionRepository.syncPendingProximityHandshakes(jwt)
        if (proximity.authorizationFailed) {
            val refreshedJwt = resolveJwtForPendingSync(forceRefresh = true)
            if (refreshedJwt.isNullOrBlank()) {
                pausePendingSyncForAuth()
                return
            }
            proximity = connectionRepository.syncPendingProximityHandshakes(refreshedJwt)
            if (proximity.authorizationFailed) {
                pausePendingSyncForAuth()
                return
            }
        }

        resumePendingSyncIfPaused()

        val recovered = proximity.recoveredUsers
        if (!recovered.isNullOrEmpty()) {
            _proximityHandshakeRecovered.emit(
                ProximityHandshakeRecoveryPayload(
                    users = recovered,
                    encounterLogged = proximity.recoveredEncounterLogged,
                    groupCliqueCandidateMemberIds = proximity.groupCliqueCandidateMemberIds,
                ),
            )
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
            _archivedConnectionIds.value = snapshot.archivedConnectionIds
            _hiddenConnectionIds.value = snapshot.hiddenConnectionIds
            _isDataLoaded.value = snapshot.currentUser != null || snapshot.connections.isNotEmpty()
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
            locationPreferences = _locationPreferences.value,
            archivedConnectionIds = _archivedConnectionIds.value,
            hiddenConnectionIds = _hiddenConnectionIds.value,
        )
        runCatching {
            tokenStorage.saveCachedAppSnapshot(json.encodeToString(snapshot))
        }.onFailure {
            println("AppDataManager: Failed to persist cached snapshot: ${it.message}")
        }
    }
}

