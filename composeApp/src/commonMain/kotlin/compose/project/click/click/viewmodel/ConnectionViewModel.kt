package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
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

    private val _userConnections = MutableStateFlow<List<Connection>>(emptyList())
    val userConnections: StateFlow<List<Connection>> = _userConnections.asStateFlow()

    /**
     * Connect with a user via QR code
     * @param scannedUserId The ID of the user being connected with
     * @param currentUserId The current user's ID
     * @param latitude Optional latitude of the connection location
     * @param longitude Optional longitude of the connection location
     * @param contextTag Optional user-defined tag like "Met at Dawg Daze"
     */
    fun connectWithUser(
        scannedUserId: String,
        currentUserId: String,
        latitude: Double? = null,
        longitude: Double? = null,
        contextTag: String? = null
    ) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading

            try {
                // Validate that user isn't trying to connect with themselves
                if (scannedUserId == currentUserId) {
                    _connectionState.value = ConnectionState.Error("You cannot connect with yourself!")
                    return@launch
                }

                // Get the scanned user's info
                val userResult = repository.getUserById(scannedUserId)
                if (userResult.isFailure) {
                    _connectionState.value = ConnectionState.Error("User not found")
                    return@launch
                }
                val scannedUser = userResult.getOrNull()!!

                // Create connection request with context tag
                val request = ConnectionRequest(
                    userId1 = currentUserId,
                    userId2 = scannedUserId,
                    locationLat = latitude,
                    locationLng = longitude,
                    contextTag = contextTag
                )

                // Create the connection
                val result = repository.createConnection(request)

                if (result.isSuccess) {
                    val connection = result.getOrNull()!!
                    _connectionState.value = ConnectionState.Success(connection, scannedUser)

                    // Refresh user's connections list
                    loadUserConnections(currentUserId)
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
     * Load all connections for a user
     */
    fun loadUserConnections(userId: String) {
        viewModelScope.launch {
            val result = repository.getUserConnections(userId)
            if (result.isSuccess) {
                _userConnections.value = result.getOrNull() ?: emptyList()
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