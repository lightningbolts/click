@file:Suppress("ktlint:standard:max-line-length", "ktlint:standard:backing-property-naming")

package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.api.CommunityHubNearbyDto // pragma: allowlist secret
import compose.project.click.click.data.models.CachedAppSnapshot // pragma: allowlist secret
import compose.project.click.click.data.models.CachedChatThread // pragma: allowlist secret
import compose.project.click.click.data.models.CachedHubThread // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.HomeLayoutMode // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.PendingConnectionDraft // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserAvailability // pragma: allowlist secret
import compose.project.click.click.data.realtime.RealtimeCoordinator // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.MapBeaconRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.NotificationPreferences // pragma: allowlist secret
import compose.project.click.click.data.repository.NotificationPreferencesRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.PresenceHealth // pragma: allowlist secret
import compose.project.click.click.data.repository.ProximityHandshakeRecoveryPayload // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.network.NetworkConnectivityMonitor // pragma: allowlist secret
import compose.project.click.click.notifications.createPushNotificationService // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeMapBeaconLists // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneChatsByPeer // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lightweight model for a community hub the user has successfully joined this session.
 * Surfaced in the connections feed so the user can re-enter the chat without the map.
 */
@Serializable
data class ActiveHubEntry(
    val hubId: String,
    val name: String,
    val realtimeChannel: String,
    val occupantCount: Int = 1,
    val joinedAtMs: Long = 0L,
    val category: String = "general",
    val creatorId: String? = null,
    val isEventHub: Boolean = false,
)

fun ActiveHubEntry.opensAsEventHub(): Boolean =
    isEventHub || category.equals("event", ignoreCase = true)

/**
 * Singleton app state manager that loads data once at app startup.
 * Prevents reloading when navigating between screens.
 */
object AppDataManager {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal var presenceHeartbeatJob: Job? = null
    internal var pendingSyncJob: Job? = null
    internal var chatPrefetchJob: Job? = null
    internal var aggressiveBackgroundChatSyncJob: Job? = null
    internal var realtimeCoordinatorJob: Job? = null
    internal var lastSyncedInboxVersion = 0L
    internal var beaconPrefetchedThisSession = false

    /** One retry when cold-start prefetch ran before auth/GPS and left caches empty. */
    internal var discoveryPrefetchEmptyRetryUsed = false
    internal var profilePrefetchJob: Job? = null
    internal var queuedProfilePrefetchIds: Set<String> = emptySet()
    internal var pendingSyncPausedForAuth: Boolean = false
    internal var networkConnectivityJob: Job? = null
    internal val networkConnectivityMonitor by lazy { NetworkConnectivityMonitor() }

    /** Single shared instance; lazy so JVM/Robolectric tests can reference [AppDataManager] before [initTokenStorage]. */
    internal val tokenStorage by lazy { createTokenStorage() }
    internal val authRepository by lazy { AuthRepository(tokenStorage = tokenStorage) }
    internal val supabaseRepository by lazy { SupabaseRepository() }
    internal val chatRepository by lazy { SupabaseChatRepository(tokenStorage = tokenStorage) }

    /** Supabase Realtime Presence on channel `room:presence` (user IDs with an active app session). */
    val onlineUsers: StateFlow<Set<String>> get() = chatRepository.onlineUsers

    /** Coarse health of the shared presence channel; see [PresenceHealth]. */
    val presenceHealth: StateFlow<PresenceHealth>
        get() = chatRepository.presenceHealth
    internal val notificationPreferencesRepository by lazy { NotificationPreferencesRepository() }
    internal val connectionRepository by lazy { ConnectionRepository() }
    internal val pushNotificationService by lazy { createPushNotificationService() }
    internal val mapBeaconRepository by lazy { MapBeaconRepository() }
    internal val locationService by lazy { LocationService() }
    internal val json = Json { ignoreUnknownKeys = true }

    /**
     * Eagerly-prefetched map beacons + community hubs, fetched in parallel with the connections
     * snapshot at app load so the Social Map is already hydrated before the user opens that tab.
     * [MapViewModel] seeds its own state from these on init.
     */
    internal val _prefetchedMapBeacons = MutableStateFlow<List<MapBeacon>>(emptyList())
    val prefetchedMapBeacons: StateFlow<List<MapBeacon>> = _prefetchedMapBeacons.asStateFlow()

