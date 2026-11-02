package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.collaboration.graphemeCount
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Emoji reactions on soundtracks and shared Click Drops: click-web `app/api/reactions/[kind]/[id]/route.ts`
 * (`lib/server/reactions.ts`, migration `20261007000000_reactions.sql`). One reaction per person per
 * thing, replaced by the next; `emoji: null` takes it back. Owners can't react to their own (403).
 * Mirrors iOS `Click/Core/Media/Reactions.swift`.
 */

enum class ReactionTarget(
    val wire: String,
) {
    SOUNDTRACK("soundtrack"),
    SHARED_DROP("shared_drop"),
}

/** One person's reaction as the viewer may see it. */
@Serializable
data class Reactor(
    @SerialName("user_id") val userId: String = "",
    val name: String = "Someone",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val emoji: String = "",
) {
    val firstName: String get() =
        name
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.takeIf { it.isNotEmpty() } ?: name
}

/** `{ mine, reactions, is_owner }`. Owners see everyone; others see their connections. Newest first. */
@Serializable
data class ReactionsState(
    val mine: String? = null,
    val reactions: List<Reactor> = emptyList(),
    @SerialName("is_owner") val isOwner: Boolean = false,
) {
    companion object {
        /** The quick palette shown first (click-web `lib/drops/reactionPalette.ts`); any single emoji is accepted. */
        val palette: List<String> = listOf("❤️", "🔥", "😂", "😍", "👏", "😮")
    }
}

@Serializable
internal data class ReactionPutBody(
    val emoji: String?,
)

/** The `/api/reactions/{kind}/{id}` path for [target]. */
internal fun reactionsPath(
    origin: String,
    target: ReactionTarget,
    id: String,
): String = "$origin/api/reactions/${target.wire}/${id.trim().encodeURLPathPart()}"

/**
 * The tray's optimistic state after picking [emoji] (null takes it back): only `mine` changes until
 * the server answers.
 */
internal fun ReactionsState?.withMine(emoji: String?): ReactionsState = (this ?: ReactionsState()).copy(mine = emoji)

/**
 * A best-effort client check for the "+" picker, mirroring click-web `isReactionEmoji`: exactly one
 * grapheme, at most 16 code points, no letters, digits or spaces, and at least one emoji-range code
 * point. The server stays the judge.
 */
internal fun isSingleReactionEmoji(value: String): Boolean {
    val text = value.trim()
    if (text.isEmpty() || graphemeCount(text) != 1) return false
    var codePoints = 0
    var pictographic = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val cp =
            if (c.isHighSurrogate() && i + 1 < text.length) {
                ((c.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) + 0x10000
            } else {
                c.code
            }
        if (cp <= 0xFFFF && (c.isLetterOrDigit() || c.isWhitespace())) return false
        if (cp >= 0x1F000 || cp in 0x2100..0x2BFF || cp == 0x00A9 || cp == 0x00AE || cp in 0x3030..0x303D || cp in 0x3297..0x3299) {
            pictographic = true
        }
        codePoints++
        i += if (cp > 0xFFFF) 2 else 1
    }
    return pictographic && codePoints <= 16
}

/** Reactions endpoints (fakeable in tests). */
interface ReactionsService {
    /** GET the target's reactions. 404 when the viewer can't see it (or its flag is off). */
    suspend fun reactions(
        target: ReactionTarget,
        id: String,
    ): Result<ReactionsState>

    /** PUT one emoji (replacing any earlier one), or null to take it back. */
    suspend fun react(
        target: ReactionTarget,
        id: String,
        emoji: String?,
    ): Result<ReactionsState>
}

class ReactionsApi(
    private val api: ApiClient = ApiClient(),
) : ReactionsService {
    override suspend fun reactions(
        target: ReactionTarget,
        id: String,
    ): Result<ReactionsState> =
        call {
            val response = api.clickWebClient.get(reactionsPath(ApiClient.clickWebAuthOrigin, target, id))
            if (response.status.value in 200..299) Result.success(response.body()) else api.clickWebFailure(response)
        }

    override suspend fun react(
        target: ReactionTarget,
        id: String,
        emoji: String?,
    ): Result<ReactionsState> =
        call {
            val response =
                api.clickWebClient.put(reactionsPath(ApiClient.clickWebAuthOrigin, target, id)) {
                    contentType(ContentType.Application.Json)
                    setBody(ReactionPutBody(emoji))
                }
            if (response.status.value in 200..299) Result.success(response.body()) else api.clickWebFailure(response)
        }

    private inline fun <T> call(block: () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
