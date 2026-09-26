package compose.project.click.click.notifications

/** Where tapping a push lands, decided from the payload alone (iOS `ClickApp.tapRoute`). */
sealed interface PushRoute {
    /** Direct chat by connection id (preferred) or chat id. */
    data class DirectChat(
        val chatId: String,
        val connectionId: String,
    ) : PushRoute

    data class GroupChat(
        val chatId: String,
    ) : PushRoute

    data class Event(
        val beaconId: String,
    ) : PushRoute

    data class Hub(
        val hubId: String,
    ) : PushRoute

    data class Profile(
        val userId: String,
    ) : PushRoute

    /** The Clicks list (unknown identity, availability matches). */
    data object Connections : PushRoute

    /** Unknown future types keep the current app state (just open the app). */
    data object OpenApp : PushRoute
}

/** One table for every push `type` the app receives. */
object PushRoutes {
    /** Types shown with server-written title/body rather than the chat-message path. */
    val serverTextTypes: Set<String> =
        setOf("event_reminder", "shared_upcoming_event", "reconnect_nudge", "archive_warning", "availability_match")

    fun route(data: Map<String, String>): PushRoute {
        fun value(vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> data[key]?.trim()?.takeIf { it.isNotEmpty() } }
        val chatId = value("chat_id", "chatId")
        val connectionId = value("connection_id", "connectionId")
        val peerUserId = value("peer_user_id", "user_id", "sender_user_id")

        fun directChatOr(fallback: PushRoute): PushRoute =
            if (chatId != null || connectionId != null) {
                PushRoute.DirectChat(chatId.orEmpty(), connectionId.orEmpty())
            } else {
                fallback
            }
        return when (value("type", "category").orEmpty()) {
            "chat_message", "new_message", "message", "disposable_reveal", "" ->
                if (value("group_id") != null && chatId != null) PushRoute.GroupChat(chatId) else directChatOr(PushRoute.OpenApp)
            "event_reminder", "event_teaser", "shared_upcoming_event" ->
                value("beacon_id", "event_id")?.let { PushRoute.Event(it) } ?: PushRoute.OpenApp
            "hub_message" -> value("hub_id", "venue_id")?.let { PushRoute.Hub(it) } ?: PushRoute.OpenApp
            // The profile carries the moment: friendship, story, and a hangout to confirm.
            "archive_warning", "reconnect_nudge", "anniversary", "memory_prompt", "hangout_confirm" ->
                peerUserId?.let { PushRoute.Profile(it) } ?: PushRoute.Connections
            "wave" -> connectionId?.let { PushRoute.DirectChat("", it) } ?: directChatOr(PushRoute.Connections)
            "group_revival" -> chatId?.let { PushRoute.GroupChat(it) } ?: PushRoute.Connections
            "availability_match" -> PushRoute.Connections
            else -> PushRoute.OpenApp
        }
    }
}
