package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ConnectionRepository
import compose.project.click.click.nfc.NfcManager
import compose.project.click.click.nfc.NfcState
import compose.project.click.click.utils.LocationResult
import compose.project.click.click.utils.LocationService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class NfcConnectionState {
    object Idle : NfcConnectionState()
    object FetchingLocation : NfcConnectionState()
    object Scanning : NfcConnectionState()
    object Sending : NfcConnectionState()
    data class UserDetected(val userId: String, val userName: String?) : NfcConnectionState()
    data class CreatingConnection(val otherUserId: String) : NfcConnectionState()
    data class Success(val connection: Connection, val connectedUser: User?) : NfcConnectionState()
    data class Error(val message: String) : NfcConnectionState()
}

class NfcViewModel(
    private val nfcManager: NfcManager,
    private val locationService: LocationService = LocationService(),
    private val repository: ConnectionRepository = ConnectionRepository()
) : ViewModel() {

    private val _connectionState = MutableStateFlow<NfcConnectionState>(NfcConnectionState.Idle)
    val connectionState: StateFlow<NfcConnectionState> = _connectionState.asStateFlow()

    private val _currentLocation = MutableStateFlow<LocationResult?>(null)
    val currentLocation: StateFlow<LocationResult?> = _currentLocation.asStateFlow()

    private var currentUserId: String? = null

    init {
        // Observe NFC state changes
        viewModelScope.launch {
            nfcManager.nfcState.collect { nfcState ->
                handleNfcStateChange(nfcState)
            }
        }
    }

    fun setCurrentUser(userId: String) {
        currentUserId = userId
    }

    fun isNfcAvailable(): Boolean {
        return nfcManager.isNfcAvailable()
    }

    fun isNfcEnabled(): Boolean {
        return nfcManager.isNfcEnabled()
    }

    /**
     * Fetch the device's real GPS coordinates, then start NFC scanning.
     * Shows a "Fetching Location..." state while resolving GPS.
     */
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

        // First, fetch the real location
        _connectionState.value = NfcConnectionState.FetchingLocation

        viewModelScope.launch {
            try {
                if (!locationService.hasLocationPermission()) {
                    locationService.requestLocationPermission()
                    // Give a moment for permission dialog, then try fetching
                    kotlinx.coroutines.delay(1000)
                }

                val location = locationService.getCurrentLocation()
                _currentLocation.value = location

                if (location == null) {
                    println("NfcViewModel: Could not get GPS, proceeding with null location")
                }

                // Now start the NFC scan regardless of location result
                _connectionState.value = NfcConnectionState.Scanning
                nfcManager.startNfcReader(userId)
            } catch (e: Exception) {
                println("NfcViewModel: Location error: ${e.message}")
                // Don't block on location failure — proceed without location
                _connectionState.value = NfcConnectionState.Scanning
                nfcManager.startNfcReader(userId)
            }
        }
    }

    fun stopScanning() {
        nfcManager.stopNfcReader()
        _connectionState.value = NfcConnectionState.Idle
    }

    fun openNfcSettings() {
        nfcManager.openNfcSettings()
    }

    /**
     * Create a connection using the Supabase ConnectionRepository directly.
     * This ensures the contextTag, location, and all fields are persisted correctly.
     */
    fun createConnection(otherUserId: String, contextTag: String? = null) {
        val userId = currentUserId

        if (userId == null) {
            _connectionState.value = NfcConnectionState.Error("User not logged in")
            return
        }

        if (userId == otherUserId) {
            _connectionState.value = NfcConnectionState.Error("You cannot connect with yourself!")
            return
        }

        _connectionState.value = NfcConnectionState.CreatingConnection(otherUserId)

        viewModelScope.launch {
            try {
                val location = _currentLocation.value

                // Get the other user's info
                val userResult = repository.getUserById(otherUserId)
                val otherUser = userResult.getOrNull()

                // Create connection request
                val request = ConnectionRequest(
                    userId1 = userId,
                    userId2 = otherUserId,
                    locationLat = location?.latitude,
                    locationLng = location?.longitude,
                    contextTag = contextTag
                )

                val result = repository.createConnection(request)

                result.fold(
                    onSuccess = { connection ->
                        _connectionState.value = NfcConnectionState.Success(connection, otherUser)
                        // Add to AppDataManager to update all screens
                        AppDataManager.addConnection(connection)
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
    }
}
