package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionInsights
import compose.project.click.click.data.models.ReconnectHelper
import compose.project.click.click.data.models.ReconnectReminder
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.createTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
    private val supabaseRepository: SupabaseRepository = SupabaseRepository(),
    private val chatRepository: ChatRepository = ChatRepository(tokenStorage = createTokenStorage())
) : ViewModel() {

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()
    
    // Reconnect Reminders
    private val _reconnectReminders = MutableStateFlow<List<ReconnectReminder>>(emptyList())
    val reconnectReminders: StateFlow<List<ReconnectReminder>> = _reconnectReminders.asStateFlow()
    
    // Connection Insights
    private val _connectionInsights = MutableStateFlow<ConnectionInsights?>(null)
    val connectionInsights: StateFlow<ConnectionInsights?> = _connectionInsights.asStateFlow()
    
    // Show/hide insights panel
    private val _showInsightsPanel = MutableStateFlow(false)
    val showInsightsPanel: StateFlow<Boolean> = _showInsightsPanel.asStateFlow()

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
                            createdAt = Clock.System.now().toEpochMilliseconds(),
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
                    
                    // Load reconnect reminders and insights
                    loadReconnectReminders(user.id, connections)
                    loadConnectionInsights(user.id, connections)
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
    
    /**
     * Load reconnect reminders for dormant connections
     */
    private suspend fun loadReconnectReminders(userId: String, connections: List<Connection>) {
        try {
            // Get all other user IDs from connections
            val otherUserIds = connections.flatMap { it.user_ids }.filter { it != userId }.distinct()
            
            // Fetch user info for all connected users
            val users = supabaseRepository.fetchUsersByIds(otherUserIds)
            val usersMap = users.associateBy { it.id }
            
            // Get last message time for each connection
            val connectionsWithLastMessage = connections.map { connection ->
                // Get the chat messages to find the last message time
                val chats = chatRepository.fetchUserChatsWithDetails(userId)
                val lastMessageTime = chats
                    .find { it.connection.id == connection.id }
                    ?.lastMessage?.timeCreated
                    ?: connection.created
                
                connection to lastMessageTime
            }
            
            // Calculate reminders
            val reminders = ReconnectHelper.getConnectionsNeedingReminders(
                connections = connectionsWithLastMessage,
                users = usersMap,
                currentUserId = userId,
                limit = 3
            )
            
            _reconnectReminders.value = reminders
        } catch (e: Exception) {
            println("Error loading reconnect reminders: ${e.message}")
            _reconnectReminders.value = emptyList()
        }
    }
    
    /**
     * Load connection insights statistics
     */
    private suspend fun loadConnectionInsights(userId: String, connections: List<Connection>) {
        try {
            // Get all other user IDs from connections
            val otherUserIds = connections.flatMap { it.user_ids }.filter { it != userId }.distinct()
            val users = supabaseRepository.fetchUsersByIds(otherUserIds)
            val usersMap = users.associateBy { it.id }
            
            // Get last message times
            val chats = chatRepository.fetchUserChatsWithDetails(userId)
            val messagesPerConnection = chats.associate { chatWithDetails ->
                chatWithDetails.connection.id to (chatWithDetails.lastMessage?.timeCreated ?: chatWithDetails.connection.created)
            }
            
            val insights = ReconnectHelper.calculateInsights(
                connections = connections,
                messagesPerConnection = messagesPerConnection,
                currentUserId = userId,
                users = usersMap
            )
            
            _connectionInsights.value = insights
        } catch (e: Exception) {
            println("Error loading connection insights: ${e.message}")
            _connectionInsights.value = null
        }
    }
    
    /**
     * Dismiss a reconnect reminder
     */
    fun dismissReminder(connectionId: String) {
        _reconnectReminders.value = _reconnectReminders.value.filter { it.connectionId != connectionId }
    }
    
    /**
     * Toggle the insights panel visibility
     */
    fun toggleInsightsPanel() {
        _showInsightsPanel.value = !_showInsightsPanel.value
    }
    
    /**
     * Hide the insights panel
     */
    fun hideInsightsPanel() {
        _showInsightsPanel.value = false
    }

    fun refresh() {
        loadHomeData()
    }
}