    internal val _prefetchedCommunityHubs = MutableStateFlow<List<CommunityHubNearbyDto>>(emptyList())
    val prefetchedCommunityHubs: StateFlow<List<CommunityHubNearbyDto>> = _prefetchedCommunityHubs.asStateFlow()

    /** Saved event bookmarks restored from disk for instant Home paint. */
    internal val _cachedEventBookmarks =
        MutableStateFlow<List<compose.project.click.click.data.api.EventBookmarkItemDto>>(emptyList()) // pragma: allowlist secret
    val cachedEventBookmarks:
        StateFlow<List<compose.project.click.click.data.api.EventBookmarkItemDto>> = // pragma: allowlist secret
        _cachedEventBookmarks.asStateFlow()

    /**
     * Bumped when RSVP / bookmark / check-in local caches change so Home can rebuild
     * Featured + Event reminder sections without waiting for the next prefetch.
     */
    internal val _eventEngagementVersion = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val eventEngagementVersion: SharedFlow<Unit> = _eventEngagementVersion.asSharedFlow()

    fun notifyEventEngagementChanged() {
        _eventEngagementVersion.tryEmit(Unit)
    }

    /** Home Saved Events section — keep in sync with API bookmark page size. */
    internal const val SAVED_EVENT_BOOKMARKS_LIMIT = 50

    fun updateCachedEventBookmarks(bookmarks: List<compose.project.click.click.data.api.EventBookmarkItemDto>) { // pragma: allowlist secret
        _cachedEventBookmarks.value =
            bookmarks
                .distinctBy { it.beaconId }
                .sortedByDescending { it.bookmarkedAt.orEmpty() }
                .take(SAVED_EVENT_BOOKMARKS_LIMIT)
        schedulePersistSnapshot()
    }

    /** True after the startup beacon/hub prefetch attempt finishes (success, empty, or failure). */
    internal val _discoveryMapPrefetchComplete = MutableStateFlow(false)
    val discoveryMapPrefetchComplete: StateFlow<Boolean> = _discoveryMapPrefetchComplete.asStateFlow()

    /** Last GPS fix from startup beacon prefetch — used to seed map discovery before the map tab opens. */
    internal val _lastKnownDeviceLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val lastKnownDeviceLocation: StateFlow<Pair<Double, Double>?> = _lastKnownDeviceLocation.asStateFlow()

