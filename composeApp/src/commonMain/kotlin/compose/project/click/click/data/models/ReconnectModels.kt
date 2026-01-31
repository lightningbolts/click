package compose.project.click.click.data.models

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Status of a connection's activity level
 */
enum class ConnectionActivityStatus {
    ACTIVE,      // Recent activity within 7 days
    COOLING,     // No activity for 7-14 days  
    DORMANT,     // No activity for 14-30 days
    INACTIVE     // No activity for 30+ days
}

/**
 * Represents a reconnect reminder for a dormant connection
 */
@Serializable
data class ReconnectReminder(
    val connectionId: String,
    val userId: String,          // The other user
    val userName: String?,
    val lastMessageTime: Long,   // When the last message was sent
    val daysSinceContact: Int,
    val activityStatus: ConnectionActivityStatus,
    val suggestedMessage: String // Suggested reconnect message
)

/**
 * Connection Insights Model - Stats about a user's connections
 */
@Serializable
data class ConnectionInsights(
    val totalConnections: Int = 0,
    val keptConnections: Int = 0,          // Connections that passed Vibe Check
    val activeConnections: Int = 0,        // Active in last 7 days
    val dormantConnections: Int = 0,       // Inactive for 14+ days
    val keepRate: Float = 0f,              // Percentage of connections kept
    val longestConnectionDays: Int = 0,    // Longest active connection
    val longestConnectionName: String? = null,
    val averageResponseTimeMinutes: Int = 0,
    val connectionsThisWeek: Int = 0,
    val connectionsThisMonth: Int = 0
)

/**
 * Utility object for connection activity calculations and reminders
 */
object ReconnectHelper {
    
    // Time thresholds in milliseconds
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val ACTIVE_THRESHOLD_DAYS = 7
    private const val COOLING_THRESHOLD_DAYS = 14
    private const val DORMANT_THRESHOLD_DAYS = 30
    
    /**
     * Calculate the activity status of a connection based on last message time
     */
    fun getActivityStatus(lastMessageTime: Long): ConnectionActivityStatus {
        val now = Clock.System.now().toEpochMilliseconds()
        val daysSince = ((now - lastMessageTime) / DAY_MS).toInt()
        
        return when {
            daysSince <= ACTIVE_THRESHOLD_DAYS -> ConnectionActivityStatus.ACTIVE
            daysSince <= COOLING_THRESHOLD_DAYS -> ConnectionActivityStatus.COOLING
            daysSince <= DORMANT_THRESHOLD_DAYS -> ConnectionActivityStatus.DORMANT
            else -> ConnectionActivityStatus.INACTIVE
        }
    }
    
