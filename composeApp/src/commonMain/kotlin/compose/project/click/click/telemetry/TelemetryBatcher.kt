package compose.project.click.click.telemetry

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ApiConfig
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * In-memory friction telemetry batcher. Counts map friction on-device; only aggregated,
 * non-identifying payloads leave the device when session rules are met.
 */
object TelemetryBatcher {

    private const val ANOMALY_MIN_DURATION_SEC = 180L
    private const val GRASS_NUDGE_MIN_DURATION_SEC = 240L
    private const val ACTIVE_PAN_WINDOW_MS = 45_000L

    data class FrictionSession(
        val sessionStartTimeMs: Long = 0L,
        val mapPanCount: Int = 0,
        val qrFallbackCount: Int = 0,
        val actionTakenCount: Int = 0,
        val lastPanAtMs: Long = 0L,
        val hexbinId: String = AnonymizedHexbin.UNKNOWN_CELL,
    )

    data class FrictionUiState(
        val session: FrictionSession = FrictionSession(),
        val showGrassNudge: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(FrictionUiState())
    val uiState: StateFlow<FrictionUiState> = _uiState.asStateFlow()

    private var session = FrictionSession()
    private var mapSessionActive = false

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

    fun beginMapSession(hexbinId: String = session.hexbinId) {
        if (mapSessionActive) {
            if (hexbinId != AnonymizedHexbin.UNKNOWN_CELL) {
                session = session.copy(hexbinId = hexbinId)
                publishUi()
            }
            return
        }
        mapSessionActive = true
        val now = Clock.System.now().toEpochMilliseconds()
        session = FrictionSession(
            sessionStartTimeMs = now,
            hexbinId = hexbinId,
        )
        publishUi()
    }

    fun endMapSession() {
        mapSessionActive = false
    }

    fun updateHexbinFromCoordinates(latitude: Double?, longitude: Double?) {
        if (latitude == null || longitude == null) return
        val cell = AnonymizedHexbin.fromCoordinates(latitude, longitude)
        if (cell == session.hexbinId) return
        session = session.copy(hexbinId = cell)
        publishUi()
    }

    fun recordMapPan() {
        if (!mapSessionActive) beginMapSession()
        val now = Clock.System.now().toEpochMilliseconds()
        session = session.copy(
            mapPanCount = session.mapPanCount + 1,
            lastPanAtMs = now,
        )
        publishUi()
    }

    fun recordQrFallback() {
        session = session.copy(qrFallbackCount = session.qrFallbackCount + 1)
        publishUi()
    }

    fun recordActionTaken() {
        session = session.copy(
            actionTakenCount = session.actionTakenCount + 1,
            lastPanAtMs = 0L,
        )
        publishUi()
    }

    fun dismissGrassNudge() {
        _uiState.update { it.copy(showGrassNudge = false) }
    }

    /** Re-evaluates nudge visibility (e.g. when the 4-minute threshold elapses without a new pan). */
    fun refreshUiClock() {
        publishUi()
    }

    /**
     * Called when the app backgrounds. Never blocks the UI thread — work runs on [Dispatchers.Default].
     */
    fun onAppBackgrounded() {
        scope.launch {
            flushOnBackgroundIfNeeded()
        }
    }

    private suspend fun flushOnBackgroundIfNeeded() {
        if (!mapSessionActive && session.sessionStartTimeMs == 0L) return

        val now = Clock.System.now().toEpochMilliseconds()
        val durationSec = ((now - session.sessionStartTimeMs) / 1000L).coerceAtLeast(0L)
        val snapshot = session

        resetSession()

        if (durationSec <= ANOMALY_MIN_DURATION_SEC) return
        if (snapshot.actionTakenCount != 0) return

        postFrictionAnomaly(
            durationSec = durationSec,
            panCount = snapshot.mapPanCount,
            hexbinId = snapshot.hexbinId,
        )
    }

    private fun resetSession() {
        mapSessionActive = false
        session = FrictionSession()
        publishUi()
    }

    private fun publishUi() {
        val now = Clock.System.now().toEpochMilliseconds()
        val elapsedSec = if (session.sessionStartTimeMs > 0L) {
            ((now - session.sessionStartTimeMs) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        val recentlyPanning = session.lastPanAtMs > 0L &&
            (now - session.lastPanAtMs) <= ACTIVE_PAN_WINDOW_MS
        val showNudge = session.actionTakenCount == 0 &&
            session.mapPanCount > 0 &&
            elapsedSec >= GRASS_NUDGE_MIN_DURATION_SEC &&
            recentlyPanning

        _uiState.value = FrictionUiState(
            session = session,
            showGrassNudge = showNudge,
        )
    }

    @Serializable
    private data class FrictionAnomalyPayload(
        val event: String,
        @SerialName("duration_sec") val durationSec: Long,
        @SerialName("pan_count") val panCount: Int,
        @SerialName("action_taken") val actionTaken: Int? = null,
        @SerialName("hexbin_id") val hexbinId: String,
    )

    private suspend fun postFrictionAnomaly(
        durationSec: Long,
        panCount: Int,
        hexbinId: String,
    ) {
        val token = runCatching {
            SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return

        val url = "${ApiConfig.CLICK_WEB_BASE_URL.trimEnd('/')}/api/telemetry/friction"
        val body = FrictionAnomalyPayload(
            event = "map_friction_anomaly",
            durationSec = durationSec,
            panCount = panCount,
            actionTaken = null,
            hexbinId = hexbinId,
        )

        runCatching {
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                println("TelemetryBatcher: friction POST failed status=${response.status.value}")
            }
        }.onFailure { e ->
            println("TelemetryBatcher: friction POST error: ${e.redactedRestMessage()}")
        }
    }
}
