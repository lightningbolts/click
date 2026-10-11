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

    // region G1-3 destinations without an Android screen yet: the IDs are parsed now; the push
    // service opens the app (OpenApp) for these until the screen lands.

    /** An event's Click Drops recap (G3-2). */
    data class EventRecap(
        val beaconId: String,
    ) : PushRoute

    /** A shared Click Drop's story on Home (G2-4). [dropCount] > 1 when the push was batched. */
    data class SharedDrop(
        val dropId: String,
        val posterId: String? = null,
        val dropCount: Int? = null,
    ) : PushRoute

    /** The device-approval sheet (G7-1). */
    data class DeviceApproval(
        val requestId: String,
    ) : PushRoute

    /** The Activity inbox (G6-1), e.g. a prior-connection request answered there. */
    data class Activity(
        val connectionId: String? = null,
    ) : PushRoute

    // endregion

    /** Unknown future types keep the current app state (just open the app). */
    data object OpenApp : PushRoute
}

/** One table for every push `type` the app receives. */
object PushRoutes {
    /** Types shown with server-written title/body rather than the chat-message path. */
    val serverTextTypes: Set<String> =
        setOf(
            "event_reminder",
            "shared_upcoming_event",
            "reconnect_nudge",
            "archive_warning",
            "availability_match",
            // G1-3 (sheet 08 §16.3)
            "event_recap",
            "event_drop_recap",
            "shared_drop_released",
            "device_approval",
            "prior_connection_request",
            "prior_connection_accepted",
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
        return when (value("type", "category").orEmpty()) {
            "chat_message", "new_message", "message", "disposable_reveal", "" ->
                if (value("group_id") != null && chatId != null) PushRoute.GroupChat(chatId) else directChatOr(PushRoute.OpenApp)
            // Activity-only types (event_rsvp…event_trending) reuse the router for Activity rows.
            "event_reminder", "event_teaser", "shared_upcoming_event", "event_rsvp", "event_rsvp_request",
            "friends_going", "event_trending",
            ->
                value("beacon_id", "event_id")?.let { PushRoute.Event(it) } ?: PushRoute.OpenApp
            "event_drop_recap", "event_recap" ->
                value("beacon_id", "event_id")?.let { PushRoute.EventRecap(it) } ?: PushRoute.OpenApp
            "hub_message" -> value("hub_id", "venue_id")?.let { PushRoute.Hub(it) } ?: PushRoute.OpenApp
            // The profile carries the moment: friendship, story, and a hangout to confirm.
            "archive_warning", "reconnect_nudge", "reconnect_lull", "anniversary", "memory_prompt", "hangout_confirm",
            "prior_connection_accepted",
            ->
                peerUserId?.let { PushRoute.Profile(it) } ?: PushRoute.Connections
            "wave", "new_connection" -> connectionId?.let { PushRoute.DirectChat("", it) } ?: directChatOr(PushRoute.Connections)
            "reconnect_nearby" -> connectionId?.let { PushRoute.DirectChat("", it) } ?: PushRoute.Connections
            "group_revival" -> chatId?.let { PushRoute.GroupChat(it) } ?: PushRoute.Connections
            "availability_match" -> PushRoute.Connections
            "device_approval" -> value("request_id")?.let { PushRoute.DeviceApproval(it) } ?: PushRoute.OpenApp
            "shared_drop_released" ->
                value("drop_id")?.let {
                    PushRoute.SharedDrop(it, posterId = value("poster_id"), dropCount = value("drop_count")?.toIntOrNull())
                } ?: PushRoute.OpenApp
            "reaction" -> {
                val targetId = value("target_id")
                when {
                    targetId == null -> PushRoute.OpenApp
                    value("target_kind") == "shared_drop" -> PushRoute.SharedDrop(targetId)
                    else -> PushRoute.Event(targetId)
                }
            }
            "prior_connection_request" -> PushRoute.Activity(connectionId)
            else -> PushRoute.OpenApp
        }
    }
}
