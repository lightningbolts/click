package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Emoji reactions on soundtracks and shared Click Drops (click-web
 * `app/api/reactions/[kind]/[id]/route.ts`, sheet 08 G1-7). One reaction per person, replaced by
 * the next; `emoji: null` takes it back. Owners can't react to their own (403).
 */

enum class ReactionTarget(
    val wire: String,
) {
    SOUNDTRACK("soundtrack"),
    SHARED_DROP("shared_drop"),
}

@Serializable
data class Reactor(
    @SerialName("user_id") val userId: String = "",
    val name: String = "Someone",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val emoji: String = "",
) {
    val firstName: String get() = name.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() } ?: name
}

/** `{ mine, reactions, is_owner }`. Owners see everyone; others their connections. Newest first. */
@Serializable
data class ReactionsState(
    val mine: String? = null,
    val reactions: List<Reactor> = emptyList(),
    @SerialName("is_owner") val isOwner: Boolean = false,
) {
    companion object {
        /** The quick palette shown first (click-web `REACTION_EMOJI`); any single emoji is accepted. */
        val palette: List<String> = listOf("❤️", "🔥", "😂", "😍", "👏", "😮")
    }
}

@Serializable
internal data class ReactionPutBody(
    val emoji: String?,
)

class ReactionsApi(
    private val api: ApiClient = ApiClient(),
) {
    private fun path(
        target: ReactionTarget,
        id: String,
    ) = "${ApiClient.clickWebAuthOrigin}/api/reactions/${target.wire}/${id.trim().encodeURLPathPart()}"

    /** GET the target's reactions. 404 when the viewer can't see it (or its flag is off). */
    suspend fun reactions(
        target: ReactionTarget,
        id: String,
    ): Result<ReactionsState> =
        try {
            val response = api.clickWebClient.get(path(target, id))
            if (response.status.value in 200..299) Result.success(response.body()) else api.clickWebFailure(response)
        } catch (e: ClientRequestException) {
            api.clickWebFailure(e.response)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** PUT one emoji (replacing any earlier one), or null to take it back. */
    suspend fun react(
        target: ReactionTarget,
        id: String,
        emoji: String?,
    ): Result<ReactionsState> =
        try {
            val response =
                api.clickWebClient.put(path(target, id)) {
                    contentType(ContentType.Application.Json)
                    setBody(ReactionPutBody(emoji))
                }
            if (response.status.value in 200..299) Result.success(response.body()) else api.clickWebFailure(response)
        } catch (e: ClientRequestException) {
            api.clickWebFailure(e.response)
        } catch (e: Exception) {
            Result.failure(e)
        }
}
