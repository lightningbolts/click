package compose.project.click.click.data.realtime // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Keeps Home, Map and Clicks current without polling (iOS `LiveUpdates`, web #131): listens on the
 * viewer's private `user:<id>` topic and the shared private `beacons` topic for the content-free
 * `changed` hint `{kind, lat?, lng?}`, and publishes coalesced [batches]. Screens refetch through
 * the usual audience-checked APIs. Joining is authorized by RLS on `realtime.messages`, so the join
 * carries the user's JWT (supabase-kt sends the session token with `isPrivate = true`).
 */
object LiveUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startMutex = Mutex()

    private val _batches = MutableSharedFlow<LiveBatch>(extraBufferCapacity = 16)

    /** One emission per coalesced refetch round (600 ms per burst). */
    val batches: SharedFlow<LiveBatch> = _batches.asSharedFlow()

    private val coalescer = LiveUpdatesCoalescer(scope) { batch -> _batches.emit(batch) }

    private var boundUserId: String? = null
    private var userJob: Job? = null
    private var beaconsJob: Job? = null

    /** Joins both topics for [userId] (a no-op when already live for them). */
    suspend fun start(userId: String) {
        if (userId.isBlank()) return
        startMutex.withLock {
            if (boundUserId == userId && userJob?.isActive == true && beaconsJob?.isActive == true) return
            stopLocked()
            boundUserId = userId
            userJob = launchTopic("user:$userId") { coalescer.userTopicRejoined() }
            beaconsJob = launchTopic("beacons") { coalescer.beaconsTopicRejoined() }
        }
    }

    /** Sign-out: leave both topics and drop anything pending. */
    suspend fun stop() {
        startMutex.withLock { stopLocked() }
        coalescer.reset()
    }

    /** After a token refresh dropped the socket: rejoin with the new JWT, then catch up. */
    suspend fun rebind(userId: String) {
        // Only while live for this user: a sign-out stopped us, and must not be undone here.
        val wasBound =
            startMutex.withLock {
                (boundUserId == userId).also { bound -> if (bound) stopLocked() }
            }
        if (!wasBound) return
        start(userId)
        coalescer.userTopicRejoined()
        coalescer.beaconsTopicRejoined()
    }

    /** Foreground flushes accumulated hints and revives the topics; background only collects. */
    fun setActive(active: Boolean) {
        scope.launch {
            coalescer.setActive(active)
            if (active) boundUserId?.let { start(it) }
        }
    }

    private fun stopLocked() {
        userJob?.cancel()
        beaconsJob?.cancel()
        userJob = null
        beaconsJob = null
        boundUserId = null
    }

    /**
     * Subscribes with retry/backoff. Any join after the first (a socket reconnect that rejoined
     * the channel, or a retry here) is a rejoin: hints sent meanwhile were missed, so catch up.
     */
    private fun launchTopic(
        topic: String,
        onRejoined: suspend () -> Unit,
    ): Job =
        scope.launch {
            var attempt = 0
            var joinedOnce = false
            while (isActive) {
                var channel: RealtimeChannel? = null
                try {
                    EnsureFreshAccessToken.get()
                    runCatching { SupabaseConfig.client.realtime.connect() }
                    val active = SupabaseConfig.client.channel(topic) { isPrivate = true }
                    channel = active
                    coroutineScope {
                        launch {
                            active.broadcastFlow<JsonObject>(event = "changed").collect { payload ->
                                coalescer.receive(
                                    kindWire = (payload["kind"] as? JsonPrimitive)?.contentOrNull,
                                    latitude = (payload["lat"] as? JsonPrimitive)?.doubleOrNull,
                                    longitude = (payload["lng"] as? JsonPrimitive)?.doubleOrNull,
                                )
                            }
                        }
                        launch {
                            var wasSubscribed = false
                            active.status.collect { status ->
                                val subscribed = status == RealtimeChannel.Status.SUBSCRIBED
                                if (subscribed && !wasSubscribed) {
                                    if (joinedOnce) onRejoined()
                                    joinedOnce = true
                                    attempt = 0
                                }
                                wasSubscribed = subscribed
                            }
                        }
                        if (!active.subscribeWithTimeout()) error("subscribe timed out")
                    }
                    error("live updates flow completed")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    println("LiveUpdates: $topic failed (attempt $attempt): ${e.redactedRestMessage()}")
                    delay(minOf(30_000L, 500L * attempt))
                } finally {
                    channel?.let { ch -> scope.launch { runCatching { SupabaseConfig.client.realtime.removeChannel(ch) } } }
                }
            }
        }
}
