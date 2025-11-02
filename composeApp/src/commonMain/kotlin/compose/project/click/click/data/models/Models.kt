package compose.project.click.click.data.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val image: String,
    val shareKey: Long = 0,
    val connections: List<String> = emptyList(),
    val createdAt: Long
)

@Serializable
data class Message(
    val id: String,
    val userId: String,
    val content: String,
    val timeCreated: Long,
    val timeEdited: Long
)

@Serializable
data class Connection(
    val id: String,
    val user1Id: String,
    val user2Id: String,
    val location: Pair<Double, Double>,
    val created: Long,
    val expiry: Long,
    val shouldContinue: Pair<Boolean, Boolean> = Pair(false, false)
)

@Serializable
data class Chat(
    val id: String,
    val messages: List<Message> = emptyList()
)

