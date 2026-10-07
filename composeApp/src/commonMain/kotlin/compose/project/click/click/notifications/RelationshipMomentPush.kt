package compose.project.click.click.notifications

/**
 * Relationship-moment pushes (click-web `lib/nudges/moments.ts`). They carry server-written
 * `title`/`body` and must never go down the chat-message path (which decrypts, bumps the inbox,
 * and titles by sender). Tap routing lives in [PushRoutes].
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
}