    fun noteDeviceLocation(
        latitude: Double,
        longitude: Double,
    ) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (latitude == 0.0 && longitude == 0.0) return
        _lastKnownDeviceLocation.value = latitude to longitude
    }

    /** Radius (meters) for the eager beacon prefetch — matches the map discovery feed radius. */
    internal const val BEACON_PREFETCH_RADIUS_METERS = 50_000.0

    /** Bounded retries so the discovery feed seeds even when GPS is slow to warm up at cold start. */
    internal const val BEACON_PREFETCH_MAX_ATTEMPTS = 6
    internal const val BEACON_PREFETCH_RETRY_DELAY_MS = 4_000L

    internal var beaconPrefetchJob: Job? = null

    // Current user state
    internal val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // User's connections
    internal val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    /** Per-user archive rows ([connection_archives]); excludes these from Active surfaces. */
    internal val _archivedConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val archivedConnectionIds: StateFlow<Set<String>> = _archivedConnectionIds.asStateFlow()

    /** Per-user hidden rows ([connection_hidden]); excluded everywhere. */
    internal val _hiddenConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenConnectionIds: StateFlow<Set<String>> = _hiddenConnectionIds.asStateFlow()

    /** Per-user core pins ([connection_core]); sorted first in lists; map-visible when ghosted off-map. */
    internal val _coreConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val coreConnectionIds: StateFlow<Set<String>> = _coreConnectionIds.asStateFlow()

    /**
     * Incremented when a verified group ("click") is created elsewhere so [ConnectionsScreen]
     * can force-refresh its [ChatViewModel] chat list (separate repository instance).
     */
    internal val _chatListRefreshEpoch = MutableStateFlow(0)
    val chatListRefreshEpoch: StateFlow<Int> = _chatListRefreshEpoch.asStateFlow()

    fun bumpChatListRefresh() {
        _chatListRefreshEpoch.value = _chatListRefreshEpoch.value + 1
    }

    /**
     * Bumped after BLE/proximity handshake or reconnect encounter save so open profile
     * sheets re-fetch shared connection + timeline without requiring a cache clear.
     */
    internal val _proximityEncounterEpoch = MutableStateFlow(0L)
    val proximityEncounterEpoch: StateFlow<Long> = _proximityEncounterEpoch.asStateFlow()

    /**
     * Invalidate profile caches for affected peers, force a connections refresh, and bump
     * [proximityEncounterEpoch] so UI sheets re-load encounter timelines.
     */
    fun notifyProximityConnectionChanged(
        peerUserIds: Collection<String>,
        connectionIds: Collection<String> = emptyList(),
    ) {
        val targets =
            compose.project.click.click.viewmodel.proximityConnectionChangeTargets( // pragma: allowlist secret
                peerUserIds = peerUserIds,
                connectionIds = connectionIds,
                currentUserId = _currentUser.value?.id,
            )
        if (targets.peerUserIds.isNotEmpty()) {
            supabaseRepository.invalidateUserPublicProfiles(targets.peerUserIds)
            supabaseRepository.invalidateProfileTimelinesForPeers(targets.peerUserIds)
        }
        // connectionIds reserved for future connection-scoped timeline keys / chat targets
        @Suppress("UNUSED_VARIABLE")
        val unusedConnectionIds = targets.connectionIds
        _proximityEncounterEpoch.value = _proximityEncounterEpoch.value + 1L
        refresh(force = true)
        schedulePersistSnapshot()
    }

    // Connected users info
    internal val _connectedUsers = MutableStateFlow<Map<String, User>>(emptyMap())
    val connectedUsers: StateFlow<Map<String, User>> = _connectedUsers.asStateFlow()

    internal val _cachedChatThreads = MutableStateFlow<Map<String, CachedChatThread>>(emptyMap())
    val cachedChatThreads: StateFlow<Map<String, CachedChatThread>> = _cachedChatThreads.asStateFlow()

    internal val _cachedHubThreads = MutableStateFlow<Map<String, CachedHubThread>>(emptyMap())
    val cachedHubThreads: StateFlow<Map<String, CachedHubThread>> = _cachedHubThreads.asStateFlow()

    /**
     * Unified inbox rows persisted in [CachedAppSnapshot] for instant Clicks list on cold start
     * (includes verified group chats, not only [connections]).
     */
    internal val _inboxFeedChats = MutableStateFlow<List<ChatWithDetails>>(emptyList())
    val inboxFeedChats: StateFlow<List<ChatWithDetails>> = _inboxFeedChats.asStateFlow()

    fun persistInboxFeedChats(chats: List<ChatWithDetails>) {
        _inboxFeedChats.value = dedupeOneToOneChatsByPeer(chats)
        scope.launch { schedulePersistSnapshot() }
    }

    /** In-memory inbox row for opening a thread without refetching the full inbox. */
    fun chatInboxRowForThread(
        threadId: String,
        userId: String,
    ): ChatWithDetails? {
        if (_currentUser.value?.id != userId || threadId.isBlank()) return null
        return _inboxFeedChats.value.firstOrNull {
            it.connection.id == threadId || it.chat.id == threadId
        }
    }

    fun persistProfileTimelineCaches() {
        scope.launch { persistSnapshot() }
    }

    /** Local SSOT merge for map beacons; persisted in [CachedAppSnapshot] for offline cold start. */
    fun mergeCachedMapBeacons(incoming: List<MapBeacon>) {
        if (incoming.isEmpty()) return
        _prefetchedMapBeacons.value = mergeMapBeaconLists(_prefetchedMapBeacons.value, incoming)
        scope.launch { persistSnapshot() }
    }

    fun mergeCachedCommunityHubsFromDto(incoming: List<CommunityHubNearbyDto>) = mergeCachedCommunityHubsFromDtoImpl(incoming = incoming)

    // Current user's availability
    internal val _userAvailability = MutableStateFlow<UserAvailability?>(null)
    val userAvailability: StateFlow<UserAvailability?> = _userAvailability.asStateFlow()

    /** Cached interest tags — populated during initial app load for instant Settings render. */
    internal val _userInterestTags = MutableStateFlow<List<String>>(emptyList())
    val userInterestTags: StateFlow<List<String>> = _userInterestTags.asStateFlow()

    // Loading state - start as false, set to true in initializeData
    internal val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Data loaded flag - prevents reloading
    internal val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()

    // Last refresh time
    internal var lastRefreshTime: Long = 0
    internal const val REFRESH_COOLDOWN_MS = 30_000 // 30 seconds minimum between refreshes
    internal const val PRESENCE_HEARTBEAT_MS = 30_000L
    internal const val PENDING_SYNC_RETRY_MS = 15_000L
    internal const val STARTUP_TIMEOUT_MS = 15_000L
    internal const val FOREGROUND_RECOVERY_DEBOUNCE_MS = 900L
    internal const val CHAT_PREFETCH_LIMIT = 12
    internal const val CHAT_PREFETCH_MAX_MESSAGES = 80
    internal const val GROUP_CHAT_PREFETCH_MAX_MESSAGES = 50
    internal const val HUB_THREAD_CACHE_MAX_MESSAGES = 120
    internal const val PERSIST_SNAPSHOT_DEBOUNCE_MS = 3_000L

    internal var loadAllDataJob: Job? = null
    internal var persistSnapshotJob: Job? = null
    internal var lastForegroundRecoveryMs: Long = 0L
    internal var silentChatPrefetchCompleted = false

    // Error state
    internal val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal val _pendingConnectionsCount = MutableStateFlow(0)
    val pendingConnectionsCount: StateFlow<Int> = _pendingConnectionsCount.asStateFlow()

    internal val _proximityHandshakeRecovered =
        MutableSharedFlow<ProximityHandshakeRecoveryPayload>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val proximityHandshakeRecovered: SharedFlow<ProximityHandshakeRecoveryPayload> =
        _proximityHandshakeRecovered.asSharedFlow()

    /** One-shot UI messages (e.g. profile or notification settings save failed). */
    internal val _transientUserMessages =
        MutableSharedFlow<String>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val transientUserMessages: SharedFlow<String> = _transientUserMessages.asSharedFlow()

    /**
     * Emitted after [handleApplicationForegrounded] refreshes the Supabase session and Realtime
     * socket so [ChatViewModel] can re-attach Postgres channels without waiting for heartbeat timeouts.
     */
    internal val _foregroundRealtimeRecovery =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val foregroundRealtimeRecovery: SharedFlow<Unit> = _foregroundRealtimeRecovery.asSharedFlow()

    /**
     * Emitted after proximity / connection mutations that need a forced Clicks inbox rebuild
     * (collapse duplicate 1:1 edges) rather than trusting a stale disk feed.
     */
    internal val _inboxReloadRequests =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val inboxReloadRequests: SharedFlow<Unit> = _inboxReloadRequests.asSharedFlow()

    fun requestInboxReload() {
        scope.launch { _inboxReloadRequests.emit(Unit) }
    }

    /** Surfaces a one-shot message to UI collectors (e.g. onboarding before the main scaffold exists). */
    fun postTransientUserMessage(message: String) {
        if (message.isBlank()) return
        scope.launch { _transientUserMessages.emit(message.trim()) }
    }

    // ── Active Community Hubs (persisted across cold starts) ───
    internal val _activeHubs = MutableStateFlow<List<ActiveHubEntry>>(emptyList())
    val activeHubs: StateFlow<List<ActiveHubEntry>> = _activeHubs.asStateFlow()

    fun registerActiveHub(entry: ActiveHubEntry) {
        _activeHubs.value =
            (_activeHubs.value.filterNot { it.hubId == entry.hubId } + entry)
                .sortedByDescending { it.joinedAtMs }
        persistActiveHubs()
    }

    fun removeActiveHub(hubId: String) {
        _activeHubs.value = _activeHubs.value.filterNot { it.hubId == hubId }
        persistActiveHubs()
    }

    internal val _dismissedCommunityHubIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedCommunityHubIds: StateFlow<Set<String>> = _dismissedCommunityHubIds.asStateFlow()

    /** Removes a hub from active groups and map/discovery feeds after delete (not leave). */
    fun dismissCommunityHub(hubId: String) {
        val trimmed = hubId.trim()
        if (trimmed.isEmpty()) return
        removeActiveHub(trimmed)
        _prefetchedCommunityHubs.value = _prefetchedCommunityHubs.value.filterNot { it.hubId == trimmed }
        _dismissedCommunityHubIds.value = _dismissedCommunityHubIds.value + trimmed
    }

    fun updateActiveHubDetails(
        hubId: String,
        name: String,
        category: String,
        creatorId: String? = null,
        isEventHub: Boolean? = null,
    ) {
        val trimmedId = hubId.trim()
        if (trimmedId.isEmpty()) return
        val nextCategory = category.trim().ifBlank { null }
        _activeHubs.value =
            _activeHubs.value.map { entry ->
                if (entry.hubId != trimmedId) {
                    entry
                } else {
                    val resolvedCategory = nextCategory ?: entry.category
                    entry.copy(
                        name = name.trim().ifBlank { entry.name },
                        category = resolvedCategory,
                        creatorId = creatorId ?: entry.creatorId,
                        isEventHub =
                            isEventHub
                                ?: entry.isEventHub ||
                                resolvedCategory.equals("event", ignoreCase = true),
                    )
                }
            }
        persistActiveHubs()
    }

    // Ghost Mode state - privacy toggle to stop sharing location and halt network requests
    internal val _ghostModeEnabled = MutableStateFlow(false)
    val ghostModeEnabled: StateFlow<Boolean> = _ghostModeEnabled.asStateFlow()

    internal val _homeLayoutMode = MutableStateFlow(HomeLayoutMode.PILE)
    val homeLayoutMode: StateFlow<HomeLayoutMode> = _homeLayoutMode.asStateFlow()

    fun setHomeLayoutMode(mode: HomeLayoutMode) {
        if (_homeLayoutMode.value == mode) return
        _homeLayoutMode.value = mode
        scope.launch {
            tokenStorage.saveHomeLayoutMode(mode.name)
        }
    }

    internal val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

    // Location privacy preferences (persisted to Supabase profile)
    internal val _locationPreferences = MutableStateFlow(LocationPreferences())
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
        println(
            "AppDataManager: Ghost Mode ${if (newValue) "ENABLED - halting background sync" else "DISABLED - resuming background sync"}",
        )
    }

    /**
     * OS resumed the UI (foreground). Cancels any in-flight [loadAllData] work, drops stale Ktor /
     * Realtime sockets, refreshes the GoTrue session, and starts a fresh load without waiting for
     * [STARTUP_TIMEOUT_MS] on half-open connections (common after iOS backgrounding).
     */
    fun handleApplicationForegrounded() {
        recoverSessionAndRealtime(reason = "foreground", forceDataRefresh = false)
    }

    fun cachedChatThreadFor(threadId: String): CachedChatThread? {
        if (threadId.isBlank()) return null
        val threads = _cachedChatThreads.value
        return threads[threadId] ?: threads.values.firstOrNull { it.chatId == threadId }
    }

    fun cacheChatThread(
        connectionId: String,
        chatId: String,
        messages: List<Message>,
        participants: List<User>,
        reactions: List<MessageReaction> = emptyList(),
    ) {
        if (connectionId.isBlank() || chatId.isBlank()) return
        val boundedMessages = messages.takeLast(CHAT_PREFETCH_MAX_MESSAGES)
        val thread =
            CachedChatThread(
                connectionId = connectionId,
                chatId = chatId,
                cachedAtMs = Clock.System.now().toEpochMilliseconds(),
                messages = boundedMessages,
                participants = participants.distinctBy { it.id },
                reactions = reactions,
            )
        _cachedChatThreads.value = _cachedChatThreads.value + (connectionId to thread)
        val last = boundedMessages.lastOrNull()
        if (last != null) {
            updateConnectionChatActivity(connectionId, last.timeCreated, last)
            updateInboxFeedChatActivity(connectionId, last)
        } else {
            scope.launch { schedulePersistSnapshot() }
        }
    }

    fun cachedHubThreadFor(hubId: String): CachedHubThread? {
        if (hubId.isBlank()) return null
        return _cachedHubThreads.value[hubId]
    }

    fun cacheHubThread(
        hubId: String,
        realtimeChannel: String,
        messages: List<Message>,
        participants: List<User> = emptyList(),
    ) {
        if (hubId.isBlank() || realtimeChannel.isBlank()) return
        val boundedMessages = messages.takeLast(HUB_THREAD_CACHE_MAX_MESSAGES)
        val thread =
            CachedHubThread(
                hubId = hubId,
                realtimeChannel = realtimeChannel,
                cachedAtMs = Clock.System.now().toEpochMilliseconds(),
                messages = boundedMessages,
                participants = participants.distinctBy { it.id },
            )
        _cachedHubThreads.value = _cachedHubThreads.value + (hubId to thread)
        scope.launch { persistSnapshot() }
    }

    fun clearHubThreadCache(hubId: String) {
        if (hubId.isBlank()) return
        if (!_cachedHubThreads.value.containsKey(hubId)) return
        _cachedHubThreads.value = _cachedHubThreads.value - hubId
        scope.launch { persistSnapshot() }
    }

    fun updateInboxFeedChatActivity(
        connectionId: String,
        lastMessagePreview: Message,
    ) {
        applyInboxFeedChatActivity(connectionId, lastMessagePreview)
    }

    fun updateInboxFeedChatActivityFromPush(
        connectionId: String,
        lastMessagePreview: Message,
    ) {
        updateInboxFeedChatActivity(connectionId, lastMessagePreview)
    }

    /** Map tab or Home — prefetch nearby beacons/hubs (retries once if caches stay empty). */
    fun requestMapDiscoveryPrefetch() {
        if (_ghostModeEnabled.value) return
        if (beaconPrefetchJob?.isActive == true) return
        val hasDiscoveryCache =
            _prefetchedMapBeacons.value.isNotEmpty() ||
                _prefetchedCommunityHubs.value.isNotEmpty()
        if (beaconPrefetchedThisSession && hasDiscoveryCache) return
        if (beaconPrefetchedThisSession && discoveryPrefetchEmptyRetryUsed) return
        if (beaconPrefetchedThisSession && !hasDiscoveryCache) {
            discoveryPrefetchEmptyRetryUsed = true
        } else if (!beaconPrefetchedThisSession) {
            beaconPrefetchedThisSession = true
        }
        startBeaconPrefetch()
    }

    /**
     * Hydrate local SSOT from disk immediately after auth fast-path so the dashboard can render
     * without waiting for the first network-backed [loadAllData] pass.
     */
    suspend fun primeOfflineBootCache() {
        restoreCachedSnapshot()
        restoreActiveHubs()
        if (_currentUser.value != null) {
            _isDataLoaded.value = true
        }
    }

    /**
     * Initialize app data - call this once when the app starts.
     *
     * Disk-first [primeOfflineBootCache] may already set [_isDataLoaded]; that must NOT skip
     * pending sync, network reconnect observers, or the first network-backed [loadAllData].
     */
    fun initializeData() {
        restoreHomeLayoutMode()
        scope.launch {
            refreshPendingConnectionCount()
        }
        startPendingConnectionSync()
        startNetworkConnectivityObserver()
        // Disk prime sets isDataLoaded=true for offline UI; still run a network hydrate unless
        // one is already in flight.
        if (_isLoading.value || loadAllDataJob?.isActive == true) return
        startLoadAllDataJob()
    }

    /**
     * True after a recent [loadAllData] when local connection/inbox caches can back the Clicks list
     * without an immediate [ChatViewModel.loadChats] network round-trip.
     */
    fun isInboxFeedFresh(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        if (!_isDataLoaded.value) return false
        val inbox = _inboxFeedChats.value
        val connections = _connections.value
        // Connections alone are not a complete inbox — verified cliques only live in inboxFeedChats.
        if (inbox.isEmpty() && connections.isEmpty()) return false
        // Direct-only poison: 1:1 rows exist but no groupClique rows, and we have not successfully
        // completed a group fetch this process. Force network so Groups tab can recover.
        if (connections.isNotEmpty() &&
            inbox.none { it.groupClique != null } &&
            !groupInboxHydratedThisSession
        ) {
            return false
        }
        val currentVersion = RealtimeCoordinator.currentInboxVersion()
        if (currentVersion != lastSyncedInboxVersion) return false
        if (lastRefreshTime <= 0L) return true
        return nowMs - lastRefreshTime < REFRESH_COOLDOWN_MS
    }

    /** Set after a successful group-clique inbox fetch (including authentic empty). */
    var groupInboxHydratedThisSession: Boolean = false
        internal set

    fun markGroupInboxHydrated() {
        groupInboxHydratedThisSession = true
    }

    fun notifyInboxVersionSynced() {
        lastSyncedInboxVersion = RealtimeCoordinator.currentInboxVersion()
    }

    fun bumpInboxFromPush() {
        RealtimeCoordinator.bumpInboxVersion()
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

    suspend fun clearData() = clearDataImpl()

    /**
     * Reset all cached data and reload from server.
     * Used after login/signup to ensure connection counts,
     * chats, and other data are fetched fresh.
     */
    fun resetAndReload() {
        loadAllDataJob?.cancel()
        loadAllDataJob =
            scope.launch {
                clearData()
                loadAllData()
            }
    }

    fun addConnection(
        connection: Connection,
        otherUser: User? = null,
    ) = addConnectionImpl(connection = connection, otherUser = otherUser)

    /**
     * Remove a connection
     */
    fun removeConnection(connectionId: String) {
        _connections.value = _connections.value.filter { it.id != connectionId }
        seedJunctionCacheFromMemory()
        scope.launch {
            schedulePersistSnapshot()
        }
    }

    /** Optimistic hide: used after [connection_hidden] insert or when blocking. */
    fun hideConnectionLocally(connectionId: String) {
        _hiddenConnectionIds.value = _hiddenConnectionIds.value + connectionId
        removeConnection(connectionId)
    }

    fun unhideConnectionLocally(connectionId: String) {
        _hiddenConnectionIds.value = _hiddenConnectionIds.value - connectionId
        seedJunctionCacheFromMemory()
        scope.launch { schedulePersistSnapshot() }
    }

    /**
     * Revert an optimistic [hideConnectionLocally]: removes the ID from hidden set and
     * restores the [Connection] back into the connections list. Used when the server call
     * to [connection_hidden] fails and we need to undo the local hide.
     */
    fun revertHideConnectionLocally(
        connectionId: String,
        connection: Connection,
    ) {
        _hiddenConnectionIds.value = _hiddenConnectionIds.value - connectionId
        publishConnections(_connections.value + connection)
        seedJunctionCacheFromMemory()
        scope.launch { schedulePersistSnapshot() }
    }

    fun markConnectionArchivedLocally(connectionId: String) {
        _archivedConnectionIds.value = _archivedConnectionIds.value + connectionId
        seedJunctionCacheFromMemory()
        scope.launch { schedulePersistSnapshot() }
    }

    fun markConnectionUnarchivedLocally(connectionId: String) {
        _archivedConnectionIds.value = _archivedConnectionIds.value - connectionId
        seedJunctionCacheFromMemory()
        scope.launch { schedulePersistSnapshot() }
    }

    fun markConnectionCoreLocally(connectionId: String) {
        _coreConnectionIds.value = _coreConnectionIds.value + connectionId
        scope.launch { schedulePersistSnapshot() }
    }

    fun markConnectionNotCoreLocally(connectionId: String) {
        _coreConnectionIds.value = _coreConnectionIds.value - connectionId
        scope.launch { schedulePersistSnapshot() }
    }

    /**
     * After QR/NFC reconnect: clear local junction bookkeeping and replace the in-memory row.
     */
    fun applyRestoredConnection(connection: Connection) {
        _archivedConnectionIds.value = _archivedConnectionIds.value - connection.id
        _hiddenConnectionIds.value = _hiddenConnectionIds.value - connection.id
        publishConnections(_connections.value.filter { it.id != connection.id } + connection)
        seedJunctionCacheFromMemory()
        scope.launch { schedulePersistSnapshot() }
    }

    fun replaceLocalConnection(
        localId: String,
        syncedConnection: Connection,
        otherUser: User? = null,
    ) {
        publishConnections(
            _connections.value
                .filterNot { it.id == localId }
                .plus(syncedConnection),
        )
        if (otherUser != null) {
            _connectedUsers.value = _connectedUsers.value + (otherUser.id to otherUser)
        }
        scope.launch {
            schedulePersistSnapshot()
        }
    }

    /**
     * Patch in-memory [Connection] rows when chat activity changes so [connections] consumers
     * see fresh [Connection.last_message_at] and optional preview text without a full reload.
     */
    fun updateConnectionChatActivity(
        connectionId: String,
        lastMessageAt: Long,
        lastMessagePreview: Message? = null,
    ) {
        _connections.value =
            _connections.value.map { c ->
                if (c.id != connectionId) return@map c
                val mergedAt = listOfNotNull(c.last_message_at, lastMessageAt).maxOrNull()
                val newChat =
                    if (lastMessagePreview != null) {
                        val existingPreview = c.chat.messages.firstOrNull()
                        val shouldReplacePreview =
                            existingPreview == null ||
                                lastMessagePreview.id == existingPreview.id ||
                                lastMessagePreview.timeCreated > existingPreview.timeCreated ||
                                (
                                    lastMessagePreview.timeCreated == existingPreview.timeCreated &&
                                        lastMessagePreview.id >= existingPreview.id
                                )
                        if (shouldReplacePreview) {
                            c.chat.copy(messages = listOf(lastMessagePreview))
                        } else {
                            c.chat
                        }
                    } else {
                        c.chat
                    }
                c.copy(last_message_at = mergedAt, chat = newChat)
            }
        scope.launch { schedulePersistSnapshot() }
    }

    fun setPendingConnectionsCount(count: Int) {
        _pendingConnectionsCount.value = count
    }

    suspend fun refreshPendingConnectionCount() {
        val queueJson = tokenStorage.getPendingConnectionQueue()
        _pendingConnectionsCount.value =
            runCatching {
                if (queueJson.isNullOrBlank()) {
                    0
                } else {
                    json.decodeFromString<List<PendingConnectionDraft>>(queueJson).size
                }
            }.getOrElse { 0 }
    }

    fun setEventReminderNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(eventReminderPushEnabled = enabled),
        )
    }

    fun setAvailabilityMatchNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(availabilityMatchPushEnabled = enabled),
        )
    }

    fun setHubMessageNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(hubMessagePushEnabled = enabled),
        )
    }

    fun setEventTeaserNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(eventTeaserPushEnabled = enabled),
        )
    }

    fun setReconnectNudgeNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(reconnectNudgePushEnabled = enabled),
        )
    }

    fun setMessageNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(messagePushEnabled = enabled),
        )
    }

    fun setCallNotificationsEnabled(enabled: Boolean) {
        updateNotificationPreferences(
            _notificationPreferences.value.copy(callPushEnabled = enabled),
        )
    }

    /**
     * Get a connected user by their ID
     */
    fun getConnectedUser(userId: String): User? = _connectedUsers.value[userId]

    /**
     * Update user availability
     */
    fun updateUserAvailability(availability: UserAvailability) {
        _userAvailability.value = availability
    }

    fun toggleFreeThisWeek() = toggleFreeThisWeekImpl()

    /**
     * Get a specific connection by ID
     */
    fun getConnection(connectionId: String): Connection? = _connections.value.find { it.id == connectionId }

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
            schedulePersistSnapshot()
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

    fun updateProfileName(
        firstName: String,
        lastName: String,
    ) = updateProfileNameImpl(firstName = firstName, lastName = lastName)

    /**
     * Updates the in-memory current user avatar URL after a successful storage upload + DB update.
     */
    fun applyProfilePictureUrl(publicUrl: String) {
        val latest = _currentUser.value ?: return
        _currentUser.value = latest.copy(image = publicUrl)
        scope.launch {
            runCatching { schedulePersistSnapshot() }
                .onFailure { println("applyProfilePictureUrl: snapshot failed: ${it.message}") }
        }
    }

    /** Updates in-memory interest tags after settings save (Common Ground / profile). */
    fun applyInterestTags(tags: List<String>) {
        val latest = _currentUser.value ?: return
        _currentUser.value = latest.copy(tags = tags)
        _userInterestTags.value = tags
        scope.launch {
            runCatching { schedulePersistSnapshot() }
                .onFailure { println("applyInterestTags: snapshot failed: ${it.message}") }
        }
    }

    fun applyPersonalityTags(tags: List<String>) {
        val latest = _currentUser.value ?: return
        _currentUser.value = latest.copy(personalityTags = tags)
        scope.launch {
            runCatching { schedulePersistSnapshot() }
                .onFailure { println("applyPersonalityTags: snapshot failed: ${it.message}") }
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
                    pendingHandshakeId = proximity.pendingHandshakeId,
                    isAggregateNewConnection = proximity.isAggregateNewConnection,
                ),
            )
        }
    }
}
