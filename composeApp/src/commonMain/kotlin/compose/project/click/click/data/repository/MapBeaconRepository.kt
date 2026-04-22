package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconRows // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Fetches community map beacons and inserts new drops via Supabase Edge Function + PostgREST.
 */
class MapBeaconRepository(
    private val tokenStorage: TokenStorage = createTokenStorage(),
    httpClient: HttpClient? = null,
) {
    private val ownsClient = httpClient == null
    private val client = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    fun close() {
        if (ownsClient) client.close()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchLocalBeacons(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): Result<List<MapBeacon>> {
        val jwt = tokenStorage.getJwt()
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val body = buildJsonObject {
                put("min_lat", JsonPrimitive(minLat))
                put("max_lat", JsonPrimitive(maxLat))
                put("min_lon", JsonPrimitive(minLon))
                put("max_lon", JsonPrimitive(maxLon))
            }
            val response = client.post(SupabaseConfig.functionUrl("fetch-local-beacons")) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append("apikey", SupabaseConfig.supabaseAnonApiKey)
                    append(HttpHeaders.Authorization, "Bearer $jwt")
                }
                setBody(body)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("fetch-local-beacons: ${response.status.value} $text"))
            }
            val rows = parseBeaconResponsePayload(text)
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertBeacon(insert: MapBeaconInsert): Result<Unit> =
        try {
            SupabaseConfig.client.from("map_beacons").insert(insert)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    private fun parseBeaconResponsePayload(text: String): List<MapBeacon> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "null") return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(trimmed)
            when (root) {
                is JsonObject -> {
                    val rows = root["beacons"] ?: root["data"] ?: root["rows"] ?: root["items"]
                    when (rows) {
                        null -> {
                            if (root.keys.any { it.equals("id", true) || it == "kind" }) {
                                parseMapBeaconRows(root)
                            } else {
                                emptyList()
                            }
                        }
                        else -> parseMapBeaconRows(rows)
                    }
                }
                else -> parseMapBeaconRows(root)
            }
        }.getOrElse {
            runCatching { parseMapBeaconRows(json.parseToJsonElement(trimmed)) }.getOrDefault(emptyList())
        }
    }
}
