package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionRequest // pragma: allowlist secret
import compose.project.click.click.data.models.ContextTag // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.isPendingSync // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.isRetryableForProximityBind // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import compose.project.click.click.proximity.ProximityManager // pragma: allowlist secret
import compose.project.click.click.proximity.scheduleProximityHandshakeSync // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import io.ktor.client.HttpClient // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Loading : ConnectionState()
    data class Success(val connection: Connection, val connectedUser: User) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    object ProximityFetchingLocation : ConnectionState()
    object ProximityHandshaking : ConnectionState()
    data class PendingConfirmation(val users: List<User>) : ConnectionState()
    /**
     * Tri-factor tokens are stored locally; [bind-proximity-connection] will run when the device is online again.
     */
    data class ProximityCapturedOfflineSyncing(
        val message: String = "Connection Captured. Syncing when online...",
    ) : ConnectionState()
    /**
     * Proximity group (or single peer) connections are created; user is adding subjective context tags
     * to be fanned out to every [newConnections] row.
     */
    data class TaggingContext(
        val newConnections: List<Connection>,
        val targetUsers: List<UserProfile>,
    ) : ConnectionState()
}

class ConnectionViewModel : ViewModel() {
    private val repository = ConnectionRepository()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var lastProximityLat: Double? = null
    private var lastProximityLng: Double? = null
    private var lastProximityAltitudeMeters: Double? = null

