package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import kotlinx.serialization.Serializable

/**
 * Events emitted by the realtime messages subscription ([ChatRepository.subscribeToMessages]).
 */
sealed class MessageChangeEvent {
    data class Insert(val message: Message) : MessageChangeEvent()
    data class Update(val message: Message) : MessageChangeEvent()
    data class Delete(val messageId: String) : MessageChangeEvent()
}

/**
 * Events emitted by the realtime reactions subscription ([ChatRepository.subscribeToReactions]).
 */
sealed class ReactionChangeEvent {
    data class Insert(val reaction: MessageReaction) : ReactionChangeEvent()
    data class Delete(val reactionId: String, val messageId: String) : ReactionChangeEvent()
}

@Serializable
data class TypingStatus(val userId: String, val isTyping: Boolean)
