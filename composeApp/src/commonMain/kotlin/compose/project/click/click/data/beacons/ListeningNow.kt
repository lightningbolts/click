package compose.project.click.click.data.beacons

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebRequestException // pragma: allowlist secret
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/*
 * "Listening now" on a soundtrack beacon (flag `soundtrack_presence`; click-web
 * `app/api/beacons/[beaconId]/listening/route.ts`; iOS `SoundtrackPresence.swift`). A count of people
 * listening from anywhere, names only for your own connections. No likes, no rankings, no location.
 */

data class ListeningPerson(
    val userId: String,
    val name: String,
)

data class ListeningNow(
    val count: Int,
    val isListening: Boolean,
    val connections: List<ListeningPerson>,
    /** How often a listener re-confirms while they stay (server: half its TTL; at least 60 s). */
    val heartbeatSeconds: Int,
) {
    /** "Maya is listening", "Maya and Sam are listening", "Maya, Sam and 3 others are listening" (iOS). */
    val summary: String?
        get() {
            if (count <= 0) return null
            val names = connections.take(2).map { it.name }
            val others = count - connections.size - (if (isListening) 1 else 0)
            return when {
                names.isEmpty() -> if (isListening && count == 1) "You're listening here" else "$count listening now"
                names.size == 1 && others <= 0 -> "${names[0]} is listening"
                names.size == 2 && others <= 0 -> "${names[0]} and ${names[1]} are listening"
                else -> {
                    val rest = count - names.size - (if (isListening) 1 else 0)
                    "${names.joinToString(", ")} and $rest other${if (rest == 1) "" else "s"} are listening"
                }
            }
        }

    companion object {
        fun parse(root: JsonObject): ListeningNow {
            fun prim(key: String) = root[key] as? JsonPrimitive
            val people =
                (root["connections"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val row = element as? JsonObject ?: return@mapNotNull null
                    val id = (row["user_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    ListeningPerson(id, (row["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Someone")
                }
            return ListeningNow(
                count = prim("count")?.intOrNull ?: 0,
                isListening = prim("is_listening")?.booleanOrNull == true,
                connections = people,
                heartbeatSeconds = maxOf(60, prim("heartbeat_seconds")?.intOrNull ?: 360),
            )
        }
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

private suspend fun ApiClient.listeningResult(response: HttpResponse): Result<ListeningNow> =
    if (response.status.value in 200..299) {
        (lenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject)
            ?.let { Result.success(ListeningNow.parse(it)) }
            ?: Result.failure(Exception("Unexpected response"))
    } else {
        Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
    }

private fun url(beaconId: String) = "${ApiClient.clickWebAuthOrigin}/api/beacons/$beaconId/listening"

/** GET — a 404 means the flag is off or the soundtrack ended: hide the section. */
internal suspend fun ApiClient.getListening(beaconId: String): Result<ListeningNow> =
    runCatching { clickWebClient.get(url(beaconId)) }.fold({ listeningResult(it) }, { Result.failure(it) })

/** POST — "I'm listening" heartbeat. Works from anywhere; no location is sent. */
internal suspend fun ApiClient.startListening(beaconId: String): Result<ListeningNow> =
    runCatching { clickWebClient.post(url(beaconId)) }.fold({ listeningResult(it) }, { Result.failure(it) })

/** DELETE — stop now instead of waiting for the heartbeat to lapse. */
internal suspend fun ApiClient.stopListening(beaconId: String): Result<ListeningNow> =
    runCatching { clickWebClient.delete(url(beaconId)) }.fold({ listeningResult(it) }, { Result.failure(it) })
