package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.NoiseLevelCategory
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ConnectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // Use AppDataManager for connections to avoid reloading
    val userConnections: StateFlow<List<Connection>> = AppDataManager.connections

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
        contextTag: String? = null,
        contextTagObject: ContextTag? = null,
        connectionMethod: String = "qr",
        tokenAgeMs: Long? = null,
        qrToken: String? = null,
        noiseLevelCategory: NoiseLevelCategory? = null
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
                    contextTag = contextTagObject?.label ?: contextTag,
                    contextTagObject = contextTagObject,
                    connectionMethod = connectionMethod,
                    tokenAgeMs = tokenAgeMs,
                    qrToken = qrToken,
                    initiatorId = if (connectionMethod == "qr") scannedUserId else null,
                    responderId = if (connectionMethod == "qr") currentUserId else null,
                    noiseLevelCategory = noiseLevelCategory
                )

                // Create the connection
                val result = repository.createConnection(request)

                if (result.isSuccess) {
                    val connection = result.getOrNull()!!
                    val connectedUserId = connection.user_ids.firstOrNull { it != currentUserId } ?: scannedUserId
                    val connectedUser = repository.getUserById(connectedUserId).getOrElse {
                        User(id = connectedUserId, name = "Connection", createdAt = 0L)
                    }
                    _connectionState.value = ConnectionState.Success(connection, connectedUser)

                    // Add to AppDataManager to update all screens immediately
                    AppDataManager.addConnection(connection, connectedUser)

                    // Force a full refresh so connections screen picks up the new connection
                    // This also updates the connected users map
                    AppDataManager.refresh(force = true)
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