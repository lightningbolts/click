package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.nfc.NfcManager
import compose.project.click.click.nfc.NfcState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class NfcConnectionState {
    object Idle : NfcConnectionState()
    object Scanning : NfcConnectionState()
    object Sending : NfcConnectionState()
    data class UserDetected(val userId: String, val userName: String?) : NfcConnectionState()
    data class CreatingConnection(val otherUserId: String) : NfcConnectionState()
    data class Success(val connection: Connection) : NfcConnectionState()
    data class Error(val message: String) : NfcConnectionState()
}

class NfcViewModel(
    private val nfcManager: NfcManager,
    private val apiClient: ApiClient = ApiClient()
) : ViewModel() {

    private val _connectionState = MutableStateFlow<NfcConnectionState>(NfcConnectionState.Idle)
    val connectionState: StateFlow<NfcConnectionState> = _connectionState.asStateFlow()

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private var currentUserId: String? = null
    private var authToken: String? = null

    init {
        // Observe NFC state changes
        viewModelScope.launch {
            nfcManager.nfcState.collect { nfcState ->
                handleNfcStateChange(nfcState)
            }
        }
    }

    fun setCurrentUser(userId: String, token: String) {
        currentUserId = userId
        authToken = token
    }

    fun setCurrentLocation(latitude: Double, longitude: Double) {
        _currentLocation.value = Pair(latitude, longitude)
    }

    fun isNfcAvailable(): Boolean {
        return nfcManager.isNfcAvailable()
    }

    fun isNfcEnabled(): Boolean {
        return nfcManager.isNfcEnabled()
    }

    fun startScanning() {
        val userId = currentUserId
        if (userId == null) {
            _connectionState.value = NfcConnectionState.Error("User not logged in")
            return
        }

        if (!nfcManager.isNfcAvailable()) {
            _connectionState.value = NfcConnectionState.Error("NFC not available on this device")
            return
        }

        if (!nfcManager.isNfcEnabled()) {
            _connectionState.value = NfcConnectionState.Error("Please enable NFC in settings")
            return
        }

        _connectionState.value = NfcConnectionState.Scanning
        nfcManager.startNfcReader(userId)
    }

    fun stopScanning() {
        nfcManager.stopNfcReader()
        _connectionState.value = NfcConnectionState.Idle
    }

    fun openNfcSettings() {
        nfcManager.openNfcSettings()
    }

    fun createConnection(otherUserId: String) {
        val userId = currentUserId
        val token = authToken
        val location = _currentLocation.value

        if (userId == null || token == null) {
            _connectionState.value = NfcConnectionState.Error("User not logged in")
            return
        }

        _connectionState.value = NfcConnectionState.CreatingConnection(otherUserId)

        viewModelScope.launch {
            try {
                val result = apiClient.createConnection(
                    authToken = token,
                    user1Id = userId,
                    user2Id = otherUserId,
                    latitude = location?.first ?: 0.0,
                    longitude = location?.second ?: 0.0
                )

                result.fold(
                    onSuccess = { connection ->
                        _connectionState.value = NfcConnectionState.Success(connection)
                        stopScanning()
                    },
                    onFailure = { error ->
                        _connectionState.value = NfcConnectionState.Error(
                            "Failed to create connection: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _connectionState.value = NfcConnectionState.Error(
                    "Failed to create connection: ${e.message}"
                )
            }
        }
    }

    fun resetState() {
        _connectionState.value = NfcConnectionState.Idle
    }

    private fun handleNfcStateChange(nfcState: NfcState) {
        when (nfcState) {
            is NfcState.Idle -> {
                if (_connectionState.value !is NfcConnectionState.Success) {
                    _connectionState.value = NfcConnectionState.Idle
                }
            }
            is NfcState.Scanning -> {
                _connectionState.value = NfcConnectionState.Scanning
            }
            is NfcState.Sending -> {
                _connectionState.value = NfcConnectionState.Sending
            }
            is NfcState.DataReceived -> {
                _connectionState.value = NfcConnectionState.UserDetected(
                    nfcState.userId,
                    nfcState.userName
                )
            }
            is NfcState.Success -> {
                // NFC exchange was successful, now create the connection
                createConnection(nfcState.otherUserId)
            }
            is NfcState.Error -> {
                _connectionState.value = NfcConnectionState.Error(nfcState.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        nfcManager.stopNfcReader()
        apiClient.close()
    }
}

