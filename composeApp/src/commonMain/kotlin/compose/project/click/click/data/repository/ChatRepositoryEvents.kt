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
 * Combined Postgres realtime stream for [messages] and [message_reactions] on one channel
 * (matches web `chat:${chatId}`: one subscribe so reaction inserts are not dropped on mobile).
 */
sealed class ChatRealtimeEvent {
    data class Message(val event: MessageChangeEvent) : ChatRealtimeEvent()
    data class Reaction(val event: ReactionChangeEvent) : ChatRealtimeEvent()
}

/**
 * INSERT on [messages] visible to the user (RLS). Used to refresh the connections list preview
 * without opening each chat or waiting for a debounced full reload.
 */
data class MessageListInsertEvent(val connectionId: String, val message: Message)

/** Reaction row changes delivered over the shared chat realtime channel. */
sealed class ReactionChangeEvent {
    data class Insert(val reaction: MessageReaction) : ReactionChangeEvent()
    data class Delete(val reactionId: String, val messageId: String) : ReactionChangeEvent()
}

@Serializable
data class TypingStatus(val userId: String, val isTyping: Boolean)
