package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MapState {
    object Loading : MapState()
    data class Success(val connections: List<Connection>) : MapState()
    data class Error(val message: String) : MapState()
}

class MapViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
) : ViewModel() {

    private val _mapState = MutableStateFlow<MapState>(MapState.Loading)
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    init {
        loadConnections()
    }

    fun loadConnections() {
        viewModelScope.launch {
            try {
                _mapState.value = MapState.Loading

                val currentUser = authRepository.getCurrentUser()

                if (currentUser != null) {
                    // Fetch user connections from Supabase
                    val connections = supabaseRepository.fetchUserConnections(currentUser.id)
                    _mapState.value = MapState.Success(connections = connections)
                } else {
                    // No user logged in
                    _mapState.value = MapState.Success(connections = emptyList())
                }
            } catch (e: Exception) {
                _mapState.value = MapState.Error(
                    e.message ?: "An error occurred while loading connections"
                )
            }
        }
    }

    fun refresh() {
        loadConnections()
    }
}

