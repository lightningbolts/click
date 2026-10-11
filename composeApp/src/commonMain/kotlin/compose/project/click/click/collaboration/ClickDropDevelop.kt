package compose.project.click.click.collaboration

import compose.project.click.click.core.FeatureFlags
import compose.project.click.click.core.FeatureFlagsState
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Server flag keys for Click Drops (`GET /api/me/features`). */
object ClickDropFlags {
    const val DROPS_DEVELOP = "drops_develop"
    const val SHARED_DROPS = "shared_drops"
}

/** Chat drops reveal 24 hours after they're sent (sheet 07 G2-1). */
const val CHAT_DROP_REVEAL_HOURS = 24

/**
 * Shared drops develop `shared_drops.config.develop_hours` after posting (1 when the flags haven't
 * loaded: the server seed). The server stamps `reveal_at`, so clients only need this for copy.
 */
fun sharedDropDevelopHours(flags: FeatureFlagsState = FeatureFlags.state.value): Int = flags.sharedDrops.developHours

/** Reveal time stamped on a new chat drop (`collaboration_ttl`). */
fun chatClickDropRevealAtIso(now: Instant = Clock.System.now()): String = now.plus(CHAT_DROP_REVEAL_HOURS, DateTimeUnit.HOUR).toString()

/**
 * The shared Click Drop develop state machine (spec §2) for chat, event and shared drops. Mirrors
 * click-web `lib/drops/developState.ts` and iOS `ClickDropDevelopState`.
 *
 *   Pending   → before reveal: pixelated, countdown visible.
 *   Ready     → reveal passed, this viewer hasn't developed it. Quiet: no badge.
 *   Developed → the viewer tapped, or was watching when the timer hit zero. Permanent per viewer.
 */
sealed interface ClickDropDevelopState {
    /** [revealAt] is null when unknown (treated as never). */
    data class Pending(
        val revealAt: Instant?,
    ) : ClickDropDevelopState

    data object Ready : ClickDropDevelopState

    data object Developed : ClickDropDevelopState

    val isPending: Boolean get() = this is Pending

    companion object {
        fun resolve(
            revealAt: Instant?,
            developedAt: Instant?,
            now: Instant = Clock.System.now(),
        ): ClickDropDevelopState {
            if (revealAt == null || now < revealAt) return Pending(revealAt)
            return if (developedAt == null) Ready else Developed
        }
    }
}

enum class ClickDropKind(
    val wire: String,
) {
    CHAT("chat"),
    EVENT("event"),
    SHARED("shared"),
    ;

    companion object {
        fun fromWire(value: String?): ClickDropKind? = entries.firstOrNull { it.wire == value }
    }
}

data class ClickDropRef(
    val kind: ClickDropKind,
    val id: String,
)

/** One drop's answer from `POST /api/drops/develop`. */
data class ClickDropDevelopResult(
    val ref: ClickDropRef,
    val status: Status,
    val developedAt: Instant?,
    /** Short-lived signed URL for a gated original (null for legacy drops, whose media is the original). */
    val originalUrl: String?,
) {
    enum class Status(
        val wire: String,
    ) {
        DEVELOPED("developed"),
        PENDING("pending"),
        NOT_FOUND("not_found"),
    }
}

internal fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

internal fun parseInstantOrNull(raw: String?): Instant? = raw?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** `{ drops: [{ kind, id, status, developed_at?, url? }] }`; malformed rows are skipped. */
fun parseClickDropDevelopResults(root: JsonElement?): List<ClickDropDevelopResult> {
    val rows = (root as? JsonObject)?.get("drops") as? JsonArray ?: return emptyList()
    return rows.mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val kind = ClickDropKind.fromWire(row["kind"].stringOrNull()) ?: return@mapNotNull null
        val id = row["id"].stringOrNull() ?: return@mapNotNull null
        val status =
            ClickDropDevelopResult.Status.entries.firstOrNull { it.wire == row["status"].stringOrNull() }
                ?: return@mapNotNull null
        ClickDropDevelopResult(
            ref = ClickDropRef(kind, id),
            status = status,
            developedAt = parseInstantOrNull(row["developed_at"].stringOrNull()),
            originalUrl = row["url"].stringOrNull(),
        )
    }
}

/** `GET /api/drops/views` → `{ developed: { id: developed_at } }`. */
fun parseClickDropViews(root: JsonElement?): Map<String, Instant> {
    val developed = (root as? JsonObject)?.get("developed") as? JsonObject ?: return emptyMap()
    return developed.mapNotNull { (id, value) -> parseInstantOrNull(value.stringOrNull())?.let { id to it } }.toMap()
}
