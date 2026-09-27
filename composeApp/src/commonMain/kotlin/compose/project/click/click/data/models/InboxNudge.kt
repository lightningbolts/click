package compose.project.click.click.data.models // pragma: allowlist secret

import compose.project.click.click.data.api.InboxNudgeDto // pragma: allowlist secret
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Server `nudges.nudge_type` values (click-web `20260926090000_relationship_moments.sql`). */
enum class NudgeKind(
    val wire: String,
) {
    RECONNECT_LULL("reconnect_lull"),
    SHARED_UPCOMING_EVENT("shared_upcoming_event"),
    ANNIVERSARY("anniversary"),
    MEMORY_PROMPT("memory_prompt"),
    GROUP_REVIVAL("group_revival"),
    WAVE("wave"),
    HANGOUT_CONFIRM("hangout_confirm"),
    ;

    companion object {
        fun fromWire(value: String?): NudgeKind? = entries.firstOrNull { it.wire == value?.trim() }
    }
}

/** What tapping a nudge's primary button does. Mirrors iOS `HomeView` nudge actions. */
sealed interface NudgeAction {
    data class OpenChat(
        val connectionId: String,
    ) : NudgeAction

    data class OpenEvent(
        val beaconId: String,
    ) : NudgeAction

    data class OpenGroupChat(
        val chatId: String,
    ) : NudgeAction

    /** Opens the peer's profile (memory prompts land on the journal). */
    data class OpenProfile(
        val userId: String,
        val connectionId: String?,
    ) : NudgeAction

    data class ConfirmHangout(
        val confirmationId: String,
    ) : NudgeAction

    data class WaveBack(
        val connectionId: String,
    ) : NudgeAction
}

/** Typed view over [InboxNudgeDto]; `null` from [InboxNudgeDto.typed] means "skip this nudge". */
data class InboxNudge(
    val dto: InboxNudgeDto,
    val kind: NudgeKind,
) {
    val id: String get() = dto.id
    val headline: String get() = dto.headline
    val body: String get() = dto.body
    val connectionId: String? get() = dto.connectionId?.trim()?.takeIf { it.isNotEmpty() }
    val beaconId: String? get() = dto.beaconId?.trim()?.takeIf { it.isNotEmpty() }
    val peerUserId: String? get() = payloadString("peer_user_id")
    val chatId: String? get() = payloadString("chat_id")
    val groupId: String? get() = payloadString("group_id")
    val groupName: String? get() = payloadString("group_name")
    val confirmationId: String? get() = payloadString("confirmation_id")
    val placeName: String? get() = payloadString("place_name")

    /** Label for the primary button. */
    val primaryActionTitle: String
        get() =
            when (kind) {
                NudgeKind.SHARED_UPCOMING_EVENT -> if (beaconId != null) "Open event" else "Say hi"
                NudgeKind.RECONNECT_LULL, NudgeKind.ANNIVERSARY -> "Say hi"
                NudgeKind.MEMORY_PROMPT -> "Add memory"
                NudgeKind.GROUP_REVIVAL -> "Plan"
                NudgeKind.WAVE -> "Wave back"
                NudgeKind.HANGOUT_CONFIRM -> "Confirm"
            }

    /** Secondary button label: hangout confirmations decline instead of dismissing. */
    val secondaryActionTitle: String
        get() = if (kind == NudgeKind.HANGOUT_CONFIRM) "Not us" else "Dismiss"

    /** The primary action, or null when the payload lacks what the action needs. */
    fun primaryAction(): NudgeAction? =
        when (kind) {
            NudgeKind.HANGOUT_CONFIRM -> confirmationId?.let { NudgeAction.ConfirmHangout(it) }
            NudgeKind.WAVE -> connectionId?.let { NudgeAction.WaveBack(it) }
            NudgeKind.GROUP_REVIVAL -> chatId?.let { NudgeAction.OpenGroupChat(it) }
            NudgeKind.MEMORY_PROMPT ->
                peerUserId?.let { NudgeAction.OpenProfile(it, connectionId) }
                    ?: connectionId?.let { NudgeAction.OpenChat(it) }
            NudgeKind.SHARED_UPCOMING_EVENT ->
                beaconId?.let { NudgeAction.OpenEvent(it) }
                    ?: connectionId?.let { NudgeAction.OpenChat(it) }
            NudgeKind.RECONNECT_LULL, NudgeKind.ANNIVERSARY -> connectionId?.let { NudgeAction.OpenChat(it) }
        }

    /**
     * Hangout confirmations are resolved by `/api/hangouts/{id}/confirm|decline` themselves; marking
     * them `acted` without calling those would silently drop the confirmation. Wave-back only
     * resolves a same-day wave server-side, so waves are still marked acted by the client.
     */
    val resolvedByHangoutEndpoints: Boolean
        get() = kind == NudgeKind.HANGOUT_CONFIRM

    private fun payloadString(key: String): String? =
        (dto.payload?.get(key) as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

fun InboxNudgeDto.typed(): InboxNudge? = NudgeKind.fromWire(nudgeType)?.let { InboxNudge(this, it) }

/**
 * Home "opportunity" priority for server nudges (iOS `HomeFeedModel`); lower is more urgent.
 * Event-bookmark opportunities and the say-hi deadline are ranked by Home itself around these.
 */
fun NudgeKind.homePriority(): Int =
    when (this) {
        NudgeKind.HANGOUT_CONFIRM -> 0
        NudgeKind.SHARED_UPCOMING_EVENT -> 1
        NudgeKind.WAVE -> 2
        NudgeKind.ANNIVERSARY -> 3
        NudgeKind.RECONNECT_LULL -> 4
        NudgeKind.MEMORY_PROMPT -> 5
        NudgeKind.GROUP_REVIVAL -> 6
    }