    /**
     * Calculate days since last contact
     */
    fun getDaysSinceContact(lastMessageTime: Long): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        return ((now - lastMessageTime) / DAY_MS).toInt()
    }
    
    /**
     * Get a suggested reconnect message based on the connection
     */
    fun getSuggestedMessage(userName: String?, contextTag: String?, daysSince: Int): String {
        val name = userName ?: "there"
        
        // Context-specific messages
        if (!contextTag.isNullOrBlank()) {
            val contextMessages = listOf(
                "Hey $name! It's been a while since we met at $contextTag. How have you been?",
                "Hi $name! Remember when we met at $contextTag? Would love to catch up!",
                "Hey! I was just thinking about $contextTag and remembered we connected there. How's everything going?"
            )
            return contextMessages.random()
        }
        
        // Time-based messages
        return when {
            daysSince <= 14 -> listOf(
                "Hey $name! What's new with you?",
                "Hi $name! How's your week going?",
                "Hey! Just wanted to say hi and see how you're doing!"
            ).random()
            daysSince <= 30 -> listOf(
                "Hey $name! It's been a couple weeks - how have you been?",
                "Hi $name! We should catch up sometime. What have you been up to?",
                "Hey! Miss chatting with you. How's everything?"
            ).random()
            else -> listOf(
                "Hey $name! It's been a while! I hope you're doing well.",
                "Hi $name! Long time no talk - would love to reconnect!",
                "Hey! I was thinking about you and wanted to reach out. How's life?"
            ).random()
        }
    }
    
    /**
     * Get the reminder message for the home screen
     */
    fun getReminderMessage(daysSince: Int, userName: String?): String {
        val name = userName ?: "someone"
        return when {
            daysSince <= 14 -> "You haven't chatted with $name in $daysSince days"
            daysSince <= 30 -> "It's been $daysSince days since you talked to $name"
            else -> "Reconnect with $name? It's been over a month!"
        }
    }
    
    /**
     * Filter connections that need reconnect reminders
     * Returns connections that are cooling or dormant
     */
    fun getConnectionsNeedingReminders(
        connections: List<Pair<Connection, Long>>, // Connection with lastMessageTime
        users: Map<String, User>,
        currentUserId: String,
        limit: Int = 3
    ): List<ReconnectReminder> {
        return connections
            .map { (connection, lastMessageTime) ->
                val otherUserId = connection.user_ids.firstOrNull { it != currentUserId } ?: ""
                val otherUser = users[otherUserId]
                val daysSince = getDaysSinceContact(lastMessageTime)
                val status = getActivityStatus(lastMessageTime)
                
                ReconnectReminder(
                    connectionId = connection.id,
                    userId = otherUserId,
                    userName = otherUser?.name,
                    lastMessageTime = lastMessageTime,
                    daysSinceContact = daysSince,
                    activityStatus = status,
                    suggestedMessage = getSuggestedMessage(
                        otherUser?.name,
                        connection.context_tag,
                        daysSince
                    )
                )
            }
            .filter { it.activityStatus in listOf(
                ConnectionActivityStatus.COOLING,
                ConnectionActivityStatus.DORMANT,
                ConnectionActivityStatus.INACTIVE
            ) }
            .sortedByDescending { it.daysSinceContact }
            .take(limit)
    }
    
    /**
     * Calculate connection insights from a list of connections
     */
    fun calculateInsights(
        connections: List<Connection>,
        messagesPerConnection: Map<String, Long>, // connectionId -> lastMessageTime
        currentUserId: String,
        users: Map<String, User>
    ): ConnectionInsights {
        if (connections.isEmpty()) {
            return ConnectionInsights()
        }
        
        val now = Clock.System.now().toEpochMilliseconds()
        val weekAgo = now - (7 * DAY_MS)
        val monthAgo = now - (30 * DAY_MS)
        
        val keptConnections = connections.count { it.isMutuallyKept() }
        
        var activeCount = 0
        var dormantCount = 0
        var longestDays = 0
        var longestConnectionName: String? = null
        
        connections.forEach { connection ->
            val lastMessage = messagesPerConnection[connection.id] ?: connection.created
            val status = getActivityStatus(lastMessage)
            
            when (status) {
                ConnectionActivityStatus.ACTIVE -> activeCount++
                ConnectionActivityStatus.DORMANT, ConnectionActivityStatus.INACTIVE -> dormantCount++
                else -> {}
            }
            
            // Calculate connection age
            val connectionDays = ((now - connection.created) / DAY_MS).toInt()
            if (connectionDays > longestDays) {
                longestDays = connectionDays
                val otherUserId = connection.user_ids.firstOrNull { it != currentUserId }
                longestConnectionName = otherUserId?.let { users[it]?.name }
            }
        }
        
        val connectionsThisWeek = connections.count { it.created >= weekAgo }
        val connectionsThisMonth = connections.count { it.created >= monthAgo }
        
        val keepRate = if (connections.isNotEmpty()) {
            (keptConnections.toFloat() / connections.size) * 100
        } else 0f
        
        return ConnectionInsights(
            totalConnections = connections.size,
            keptConnections = keptConnections,
            activeConnections = activeCount,
            dormantConnections = dormantCount,
            keepRate = keepRate,
            longestConnectionDays = longestDays,
            longestConnectionName = longestConnectionName,
            connectionsThisWeek = connectionsThisWeek,
            connectionsThisMonth = connectionsThisMonth
        )
    }
}
