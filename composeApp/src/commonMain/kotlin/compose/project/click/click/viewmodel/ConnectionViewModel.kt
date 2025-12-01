package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ConnectionRepository

package compose.project.click.click.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.ConnectionRepository
import compose.project.click.click.models.Connection
import compose.project.click.click.models.ConnectionRequest
import compose.project.click.click.models.User
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
     */
    fun connectWithUser(
        scannedUserId: String,
        currentUserId: String,
        latitude: Double? = null,
        longitude: Double? = null
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

                // Create connection request
                val request = ConnectionRequest(
                    userId1 = currentUserId,
                    userId2 = scannedUserId,
                    locationLat = latitude,
                    locationLng = longitude
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