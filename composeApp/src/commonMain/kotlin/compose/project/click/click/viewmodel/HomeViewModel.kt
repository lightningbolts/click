package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionInsights
import compose.project.click.click.data.models.ReconnectHelper
import compose.project.click.click.data.models.ReconnectReminder
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.storage.createTokenStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    
    // Track if data has been loaded already
    private var dataLoaded = false

    init {
        observeAppData()
    }
    
    /**
     * Observe the shared app data instead of loading independently
     */
    private fun observeAppData() {
        viewModelScope.launch {
            // Combine user and connections data from AppDataManager
            combine(
                AppDataManager.currentUser,
                AppDataManager.connections,
                AppDataManager.isLoading,
                AppDataManager.isDataLoaded
            ) { user, connections, isLoading, isDataLoaded ->
                Triple(user to connections, isLoading, isDataLoaded)
            }.collectLatest { (data, isLoading, isDataLoaded) ->
                val (user, connections) = data
                
                when {
                    isLoading && !isDataLoaded -> {
                        _homeState.value = HomeState.Loading
                    }
                    user != null && isDataLoaded -> {
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
                        
                        // Load additional data only once
                        if (!dataLoaded) {
                            dataLoaded = true
                            loadReconnectReminders(user.id, connections)
                            loadConnectionInsights(user.id, connections)
                        }
                    }
                    !isLoading && !isDataLoaded -> {
                        _homeState.value = HomeState.Error("Not logged in")
                    }
                }
            }
        }
    }
    
    /**
     * Load reconnect reminders for dormant connections
     */
    private suspend fun loadReconnectReminders(userId: String, connections: List<Connection>) {
        try {
            val usersMap = AppDataManager.connectedUsers.value
            
            // Get last message time for each connection
            val connectionsWithLastMessage = connections.map { connection ->
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
            val usersMap = AppDataManager.connectedUsers.value
            
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

    /**
     * Force refresh (only when user explicitly requests)
     */
    fun refresh() {
        dataLoaded = false
        AppDataManager.refresh(force = true)
    }
}
