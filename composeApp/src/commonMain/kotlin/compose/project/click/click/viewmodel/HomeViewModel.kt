package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionInsights
import compose.project.click.click.data.models.ReconnectHelper
import compose.project.click.click.data.models.ReconnectReminder
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.storage.createTokenStorage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    // ── Connections grouped by semantic location for the expandable home cards ──
    private val _locationGroupedConnections = MutableStateFlow<Map<String, List<Connection>>>(emptyMap())
    val locationGroupedConnections: StateFlow<Map<String, List<Connection>>> = _locationGroupedConnections.asStateFlow()

    // Which location groups the user has expanded on the home screen
    private val _expandedLocations = MutableStateFlow<Set<String>>(emptySet())
    val expandedLocations: StateFlow<Set<String>> = _expandedLocations.asStateFlow()

    // Connected users map (id → User) for name resolution in home cards
    private val _connectedUsers = MutableStateFlow<Map<String, User>>(emptyMap())
    val connectedUsers: StateFlow<Map<String, User>> = _connectedUsers.asStateFlow()

    // Nudge feedback toast
    private val _nudgeResult = MutableStateFlow<String?>(null)
    val nudgeResult: StateFlow<String?> = _nudgeResult.asStateFlow()
    
    // Track if data has been loaded already
    private var dataLoaded = false
    
    // Realtime channel for connections changes
    private var connectionsChannel: RealtimeChannel? = null

    init {
        observeAppData()
        subscribeToConnectionChanges()
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

                        // Build location-grouped map (all connections, not just top 3)
                        val grouped = connections
                            .sortedByDescending { it.created }
                            .groupBy { it.semantic_location ?: "Somewhere New" }
                        _locationGroupedConnections.value = grouped

                        // Expose connected users for name lookups
                        _connectedUsers.value = AppDataManager.connectedUsers.value
                        
                        _homeState.value = HomeState.Success(user, stats)
                        
                        // Load additional data only once
                        if (!dataLoaded) {
                            dataLoaded = true
                            loadReconnectReminders(user.id, connections)
                            loadConnectionInsights(user.id, connections)
                        }
                    }
                    !isDataLoaded || isLoading -> {
                        _homeState.value = HomeState.Loading
                    }
                    else -> {
                        _homeState.value = HomeState.Error("Session expired. Please log in again.")
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
     * Toggle expanded state for a location group on the home screen.
     */
    fun toggleLocationExpanded(location: String) {
        val current = _expandedLocations.value
        _expandedLocations.value = if (location in current) current - location else current + location
    }

    /**
     * Send a nudge from the home screen without opening the chat view.
     */
    fun sendNudge(chatId: String, otherUserName: String) {
        val currentUser = AppDataManager.currentUser.value ?: return
        viewModelScope.launch {
            val msg = chatRepository.sendMessage(
                chatId = chatId,
                userId = currentUser.id,
                content = "👋 ${currentUser.name ?: "Someone"} nudged you!"
            )
            _nudgeResult.value = if (msg != null) "Nudge sent to $otherUserName! 👋" else "Failed to send nudge"
        }
    }

    /**
     * Send a nudge using a connection ID — resolves the chat ID first.
     * Used from the home screen where chat IDs may not be cached.
     */
    fun sendNudgeByConnectionId(connectionId: String, otherUserName: String) {
        val currentUser = AppDataManager.currentUser.value ?: return
        viewModelScope.launch {
            try {
                val chatDetails = chatRepository.fetchChatWithDetails(connectionId, currentUser.id)
                val chatId = chatDetails?.chat?.id
                if (chatId != null) {
                    sendNudge(chatId, otherUserName)
                } else {
                    _nudgeResult.value = "Unable to send nudge — chat not found"
                }
            } catch (e: Exception) {
                _nudgeResult.value = "Failed to send nudge"
            }
        }
    }

    fun clearNudgeResult() {
        _nudgeResult.value = null
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
    
    /**
     * Subscribe to real-time changes on the connections table.
     * Triggers an AppDataManager refresh on any INSERT/UPDATE/DELETE so
     * the home screen connection count stays current without manual pull-to-refresh.
     */
    private fun subscribeToConnectionChanges() {
        viewModelScope.launch {
            try {
                val channel = SupabaseConfig.client.channel("home:connections")
                connectionsChannel = channel
                
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "connections"
                }.onEach {
                    // Any change to connections → refresh data
                    AppDataManager.refresh(force = true)
                }.launchIn(this)
                
                channel.subscribe()
            } catch (e: Exception) {
                println("HomeViewModel: Error subscribing to connections: ${e.message}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        connectionsChannel?.let { channel ->
            viewModelScope.launch {
                try { channel.unsubscribe() } catch (_: Exception) {}
            }
        }
        connectionsChannel = null
    }
}
