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

    /** A shared Click Drop's story, opened on Home once it's in the strip (iOS `TapRoute.sharedDrop`). */
    data class SharedDrop(
        val dropId: String,
    ) : PushRoute

    /** The Clicks list (unknown identity, availability matches). */
    data object Connections : PushRoute

    /** Unknown future types keep the current app state (just open the app). */
    data object OpenApp : PushRoute
}

/** One table for every push `type` the app receives (mirrors iOS `ClickNotificationCoordinator.tapRoute`). */
object PushRoutes {
    /**
     * Types shown with server-written title/body (`data.title`/`data.body`, set by
     * `send-push-notification` for every non-chat category) rather than the chat-message path.
     * Anything not here is treated as a chat message, so every non-chat type must be listed.
     */
    val serverTextTypes: Set<String> =
        setOf(
            "event_reminder",
            "shared_upcoming_event",
            "reconnect_nudge",
            "archive_warning",
            "availability_match",
            "event_rsvp",
            "event_rsvp_request",
            "friends_going",
            "event_trending",
            "event_drop_recap",
            "event_recap",
            "new_connection",
            "prior_connection_accepted",
            "prior_connection_request",
            "device_approval",
            "shared_drop_released",
            "reaction",
            "reconnect_nearby",
        )

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

        fun event(): PushRoute = value("beacon_id", "event_id")?.let { PushRoute.Event(it) } ?: PushRoute.OpenApp
        return when (value("type", "category").orEmpty()) {
            "chat_message", "new_message", "message", "disposable_reveal", "" ->
                if (value("group_id") != null && chatId != null) PushRoute.GroupChat(chatId) else directChatOr(PushRoute.OpenApp)
            "event_reminder", "event_teaser", "shared_upcoming_event", "event_rsvp", "event_rsvp_request",
            "friends_going", "event_trending",
            -> event()
            // iOS opens the recap; Android has no recap screen yet (audit G3-2), so the event page.
            "event_drop_recap", "event_recap" -> event()
            "hub_message" -> value("hub_id", "venue_id")?.let { PushRoute.Hub(it) } ?: PushRoute.OpenApp
            // The profile carries the moment: friendship, story, and a hangout to confirm.
            "archive_warning", "reconnect_nudge", "anniversary", "memory_prompt", "hangout_confirm", "prior_connection_accepted" ->
                peerUserId?.let { PushRoute.Profile(it) } ?: PushRoute.Connections
            "wave", "new_connection", "reconnect_nearby" ->
                connectionId?.let { PushRoute.DirectChat("", it) } ?: directChatOr(PushRoute.Connections)
            "group_revival" -> chatId?.let { PushRoute.GroupChat(it) } ?: PushRoute.Connections
            "availability_match" -> PushRoute.Connections
            // iOS answers it in Activity; Android shows pending prior requests in the Clicks list.
            "prior_connection_request" -> PushRoute.Connections
            // A reaction opens what was reacted to: the beacon, or the shared drop's story.
            "reaction" ->
                value("target_id")
                    ?.let { id -> if (data["target_kind"] == "shared_drop") PushRoute.SharedDrop(id) else PushRoute.Event(id) }
                    ?: PushRoute.OpenApp
            // "Just developed": the newest released drop's story.
            "shared_drop_released" -> value("drop_id")?.let { PushRoute.SharedDrop(it) } ?: PushRoute.OpenApp
            // No Android screen yet (audit G7-1): open the app rather than guess.
            "device_approval" -> PushRoute.OpenApp
            else -> PushRoute.OpenApp
        }
    }
}