    /**
     * Shared connection rows from [AppDataManager] (`MutableStateFlow` backed).
     * Screens such as [compose.project.click.click.ui.screens.ConnectionsScreen] use [ChatViewModel.chatListState]
     * for chat previews; use this flow when you only need raw [Connection] rows.
     * Excludes server-archived and removed rows so counts match the active map/home surfaces.
     */
    val userConnections: StateFlow<List<Connection>> = combine(
        AppDataManager.connections,
        AppDataManager.archivedConnectionIds,
        AppDataManager.hiddenConnectionIds,
    ) { connections, archived, hidden ->
        connections.filter { it.isActiveForUser(archived, hidden) }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Alias for callers that expect a `connections` name (same backing flow as [userConnections]). */
    val connections: StateFlow<List<Connection>> = userConnections

    /** Call when starting a connection so UI can show loading before network work begins. */
    fun markConnecting() {
        _connectionState.value = ConnectionState.Loading
    }

    /**
     * Tri-factor tap flow: GPS → concurrent BLE broadcast + 3s listen → server clustering → [PendingConfirmation].
     */
    fun startTapProximityHandshake(
        httpClient: HttpClient,
        proximityManager: ProximityManager,
        jwt: String,
        currentUserId: String,
        locationService: LocationService,
        skipLocation: Boolean,
    ) {
        if (currentUserId.isBlank()) {
            _connectionState.value = ConnectionState.Error("User not logged in")
            return
        }
        if (jwt.isBlank()) {
            _connectionState.value = ConnectionState.Error("Please sign in again.")
            return
        }
        if (!proximityManager.supportsTapExchange()) {
            _connectionState.value = ConnectionState.Error(proximityManager.capabilityNote())
            return
        }
        viewModelScope.launch {
            try {
                val shouldFetchLocation = !skipLocation && AppDataManager.shouldCaptureLocationAtTap()
                val location = if (shouldFetchLocation) {
                    _connectionState.value = ConnectionState.ProximityFetchingLocation
                    if (!locationService.hasLocationPermission()) {
                        locationService.requestLocationPermission()
                        delay(800L)
                    }
                    runCatching { locationService.getHighAccuracyLocation(5000L) }.getOrNull()
                } else {
                    null
                }
                lastProximityLat = location?.latitude
                lastProximityLng = location?.longitude
                lastProximityAltitudeMeters = location?.altitudeMeters

                val myToken = (0..9999).random().toString().padStart(4, '0')
                _connectionState.value = ConnectionState.ProximityHandshaking

                val heardTokens = coroutineScope {
                    val listen = async { proximityManager.startHandshakeListening() }
                    delay(120L)
                    proximityManager.startHandshakeBroadcast(myToken)
                    val heard = listen.await()
                    proximityManager.stopAll()
                    heard
                }

                _connectionState.value = ConnectionState.Loading
                val bindResult = withContext(Dispatchers.Default) {
                    runCatching {
                        withTimeout(22_000L) {
                            repository.bindProximityHandshake(
                                httpClient = httpClient,
                                bearerJwt = jwt,
                                myToken = myToken,
                                heardTokens = heardTokens,
                                latitude = lastProximityLat,
                                longitude = lastProximityLng,
                            ).getOrThrow()
                        }
                    }
                }

                bindResult.fold(
                    onSuccess = { users ->
                        if (users.isEmpty()) {
                            _connectionState.value = ConnectionState.Error("No nearby tap detected. Try again closer together.")
                        } else {
                            _connectionState.value = ConnectionState.PendingConfirmation(users)
                        }
                    },
                    onFailure = { e ->
                        if (e.isRetryableForProximityBind()) {
                            repository.enqueuePendingProximityHandshake(
                                myToken = myToken,
                                heardTokens = heardTokens,
                                latitude = lastProximityLat,
                                longitude = lastProximityLng,
                                altitudeMeters = lastProximityAltitudeMeters,
                            )
                            scheduleProximityHandshakeSync()
                            _connectionState.value = ConnectionState.ProximityCapturedOfflineSyncing()
                        } else {
                            _connectionState.value = ConnectionState.Error(e.message ?: "Proximity handshake failed")
                        }
                    },
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Proximity handshake failed")
            }
        }
    }

    /**
     * When [AppDataManager] / WorkManager finishes a deferred bind, resume the confirm step.
     */
    fun onProximityHandshakeRecoveredFromBackground(users: List<User>) {
        if (users.isEmpty()) return
        val cur = _connectionState.value
        if (cur is ConnectionState.ProximityCapturedOfflineSyncing || cur is ConnectionState.Idle) {
            _connectionState.value = ConnectionState.PendingConfirmation(users)
        }
    }

    /** Manual retry from the offline-captured UI. */
    fun tryFlushPendingProximityHandshakes(jwt: String) {
        if (jwt.isBlank()) {
            _connectionState.value = ConnectionState.Error("Please sign in again.")
            return
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading
            val r = withContext(Dispatchers.Default) {
                repository.syncPendingProximityHandshakes(jwt)
            }
            when {
                !r.recoveredUsers.isNullOrEmpty() ->
                    _connectionState.value = ConnectionState.PendingConfirmation(r.recoveredUsers)
                r.remainingInQueue > 0 ->
                    _connectionState.value = ConnectionState.ProximityCapturedOfflineSyncing()
                else ->
                    _connectionState.value = ConnectionState.Idle
            }
        }
    }

    /**
     * Connect with a user via QR code scan or confirmed proximity match.
     *
     * @param connectionMethod "qr", "proximity", or legacy "nfc"
     * @param initiatorId When null, derived for qr / proximity / nfc from [scannedUserId] / [currentUserId].
     */
    fun connectWithUser(
        scannedUserId: String,
        currentUserId: String,
        latitude: Double? = null,
        longitude: Double? = null,
        venueId: String? = null,
        altitudeMeters: Double? = null,
        heightCategory: HeightCategory? = null,
        exactBarometricElevationMeters: Double? = null,
        exactBarometricPressureHpa: Double? = null,
        contextTag: String? = null,
        contextTagObject: ContextTag? = null,
        connectionMethod: String = "qr",
        tokenAgeMs: Long? = null,
        qrToken: String? = null,
        noiseLevelCategory: NoiseLevelCategory? = null,
        exactNoiseLevelDb: Double? = null,
        initiatorId: String? = null,
        responderId: String? = null,
    ) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading

            try {
                if (scannedUserId == currentUserId) {
                    _connectionState.value = ConnectionState.Error("You cannot connect with yourself!")
                    return@launch
                }

                val resolvedInitiator = initiatorId ?: when (connectionMethod) {
                    "qr" -> scannedUserId
                    "proximity", "nfc" -> scannedUserId
                    else -> null
                }
                val resolvedResponder = responderId ?: when (connectionMethod) {
                    "qr" -> currentUserId
                    "proximity", "nfc" -> currentUserId
                    else -> null
                }

                val locLat = latitude ?: lastProximityLat
                val locLng = longitude ?: lastProximityLng
                val locAlt = altitudeMeters ?: lastProximityAltitudeMeters

                val request = ConnectionRequest(
                    userId1 = currentUserId,
                    userId2 = scannedUserId,
                    locationLat = locLat,
                    locationLng = locLng,
                    venueId = venueId,
                    altitudeMeters = locAlt,
                    heightCategory = heightCategory,
                    exactBarometricElevationMeters = exactBarometricElevationMeters,
                    exactBarometricPressureHpa = exactBarometricPressureHpa,
                    contextTag = contextTagObject?.label ?: contextTag,
                    contextTagObject = contextTagObject,
                    connectionMethod = connectionMethod,
                    tokenAgeMs = tokenAgeMs,
                    qrToken = qrToken,
                    initiatorId = resolvedInitiator,
                    responderId = resolvedResponder,
                    noiseLevelCategory = noiseLevelCategory,
                    exactNoiseLevelDb = exactNoiseLevelDb
                )

                val result = withContext(Dispatchers.Default) {
                    repository.createConnection(request)
                }

                if (result.isSuccess) {
                    val connection = result.getOrNull()!!
                    val connectedUserId = connection.user_ids.firstOrNull { it != currentUserId } ?: scannedUserId
                    val connectedUser = withContext(Dispatchers.Default) {
                        repository.getUserById(connectedUserId)
                    }.getOrElse {
                        User(id = connectedUserId, name = "Connection", createdAt = 0L)
                    }
                    _connectionState.value = ConnectionState.Success(connection, connectedUser)

                    AppDataManager.addConnection(connection, connectedUser)

                    if (!connection.isPendingSync()) {
                        AppDataManager.refresh(force = true)
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to create connection"
                    _connectionState.value = ConnectionState.Error(error)
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetConnectionState() {
        _connectionState.value = ConnectionState.Idle
    }

    /**
     * After the user confirms the proximity match list, create one connection per peer (1-to-1 edges),
     * then move to [ConnectionState.TaggingContext] so [saveContextTags] can fan out tags.
     */
    fun confirmProximityConnection(peerUsers: List<User>, currentUserId: String) {
        val peers = peerUsers.filter { it.id.isNotBlank() && it.id != currentUserId }.distinctBy { it.id }
        if (peers.isEmpty()) {
            _connectionState.value = ConnectionState.Error("No users to connect with")
            return
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading
            try {
                val created = mutableListOf<Connection>()
                for (peer in peers) {
                    val request = ConnectionRequest(
                        userId1 = currentUserId,
                        userId2 = peer.id,
                        locationLat = lastProximityLat,
                        locationLng = lastProximityLng,
                        altitudeMeters = lastProximityAltitudeMeters,
                        contextTag = null,
                        contextTagObject = null,
                        connectionMethod = "proximity",
                        initiatorId = peer.id,
                        responderId = currentUserId,
                    )
                    val result = withContext(Dispatchers.Default) {
                        repository.createConnection(request)
                    }
                    if (result.isFailure) {
                        _connectionState.value = ConnectionState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to create connection"
                        )
                        return@launch
                    }
                    val connection = result.getOrNull()!!
                    created.add(connection)
                    AppDataManager.addConnection(connection, peer)
                }
                if (!created.any { it.isPendingSync() }) {
                    AppDataManager.refresh(force = true)
                }
                val profiles = peers.map { it.toUserProfile() }
                _connectionState.value = ConnectionState.TaggingContext(
                    newConnections = created,
                    targetUsers = profiles,
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Apply the same subjective [contextTag] (and optional sensor enrichment) to every connection
     * in the current [ConnectionState.TaggingContext], then surface [ConnectionState.Success].
     */
    fun saveContextTags(
        contextTag: ContextTag?,
        noiseLevelCategory: NoiseLevelCategory?,
        exactNoiseLevelDb: Double?,
        heightCategory: HeightCategory?,
        exactBarometricElevationMeters: Double?,
    ) {
        val tagging = _connectionState.value as? ConnectionState.TaggingContext ?: return
        viewModelScope.launch {
            val connections = tagging.newConnections
            val targetProfiles = tagging.targetUsers
            try {
                for (connection in connections) {
                    if (connection.isPendingSync()) continue
                    val patch = withContext(Dispatchers.Default) {
                        repository.updateConnectionTags(
                            connectionId = connection.id,
                            contextTag = contextTag,
                            noiseLevelCategory = noiseLevelCategory,
                            exactNoiseLevelDb = exactNoiseLevelDb,
                            heightCategory = heightCategory,
                            exactBarometricElevationMeters = exactBarometricElevationMeters,
                        )
                    }
                    if (patch.isFailure) {
                        _connectionState.value = ConnectionState.Error(
                            patch.exceptionOrNull()?.message ?: "Failed to save context"
                        )
                        return@launch
                    }
                }
                if (!connections.any { it.isPendingSync() }) {
                    AppDataManager.refresh(force = true)
                }
                val selfId = AppDataManager.currentUser.value?.id
                if (selfId != null && targetProfiles.size >= 1) {
                    val memberUserIds = (listOf(selfId) + targetProfiles.map { it.id }).distinct().sorted()
                    if (memberUserIds.size >= 2) {
                        val selfProfile = AppDataManager.currentUser.value?.toUserProfile()
                        val nameParts = if (selfProfile != null) {
                            (listOf(selfProfile) + targetProfiles).distinctBy { it.id }.sortedBy { it.id }
                        } else {
                            targetProfiles.distinctBy { it.id }.sortedBy { it.id }
                        }
                        val initialGroupName = nameParts.joinToString(", ") { p ->
                            p.displayName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() }
                                ?: p.displayName.trim().ifBlank { "Friend" }
                        }
                        val chatRepo = SupabaseChatRepository(tokenStorage = createTokenStorage())
                        val auto = VerifiedCliqueCreation.createVerifiedCliqueWithWrappedKeys(
                            chatRepository = chatRepo,
                            connections = AppDataManager.connections.value,
                            currentUserId = selfId,
                            memberUserIds = memberUserIds,
                            initialGroupName = initialGroupName,
                        )
                        val created = auto.getOrNull()
                        if (created != null) {
                            val chatId = chatRepo.resolveChatIdForGroupId(created.groupId)
                            if (chatId != null) {
                                chatRepo.cacheGroupMasterKey(chatId, created.masterKey32)
                            }
                            AppDataManager.bumpChatListRefresh()
                        }
                    }
                }
                val primary = connections.first()
                val summaryUser = syntheticUserForProximitySuccess(targetProfiles)
                _connectionState.value = ConnectionState.Success(primary, summaryUser)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun syntheticUserForProximitySuccess(profiles: List<UserProfile>): User {
        val label = when (profiles.size) {
            0 -> "Connection"
            1 -> profiles.first().displayName
            2 -> "${profiles[0].displayName} and ${profiles[1].displayName}"
            else -> "${profiles[0].displayName}, ${profiles[1].displayName} +${profiles.size - 2} more"
        }
        val primaryId = profiles.firstOrNull()?.id ?: ""
        val primaryImage = profiles.firstOrNull()?.avatarUrl
        return User(
            id = primaryId,
            name = label,
            image = primaryImage,
            createdAt = 0L,
        )
    }
}
