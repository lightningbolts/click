package compose.project.click.click.telemetry

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ApiConfig
import compose.project.click.click.util.chatMediaDispatcher
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * First-party proximity handshake / connection-flow telemetry.
 *
 * Fire-and-forget POSTs to click-web BFF. No user ids, no raw GPS.
 * Success-path events are sampled (~10%); failures, awaiting selection, and
 * abandoned flows always emit.
 */
object ConnectionFlowTelemetry {

    private const val SUCCESS_SAMPLE_RATE = 0.10

    /** Events that always leave the device (funnel failures / selection / abandon). */
    private val ALWAYS_EMIT = setOf(
        "proximity_handshake_started",
        "proximity_handshake_awaiting_selection",
        "proximity_handshake_failed",
        "proximity_host_selection_abandoned",
        "proximity_reconnect_rate_limited",
        "proximity_recovery_poll_timeout",
        "proximity_recovery_incomplete",
        "verified_clique_from_proximity_blocked",
        "proximity_at_event_skipped",
    )

    private val ALLOWED_EVENTS = setOf(
        "proximity_handshake_started",
        "proximity_handshake_matched",
        "proximity_handshake_awaiting_selection",
        "proximity_handshake_pending",
        "proximity_handshake_offline_queued",
        "proximity_handshake_failed",
        "proximity_host_selection_confirmed",
        "proximity_host_selection_abandoned",
        "proximity_reconnect_encounter_saved",
        "proximity_reconnect_rate_limited",
        "proximity_recovery_poll_success",
        "proximity_recovery_poll_timeout",
        "proximity_recovery_incomplete",
        "verified_clique_from_proximity_created",
        "verified_clique_from_proximity_blocked",
        "proximity_at_event_attached",
        "proximity_at_event_skipped",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = true
    }

    private val httpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Serializable
    private data class ConnectionFlowPayload(
        val event: String,
        @SerialName("peer_count") val peerCount: Int? = null,
        @SerialName("is_group") val isGroup: Boolean? = null,
        @SerialName("is_reconnect") val isReconnect: Boolean? = null,
        @SerialName("selected_count") val selectedCount: Int? = null,
        @SerialName("candidate_count") val candidateCount: Int? = null,
        val reason: String? = null,
    )

    fun recordStarted(
        peerCount: Int? = null,
        isGroup: Boolean? = null,
        isReconnect: Boolean? = null,
    ) = record(
        event = "proximity_handshake_started",
        peerCount = peerCount,
        isGroup = isGroup,
        isReconnect = isReconnect,
    )

    fun recordMatched(
        peerCount: Int,
        isGroup: Boolean = false,
        isReconnect: Boolean = false,
    ) = record(
        event = "proximity_handshake_matched",
        peerCount = peerCount,
        isGroup = isGroup,
        isReconnect = isReconnect,
    )

    fun recordAwaitingSelection(
        peerCount: Int,
        candidateCount: Int? = null,
        isGroup: Boolean = false,
        isReconnect: Boolean = false,
    ) = record(
        event = "proximity_handshake_awaiting_selection",
        peerCount = peerCount,
        candidateCount = candidateCount ?: peerCount,
        isGroup = isGroup,
        isReconnect = isReconnect,
    )

    fun recordPending(
        peerCount: Int? = null,
        isGroup: Boolean? = null,
    ) = record(
        event = "proximity_handshake_pending",
        peerCount = peerCount,
        isGroup = isGroup,
    )

    fun recordOfflineQueued(
        peerCount: Int? = null,
        reason: String? = null,
    ) = record(
        event = "proximity_handshake_offline_queued",
        peerCount = peerCount,
        reason = reason,
    )

    fun recordFailed(reason: String? = null) = record(
        event = "proximity_handshake_failed",
        reason = reason,
    )

    fun recordHostSelectionConfirmed(
        selectedCount: Int,
        candidateCount: Int? = null,
        isGroup: Boolean = false,
        isReconnect: Boolean = false,
    ) = record(
        event = "proximity_host_selection_confirmed",
        selectedCount = selectedCount,
        candidateCount = candidateCount,
        peerCount = selectedCount,
        isGroup = isGroup,
        isReconnect = isReconnect,
    )

