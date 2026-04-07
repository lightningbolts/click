package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.NoiseLevelCategory
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.isPendingSync
import compose.project.click.click.data.repository.ConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Loading : ConnectionState()
    data class Success(val connection: Connection, val connectedUser: User) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ConnectionViewModel : ViewModel() {
    private val repository = ConnectionRepository()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Shared connection rows from [AppDataManager] (`MutableStateFlow` backed).
     * Screens such as [compose.project.click.click.ui.screens.ConnectionsScreen] use [ChatViewModel.chatListState]
     * for chat previews; use this flow when you only need raw [Connection] rows.
     */
    val userConnections: StateFlow<List<Connection>> = AppDataManager.connections

    /** Alias for callers that expect a `connections` name (same backing flow as [userConnections]). */
    val connections: StateFlow<List<Connection>> = userConnections

    /** Call when starting a connection so UI can show loading before network work begins. */
    fun markConnecting() {
        _connectionState.value = ConnectionState.Loading
    }

    /**
     * Connect with a user via QR code scan.
     *
     * @param scannedUserId The ID of the user being connected with
     * @param currentUserId The current user's ID
     * @param latitude Optional latitude of the connection location
     * @param longitude Optional longitude of the connection location
     * @param contextTag Optional user-defined tag like "Met at Dawg Daze"
     * @param connectionMethod "qr" or "nfc"
     * @param tokenAgeMs Milliseconds since QR token was created (legacy/fallback only)
     * @param qrToken Single-use token from the current QR format
     */
    fun connectWithUser(
        scannedUserId: String,
        currentUserId: String,
        latitude: Double? = null,
        longitude: Double? = null,
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
        exactNoiseLevelDb: Double? = null
    ) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading

            try {
                // Validate that user isn't trying to connect with themselves
                if (scannedUserId == currentUserId) {
                    _connectionState.value = ConnectionState.Error("You cannot connect with yourself!")
                    return@launch
                }

                // Create connection request with proximity signals
                val request = ConnectionRequest(
                    userId1 = currentUserId,
                    userId2 = scannedUserId,
                    locationLat = latitude,
                    locationLng = longitude,
                    altitudeMeters = altitudeMeters,
                    heightCategory = heightCategory,
                    exactBarometricElevationMeters = exactBarometricElevationMeters,
                    exactBarometricPressureHpa = exactBarometricPressureHpa,
                    contextTag = contextTagObject?.label ?: contextTag,
                    contextTagObject = contextTagObject,
                    connectionMethod = connectionMethod,
                    tokenAgeMs = tokenAgeMs,
                    qrToken = qrToken,
                    initiatorId = if (connectionMethod == "qr") scannedUserId else null,
                    responderId = if (connectionMethod == "qr") currentUserId else null,
                    noiseLevelCategory = noiseLevelCategory,
                    exactNoiseLevelDb = exactNoiseLevelDb
                )

                // Create the connection (off main thread so the UI frame isn’t blocked)
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

                    // Add to AppDataManager to update all screens immediately
                    AppDataManager.addConnection(connection, connectedUser)

                    // Force a full refresh so connections screen picks up the new connection
                    // This also updates the connected users map
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

    /**
     * Reset connection state
     */
    fun resetConnectionState() {
        _connectionState.value = ConnectionState.Idle
    }
}