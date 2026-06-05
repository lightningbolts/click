package compose.project.click.click.encounter

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.utils.LocationService
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Stateless tether pings over Supabase Realtime (no Postgres writes).
 * Channel: `room:encounter_{encounterId}`
 */
object EncounterTetherManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var channel: RealtimeChannel? = null
    private var collectJob: Job? = null
    private var activeEncounterId: String? = null
    private var currentUserId: String? = null

    private val _incomingPing = MutableSharedFlow<TetherPingReceived>(extraBufferCapacity = 4)
    val incomingPing: SharedFlow<TetherPingReceived> = _incomingPing.asSharedFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    suspend fun subscribe(encounterId: String, userId: String, peerNameResolver: (String) -> String) {
        if (encounterId.isBlank() || userId.isBlank()) return
        mutex.withLock {
            if (activeEncounterId == encounterId && channel != null) return
            teardownLocked()
            activeEncounterId = encounterId
            currentUserId = userId
            val ch = SupabaseConfig.client.channel("room:encounter_$encounterId") {
                broadcast {
                    receiveOwnBroadcasts = false
                }
            }
            channel = ch
            collectJob = scope.launch {
                ch.broadcastFlow<TetherPingPayload>(event = "tether_ping").collect { payload ->
                    if (payload.senderId == userId) return@collect
                    _incomingPing.emit(
                        TetherPingReceived(
                            senderId = payload.senderId,
                            senderName = peerNameResolver(payload.senderId),
                            latitude = payload.lat,
                            longitude = payload.lng,
                            timestampMs = payload.timestamp,
                        ),
                    )
                }
            }
            try {
                ch.subscribe(blockUntilSubscribed = true)
            } catch (_: Exception) {
                runCatching { ch.subscribe() }
            }
        }
    }

    suspend fun unsubscribe() {
        mutex.withLock { teardownLocked() }
    }

    fun pingTether(encounterId: String, senderId: String) {
        if (encounterId.isBlank() || senderId.isBlank()) return
        scope.launch {
            if (_isPinging.value) return@launch
            _isPinging.value = true
            try {
                val locationService = LocationService()
                if (!locationService.hasLocationPermission()) {
                    locationService.requestLocationPermission()
                    return@launch
                }
                val fix = locationService.getHighAccuracyLocation(timeoutMs = 5_000L) ?: return@launch
                val ch = mutex.withLock { channel }
                    ?: SupabaseConfig.client.channel("room:encounter_$encounterId").also { newCh ->
                        channel = newCh
                        try {
                            newCh.subscribe(blockUntilSubscribed = true)
                        } catch (_: Exception) {
                            runCatching { newCh.subscribe() }
                        }
                    }
                ch.broadcast(
                    event = "tether_ping",
                    message = buildJsonObject {
                        put("sender_id", senderId)
                        put("lat", fix.latitude)
                        put("lng", fix.longitude)
                        put("timestamp", Clock.System.now().toEpochMilliseconds())
                    },
                )
            } catch (_: Exception) {
                // Ephemeral — no persistence or retry UI required.
            } finally {
                _isPinging.value = false
            }
        }
    }

    private suspend fun teardownLocked() {
        collectJob?.cancel()
        collectJob = null
        activeEncounterId = null
        currentUserId = null
        channel?.let { ch ->
            runCatching { ch.unsubscribe() }
        }
        channel = null
    }
}