    fun recordHostSelectionAbandoned(
        candidateCount: Int? = null,
        reason: String? = null,
    ) = record(
        event = "proximity_host_selection_abandoned",
        candidateCount = candidateCount,
        reason = reason,
    )

    fun recordReconnectEncounterSaved(
        peerCount: Int = 1,
        isGroup: Boolean = false,
    ) = record(
        event = "proximity_reconnect_encounter_saved",
        peerCount = peerCount,
        isGroup = isGroup,
        isReconnect = true,
    )

    fun recordReconnectRateLimited(
        peerCount: Int? = null,
        isGroup: Boolean? = null,
    ) = record(
        event = "proximity_reconnect_rate_limited",
        peerCount = peerCount,
        isGroup = isGroup,
        isReconnect = true,
    )

    fun recordRecoveryPollSuccess(
        peerCount: Int? = null,
        isGroup: Boolean? = null,
        isReconnect: Boolean? = null,
    ) = record(
        event = "proximity_recovery_poll_success",
        peerCount = peerCount,
        isGroup = isGroup,
        isReconnect = isReconnect,
    )

    fun recordRecoveryPollTimeout(reason: String? = null) = record(
        event = "proximity_recovery_poll_timeout",
        reason = reason,
    )

    fun recordRecoveryIncomplete(reason: String? = null) = record(
        event = "proximity_recovery_incomplete",
        reason = reason,
    )

    fun recordVerifiedCliqueCreated(
        peerCount: Int,
        selectedCount: Int? = null,
        candidateCount: Int? = null,
    ) = record(
        event = "verified_clique_from_proximity_created",
        peerCount = peerCount,
        selectedCount = selectedCount,
        candidateCount = candidateCount,
        isGroup = true,
    )

    fun recordVerifiedCliqueBlocked(
        peerCount: Int? = null,
        candidateCount: Int? = null,
        reason: String? = null,
    ) = record(
        event = "verified_clique_from_proximity_blocked",
        peerCount = peerCount,
        candidateCount = candidateCount,
        isGroup = true,
        reason = reason,
    )

    fun recordAtEventAttached(
        peerCount: Int? = null,
        isGroup: Boolean? = null,
    ) = record(
        event = "proximity_at_event_attached",
        peerCount = peerCount,
        isGroup = isGroup,
    )

    fun recordAtEventSkipped(
        reason: String? = null,
        peerCount: Int? = null,
        isGroup: Boolean? = null,
    ) = record(
        event = "proximity_at_event_skipped",
        peerCount = peerCount,
        isGroup = isGroup,
        reason = reason,
    )

    /**
     * Fire-and-forget record. Samples success-path events at [SUCCESS_SAMPLE_RATE];
     * always emits events in [ALWAYS_EMIT].
     */
    fun record(
        event: String,
        peerCount: Int? = null,
        isGroup: Boolean? = null,
        isReconnect: Boolean? = null,
        selectedCount: Int? = null,
        candidateCount: Int? = null,
        reason: String? = null,
    ) {
        val trimmed = event.trim()
        if (trimmed !in ALLOWED_EVENTS) return
        if (trimmed !in ALWAYS_EMIT && Random.nextDouble() >= SUCCESS_SAMPLE_RATE) return

        val payload = ConnectionFlowPayload(
            event = trimmed,
            peerCount = peerCount?.coerceAtLeast(0),
            isGroup = isGroup,
            isReconnect = isReconnect,
            selectedCount = selectedCount?.coerceAtLeast(0),
            candidateCount = candidateCount?.coerceAtLeast(0),
            reason = reason?.trim()?.take(128)?.takeIf { it.isNotEmpty() },
        )
        scope.launch {
            postEvent(payload)
        }
    }

    private suspend fun postEvent(body: ConnectionFlowPayload) {
        val token = runCatching {
            SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return

        val url = "${ApiConfig.CLICK_WEB_BASE_URL.trimEnd('/')}/api/telemetry/connection-flow"

        runCatching {
            withContext(chatMediaDispatcher) {
                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(body)
                }
                if (response.status.isSuccess()) {
                    println("ConnectionFlowTelemetry: POST ok event=${body.event}")
                } else {
                    println("ConnectionFlowTelemetry: POST failed status=${response.status.value}")
                }
            }
        }.onFailure { e ->
            println("ConnectionFlowTelemetry: POST error: ${e.redactedRestMessage()}")
        }
    }
}
