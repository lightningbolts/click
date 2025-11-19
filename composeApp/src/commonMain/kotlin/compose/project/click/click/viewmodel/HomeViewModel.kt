package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserStats(
    val totalConnections: Int,
    val recentConnections: List<Connection>,
    val uniqueLocations: Int
)

sealed class HomeState {
    object Loading : HomeState()
    data class Success(val user: User, val stats: UserStats) : HomeState()
    data class Error(val message: String) : HomeState()
}

class HomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
) : ViewModel() {

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            try {
                _homeState.value = HomeState.Loading

                val currentUserAuth = authRepository.getCurrentUser()
                if (currentUserAuth != null) {
                    println("Current user from auth: ${currentUserAuth.id}, email: ${currentUserAuth.email}")

                    // Fetch full user data from Supabase
                    var user = supabaseRepository.fetchUserById(currentUserAuth.id)

                    // If user doesn't exist in database, create a fallback user object
                    if (user == null) {
                        println("User not found in database, creating fallback user")
                        user = User(
                            id = currentUserAuth.id,
                            name = currentUserAuth.email?.substringBefore('@') ?: "User",
                            email = currentUserAuth.email,
                            image = null,
                            createdAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                            lastPolled = null,
                            connections = emptyList(),
                            paired_with = emptyList(),
                            connection_today = 0,
                            last_paired = null
                        )
                    }

                    // Fetch all user connections
                    val connections = supabaseRepository.fetchUserConnections(user.id)

                    // Calculate stats
                    val recentConnections = connections
                        .sortedByDescending { it.created }
                        .take(3)

                    val uniqueLocations = connections
                        .mapNotNull { it.semantic_location }
                        .distinct()
                        .size

                    val stats = UserStats(
                        totalConnections = connections.size,
                        recentConnections = recentConnections,
                        uniqueLocations = uniqueLocations
                    )

                    _homeState.value = HomeState.Success(user, stats)
                } else {
                    _homeState.value = HomeState.Error("Not logged in")
                }
            } catch (e: Exception) {
                println("Error in loadHomeData: ${e.message}")
                e.printStackTrace()
                _homeState.value = HomeState.Error(
                    e.message ?: "Failed to load home data"
                )
            }
        }
    }

    fun refresh() {
        loadHomeData()
    }
}

