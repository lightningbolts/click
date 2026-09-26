package compose.project.click.click.notifications

/**
 * Relationship-moment pushes (click-web `lib/nudges/moments.ts`). They carry server-written
 * `title`/`body` and must never go down the chat-message path (which decrypts, bumps the inbox,
 * and titles by sender). Routing mirrors iOS `ClickApp` push handling.
 */
internal object RelationshipMomentPush {
    const val ANNIVERSARY = "anniversary"
    const val MEMORY_PROMPT = "memory_prompt"
    const val GROUP_REVIVAL = "group_revival"
    const val WAVE = "wave"
    const val HANGOUT_CONFIRM = "hangout_confirm"

    val types: Set<String> = setOf(ANNIVERSARY, MEMORY_PROMPT, GROUP_REVIVAL, WAVE, HANGOUT_CONFIRM)

    fun isMoment(type: String?): Boolean = type != null && type in types

    /**
     * Cron moments are gated like reconnect nudges ("Relationship moments"); user-triggered ones
     * (a wave, a hangout to confirm) follow the message preference, matching the server.
     */
    fun usesRelationshipMomentsPreference(type: String): Boolean = type == ANNIVERSARY || type == MEMORY_PROMPT || type == GROUP_REVIVAL

    sealed interface Route {
        data class Profile(
            val userId: String,
        ) : Route

        /** Direct chat by connection id, or a group chat by chat id. */
        data class Chat(
            val chatId: String,
            val connectionId: String,
        ) : Route

        data object Home : Route
    }

    fun route(
        type: String,
        data: Map<String, String>,
    ): Route {
        fun value(key: String): String = data[key]?.trim().orEmpty()
        val peerUserId = value("peer_user_id")
        val connectionId = value("connection_id")
        val chatId = value("chat_id")
        val chatRoute =
            when {
                chatId.isNotEmpty() || connectionId.isNotEmpty() -> Route.Chat(chatId = chatId, connectionId = connectionId)
                else -> Route.Home
            }
        return when (type) {
            ANNIVERSARY, MEMORY_PROMPT, HANGOUT_CONFIRM ->
                if (peerUserId.isNotEmpty()) Route.Profile(peerUserId) else chatRoute
            GROUP_REVIVAL -> if (chatId.isNotEmpty()) Route.Chat(chatId = chatId, connectionId = "") else Route.Home
            WAVE -> if (connectionId.isNotEmpty()) Route.Chat(chatId = "", connectionId = connectionId) else chatRoute
            else -> Route.Home
        }
    }
}
