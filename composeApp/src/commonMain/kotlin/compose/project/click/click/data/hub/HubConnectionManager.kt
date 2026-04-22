package compose.project.click.click.data.hub

import compose.project.click.click.data.SupabaseConfig
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

sealed class HubVerifyResult {
    data class Success(
        val hubId: String,
        val name: String,
        val channel: String,
    ) : HubVerifyResult()

    data class Failure(val userMessage: String) : HubVerifyResult()
}

@Serializable
private data class HubVerifyOkResponse(
    val success: Boolean = false,
    @SerialName("hub_id") val hubId: String? = null,
    val name: String? = null,
    val channel: String? = null,
)

@Serializable
private data class HubVerifyErrBody(
    val error: String? = null,
)

@Serializable
private data class HubVerifyRequestBody(
    @SerialName("hub_id") val hubId: String,
    @SerialName("user_lat") val userLat: Double,
    @SerialName("user_long") val userLong: Double,
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
            val response = httpClient.post(url) {
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
                val dto = runCatching { json.decodeFromString(HubVerifyOkResponse.serializer(), text) }
                    .getOrNull()
                if (dto?.success == true && !dto.hubId.isNullOrBlank() && !dto.channel.isNullOrBlank()) {
                    HubVerifyResult.Success(
                        hubId = dto.hubId,
                        name = dto.name ?: dto.hubId,
                        channel = dto.channel,
                    )
                } else {
                    HubVerifyResult.Failure("Could not verify hub access.")
                }
            } else {
                val errText = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                val errMsg = runCatching {
                    json.decodeFromString(HubVerifyErrBody.serializer(), errText).error
                }.getOrNull()
                when (response.status) {
                    HttpStatusCode.Forbidden ->
                        HubVerifyResult.Failure(errMsg ?: "You need to be closer to this hub to join.")
                    HttpStatusCode.NotFound ->
                        HubVerifyResult.Failure(errMsg ?: "This hub is not available.")
                    else ->
                        HubVerifyResult.Failure(errMsg ?: "Could not verify location (${response.status.value}).")
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
}
