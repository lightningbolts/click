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
import compose.project.click.click.data.models.isPendingSync // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.proximity.ProximityManager // pragma: allowlist secret
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

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Loading : ConnectionState()
    data class Success(val connection: Connection, val connectedUser: User) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    object ProximityFetchingLocation : ConnectionState()
    object ProximityHandshaking : ConnectionState()
    data class PendingConfirmation(val users: List<User>) : ConnectionState()
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
                    repository.bindProximityHandshake(
                        httpClient = httpClient,
                        bearerJwt = jwt,
                        myToken = myToken,
                        heardTokens = heardTokens,
                        latitude = lastProximityLat,
                        longitude = lastProximityLng,
                    )
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
                        _connectionState.value = ConnectionState.Error(e.message ?: "Proximity handshake failed")
                    },
                )
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Proximity handshake failed")
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
}
