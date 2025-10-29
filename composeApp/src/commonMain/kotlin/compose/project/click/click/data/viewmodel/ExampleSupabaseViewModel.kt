package compose.project.click.click.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ExampleSupabaseViewModel : ViewModel() {

    private val repository = SupabaseRepository()

    // State flows for UI
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _userConnections = MutableStateFlow<List<Connection>>(emptyList())
    val userConnections: StateFlow<List<Connection>> = _userConnections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun fetchUser(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val user = repository.fetchUserByName(name)
                _currentUser.value = user

                if (user == null) {
                    _error.value = "User not found"
                }
            } catch (e: Exception) {
                _error.value = "Error fetching user: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createUser(name: String, email: String, imageUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val newUser = User(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    email = email,
                    image = imageUrl,
                    shareKey = System.currentTimeMillis() * (0..1000000).random().toLong(),
                    connections = emptyList(),
                    createdAt = System.currentTimeMillis()
                )

                val createdUser = repository.createUser(newUser)
                if (createdUser != null) {
                    _currentUser.value = createdUser
                } else {
                    _error.value = "Failed to create user"
                }
            } catch (e: Exception) {
                _error.value = "Error creating user: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadUserConnections(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val connections = repository.fetchUserConnections(userId)
                _userConnections.value = connections
            } catch (e: Exception) {
                _error.value = "Error loading connections: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createConnection(
        user1Id: String,
        user2Id: String,
        latitude: Double,
        longitude: Double
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val connection = Connection(
                    id = UUID.randomUUID().toString(),
                    user1Id = user1Id,
                    user2Id = user2Id,
                    location = Pair(latitude, longitude),
                    created = System.currentTimeMillis(),
                    expiry = System.currentTimeMillis() + (30L * 24 * 3600 * 1000), // 30 days
                    shouldContinue = Pair(false, false)
                )

                val createdConnection = repository.createConnection(connection)
                if (createdConnection == null) {
                    _error.value = "Failed to create connection"
                }
            } catch (e: Exception) {
                _error.value = "Error creating connection: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun clearError() {
        _error.value = null
    }
}

