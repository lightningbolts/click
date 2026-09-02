package compose.project.click.click.data.hub

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed class HubVerifyResult {
    data class Success(
        val hubId: String,
        val name: String,
        val channel: String,
        val creatorId: String? = null,
    ) : HubVerifyResult()

    data class Failure(
        val userMessage: String,
        val code: String? = null,
    ) : HubVerifyResult()
}

@Serializable
private data class HubVerifyOkResponse(
    val success: Boolean = false,
    @SerialName("hub_id") val hubId: String? = null,
    val name: String? = null,
    val channel: String? = null,
    @SerialName("creator_id") val creatorId: String? = null,
)

@Serializable
private data class HubVerifyErrBody(
    val error: JsonElement? = null,
    val code: String? = null,
    val message: String? = null,
)

@Serializable
private data class HubVerifyRequestBody(
    @SerialName("hub_id") val hubId: String,
    @SerialName("user_lat") val userLat: Double,
    @SerialName("user_long") val userLong: Double,
)

@Serializable
private data class HubJoinRequestBody(
    @SerialName("hub_id") val hubId: String,
)

/**
 * Calls the Supabase Edge Function [verify-hub-proximity] with the user's coordinates.
 */
object HubConnectionManager {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verifyProximity(
        httpClient: HttpClient,
        hubId: String,
        userLat: Double,
        userLong: Double,
        bearerJwt: String,
    ): HubVerifyResult {
        val url = SupabaseConfig.functionUrl("verify-hub-proximity")
        return try {
            val response =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append("apikey", SupabaseConfig.supabaseAnonApiKey)
                        append(HttpHeaders.Authorization, "Bearer $bearerJwt")
                    }
                    setBody(
                        HubVerifyRequestBody(
                            hubId = hubId,
                            userLat = userLat,
                            userLong = userLong,
                        ),
                    )
                }
            if (response.status.isSuccess()) {
                val text = response.bodyAsText()
                val dto =
                    runCatching { json.decodeFromString(HubVerifyOkResponse.serializer(), text) }
                        .getOrNull()
                if (dto?.success == true && !dto.hubId.isNullOrBlank() && !dto.channel.isNullOrBlank()) {
                    HubVerifyResult.Success(
                        hubId = dto.hubId,
                        name = dto.name ?: dto.hubId,
                        channel = dto.channel,
                        creatorId = dto.creatorId?.trim()?.takeIf { it.isNotEmpty() },
                    )
                } else {
                    HubVerifyResult.Failure("Could not verify hub access.")
                }
            } else {
                val errText = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                val structuredError = parseHubError(json, errText)
                val errMsg = structuredError.message
                when (response.status) {
                    HttpStatusCode.Forbidden ->
                        HubVerifyResult.Failure(
                            errMsg ?: "Check in to this event to join the hub.",
                            structuredError.code,
                        )
                    HttpStatusCode.NotFound -> HubVerifyResult.Failure(errMsg ?: "This hub is not available.", structuredError.code)
                    else -> HubVerifyResult.Failure(errMsg ?: "Could not verify location (${response.status.value}).", structuredError.code)
                }
            }
        } catch (_: ClientRequestException) {
            HubVerifyResult.Failure("Network error while verifying hub.")
        } catch (_: ServerResponseException) {
            HubVerifyResult.Failure("Server error while verifying hub.")
        } catch (e: Exception) {
            HubVerifyResult.Failure(e.message ?: "Could not verify hub.")
        }
    }

    /**
     * Event hubs skip GPS — click-web checks check-in / host.
     */
    suspend fun joinEventHub(
        httpClient: HttpClient,
        hubId: String,
        bearerJwt: String,
        clickWebBaseUrl: String = CLICK_WEB_BASE_URL.trimEnd('/'),
    ): HubVerifyResult {
        val url = "$clickWebBaseUrl/api/hub/join"
        return try {
            val response =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $bearerJwt")
                    }
                    setBody(HubJoinRequestBody(hubId = hubId))
                }
            if (response.status.isSuccess()) {
                val text = response.bodyAsText()
                val dto =
                    runCatching { json.decodeFromString(HubVerifyOkResponse.serializer(), text) }
                        .getOrNull()
                if (dto?.success == true && !dto.hubId.isNullOrBlank() && !dto.channel.isNullOrBlank()) {
                    HubVerifyResult.Success(
                        hubId = dto.hubId,
                        name = dto.name ?: dto.hubId,
                        channel = dto.channel,
                        creatorId = dto.creatorId?.trim()?.takeIf { it.isNotEmpty() },
                    )
                } else {
                    HubVerifyResult.Failure("Could not join the event hub.")
                }
            } else {
                val errText = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                val structuredError = parseHubError(json, errText)
                val errMsg = structuredError.message
                when (response.status) {
                    HttpStatusCode.Forbidden ->
                        HubVerifyResult.Failure(errMsg ?: "Check in to this event to join the hub.", structuredError.code)
                    HttpStatusCode.Gone ->
                        HubVerifyResult.Failure(errMsg ?: "This hub is no longer active.", structuredError.code)
                    HttpStatusCode.NotFound ->
                        HubVerifyResult.Failure(errMsg ?: "This hub is not available.", structuredError.code)
                    else -> HubVerifyResult.Failure(errMsg ?: "Could not join hub (${response.status.value}).", structuredError.code)
                }
            }
        } catch (_: ClientRequestException) {
            HubVerifyResult.Failure("Network error while joining hub.")
        } catch (_: ServerResponseException) {
            HubVerifyResult.Failure("Server error while joining hub.")
        } catch (e: Exception) {
            HubVerifyResult.Failure(e.message ?: "Could not join hub.")
        }
    }
}

internal data class HubStructuredError(
    val code: String? = null,
    val message: String? = null,
)

/** Supports both legacy `{ error: "..." }` and v2 `{ error: { code, message } }` responses. */
internal fun parseHubError(
    json: Json,
    body: String,
): HubStructuredError {
    val payload =
        runCatching { json.decodeFromString(HubVerifyErrBody.serializer(), body) }.getOrNull()
            ?: return HubStructuredError()
    val nested = payload.error as? JsonObject
    val code =
        payload.code?.trim()?.takeIf { it.isNotEmpty() }
            ?: nested
                ?.get("code")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
    val rawMessage =
        payload.message?.trim()?.takeIf { it.isNotEmpty() }
            ?: nested
                ?.get("message")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: (payload.error as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val message =
        when (code) {
            "HUB_EXPIRED" -> "This hub is no longer active."
            "EVENT_HUB_ACCESS_DENIED", "NOT_A_PARTICIPANT" -> "Check in to this event to join the hub."
            else -> rawMessage?.take(280)
        }
    return HubStructuredError(code = code, message = message)
}
