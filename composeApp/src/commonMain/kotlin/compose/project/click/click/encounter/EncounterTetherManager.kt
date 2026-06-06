package compose.project.click.click.encounter

import compose.project.click.click.collaboration.CollaborationSessionManager
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
import kotlinx.coroutines.delay
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

/** Latest inbound tether ping for global UI (compass toast). */
typealias TetherPayload = TetherPingReceived

/**
 * Stateless tether pings over Supabase Realtime (no Postgres writes).
 * Channel: `room:encounter_{encounterId}` — [encounterId] is the collaboration session id.
 */
object EncounterTetherManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var channel: RealtimeChannel? = null
    private var collectJob: Job? = null
    private var sessionWatchJob: Job? = null
    private var activeEncounterId: String? = null
    private var currentUserId: String? = null
    private var peerNameResolver: ((String) -> String)? = null

    private val _incomingPing = MutableSharedFlow<TetherPingReceived>(extraBufferCapacity = 4)
    val incomingPing: SharedFlow<TetherPingReceived> = _incomingPing.asSharedFlow()

    private val _activeTetherPayload = MutableStateFlow<TetherPayload?>(null)
    val activeTetherPayload: StateFlow<TetherPayload?> = _activeTetherPayload.asStateFlow()

    private val _pingSent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pingSent: SharedFlow<Unit> = _pingSent.asSharedFlow()

    private var pingInFlight = false
    private var lastPingAtMs = 0L

    private const val PING_COOLDOWN_MS = 900L

    init {
        sessionWatchJob = scope.launch {
            CollaborationSessionManager.sessions.collect { sessions ->
                val selfId = currentUserId
                val activeSession = sessions.values
                    .filter { it.encounterId.isNotBlank() && it.isRollActive() }
                    .maxByOrNull { it.createdAtEpochMs }
                val encounterId = activeSession?.encounterId
                if (encounterId.isNullOrBlank() || selfId.isNullOrBlank()) {
                    return@collect
                }
                if (encounterId == activeEncounterId && channel != null) return@collect
                subscribeInternal(
                    encounterId = encounterId,
                    userId = selfId,
                    peerNameResolver = peerNameResolver ?: { "Friend" },
                )
            }
        }
    }

    fun clearActiveTetherPayload() {
        _activeTetherPayload.value = null
    }

    fun setCurrentUserId(userId: String?) {
        currentUserId = userId?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setPeerNameResolver(resolver: ((String) -> String)?) {
        peerNameResolver = resolver
    }

    suspend fun subscribe(encounterId: String, userId: String, peerNameResolver: (String) -> String) {
        if (encounterId.isBlank() || userId.isBlank()) return
        currentUserId = userId
        this.peerNameResolver = peerNameResolver
        subscribeInternal(encounterId, userId, peerNameResolver)
    }

    private suspend fun subscribeInternal(
        encounterId: String,
        userId: String,
        peerNameResolver: (String) -> String,
    ) {
        mutex.withLock {
            if (activeEncounterId == encounterId && channel != null) return
            teardownLocked()
            activeEncounterId = encounterId
            currentUserId = userId
            this.peerNameResolver = peerNameResolver
            val ch = SupabaseConfig.client.channel("room:encounter_$encounterId") {
                broadcast {
                    receiveOwnBroadcasts = false
                }
            }
            channel = ch
            collectJob = scope.launch {
                ch.broadcastFlow<TetherPingPayload>(event = "tether_ping").collect { payload ->
                    if (payload.senderId == userId) return@collect
                    val received = TetherPingReceived(
                        senderId = payload.senderId,
                        senderName = peerNameResolver(payload.senderId),
                        latitude = payload.lat,
                        longitude = payload.lng,
                        timestampMs = payload.timestamp,
                    )
                    _activeTetherPayload.value = received
                    _incomingPing.emit(received)
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
        val now = Clock.System.now().toEpochMilliseconds()
        if (pingInFlight || now - lastPingAtMs < PING_COOLDOWN_MS) return
        pingInFlight = true
        lastPingAtMs = now
        scope.launch {
            try {
                val locationService = LocationService()
                if (!locationService.hasLocationPermission()) {
                    locationService.requestLocationPermission()
                    delay(600L)
                }
                if (!locationService.hasLocationPermission()) return@launch

                val fix = locationService.getHighAccuracyLocation(timeoutMs = 8_000L) ?: return@launch

                val needsSubscribe = mutex.withLock {
                    activeEncounterId != encounterId || channel == null
                }
                if (needsSubscribe) {
                    subscribeInternal(
                        encounterId = encounterId,
                        userId = senderId,
                        peerNameResolver = peerNameResolver ?: { "Friend" },
                    )
                    delay(150L)
                }

                val ch = mutex.withLock { channel } ?: return@launch
                val payload = TetherPingPayload(
                    senderId = senderId,
                    lat = fix.latitude,
                    lng = fix.longitude,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
                ch.broadcast(
                    event = "tether_ping",
                    message = payload,
                )
                _pingSent.emit(Unit)
            } catch (_: Exception) {
                // Ephemeral — no persistence or retry UI required.
            } finally {
                pingInFlight = false
            }
        }
    }

    private suspend fun teardownLocked() {
        collectJob?.cancel()
        collectJob = null
        activeEncounterId = null
        channel?.let { ch ->
            runCatching { ch.unsubscribe() }
        }
        channel = null
    }
}
