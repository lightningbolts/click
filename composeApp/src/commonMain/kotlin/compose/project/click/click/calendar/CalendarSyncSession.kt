package compose.project.click.click.calendar

import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Ephemeral realtime exchange of device free/busy during a reconnect bump.
 * No Postgres persistence — broadcast only.
 */
object CalendarSyncSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var channel: RealtimeChannel? = null
    private var collectJob: Job? = null
    private val _peerCalendar = MutableStateFlow<CalendarFreeBusy?>(null)
    val peerCalendar: StateFlow<CalendarFreeBusy?> = _peerCalendar.asStateFlow()

    suspend fun start(connectionId: String, currentUserId: String, peerUserId: String) {
        if (connectionId.isBlank() || currentUserId.isBlank()) return
        mutex.withLock {
            stopLocked()
            val ch = SupabaseConfig.client.channel("room:calendar_sync:$connectionId") {
                broadcast {
                    receiveOwnBroadcasts = false
                }
            }
            channel = ch
            collectJob = scope.launch {
                ch.broadcastFlow<CalendarFreeBusyBroadcast>(event = "free_busy").collect { payload ->
                    if (payload.userId != currentUserId && payload.userId == peerUserId) {
                        _peerCalendar.value = CalendarFreeBusy(
                            busyBlocks = payload.busyBlocks,
                            windowStartEpochMs = payload.windowStartEpochMs,
                            windowEndEpochMs = payload.windowEndEpochMs,
                        )
                    }
                }
            }
            try {
                ch.subscribe(blockUntilSubscribed = true)
            } catch (_: Exception) {
                runCatching { ch.subscribe() }
            }
        }
    }

    suspend fun publishLocal(connectionId: String, userId: String, snapshot: CalendarFreeBusy) {
        if (connectionId.isBlank() || userId.isBlank()) return
        val ch = mutex.withLock { channel } ?: return
        try {
            ch.broadcast(
                event = "free_busy",
                message = buildJsonObject {
                    put("user_id", userId)
                    put("window_start_ms", snapshot.windowStartEpochMs)
                    put("window_end_ms", snapshot.windowEndEpochMs)
                    putJsonArray("busy_blocks") {
                        snapshot.busyBlocks.forEach { block ->
                            add(
                                buildJsonObject {
                                    put("start_ms", block.startEpochMs)
                                    put("end_ms", block.endEpochMs)
                                },
                            )
                        }
                    }
                },
            )
        } catch (_: Exception) {
            // Ephemeral — ignore send failures.
        }
    }

    suspend fun stop() {
        mutex.withLock { stopLocked() }
    }

    private suspend fun stopLocked() {
        collectJob?.cancel()
        collectJob = null
        _peerCalendar.value = null
        channel?.let { ch ->
            runCatching { ch.unsubscribe() }
        }
        channel = null
    }
}
