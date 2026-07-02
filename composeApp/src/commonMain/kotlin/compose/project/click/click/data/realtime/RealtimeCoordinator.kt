package compose.project.click.click.data.realtime

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.ChatMessageSubscription
import compose.project.click.click.data.repository.MessageListInsertEvent
import compose.project.click.click.data.repository.SupabaseChatRepository
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** App-scoped Realtime fan-in: one messages listener + one connections listener. */
object RealtimeCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chatRepository by lazy { SupabaseChatRepository(tokenStorage = createTokenStorage()) }
    private val startMutex = Mutex()

    private var boundUserId: String? = null
    private var messageSub: ChatMessageSubscription? = null
    private var messageCollectJob: Job? = null
    private var connectionsChannel: RealtimeChannel? = null
    private var connectionsCollectJob: Job? = null

    private val _messageInserts = MutableSharedFlow<MessageListInsertEvent>(extraBufferCapacity = 64)
    val messageInserts: SharedFlow<MessageListInsertEvent> = _messageInserts.asSharedFlow()

    private val _connectionJunctionChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val connectionJunctionChanged: SharedFlow<Unit> = _connectionJunctionChanged.asSharedFlow()

    /** Monotonic counter bumped on message insert or connection junction change. */
    private val _inboxVersion = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)
    val inboxVersion: SharedFlow<Long> = _inboxVersion.asSharedFlow()
    private var inboxVersionCounter = 0L

    fun currentInboxVersion(): Long = inboxVersionCounter

    suspend fun ensureStarted(userId: String) {
        if (userId.isBlank()) return
        startMutex.withLock {
            if (boundUserId == userId && messageCollectJob?.isActive == true) return
            stopLocked()
            boundUserId = userId
            startMessageListenerLocked()
            startConnectionsListenerLocked(userId)
        }
    }

    fun bumpInboxVersion() {
        scope.launch {
            startMutex.withLock { bumpInboxVersionLocked() }
        }
    }

    private fun bumpInboxVersionLocked() {
        inboxVersionCounter += 1L
        _inboxVersion.tryEmit(inboxVersionCounter)
    }

    fun stop() {
        scope.launch {
            startMutex.withLock { stopLocked() }
        }
    }

    private fun stopLocked() {
        messageCollectJob?.cancel()
        messageCollectJob = null
        connectionsCollectJob?.cancel()
        connectionsCollectJob = null
        runCatching { messageSub?.let { sub -> scope.launch { sub.detach() } } }
        messageSub = null
        connectionsChannel?.let { ch ->
            scope.launch { runCatching { ch.unsubscribe() } }
        }
        connectionsChannel = null
        boundUserId = null
    }

    private fun startMessageListenerLocked() {
        messageCollectJob = scope.launch {
            var attempt = 0
            while (isActive) {
                var sub: ChatMessageSubscription? = null
                try {
                    // subscribeToMessageInserts() registers postgresChangeFlow synchronously;
                    // attach() must run before collect() but after listener registration.
                    val (subscription, flow) = chatRepository.subscribeToMessageInserts()
                    sub = subscription
                    messageSub = subscription
                    subscription.attach()
                    attempt = 0
                    flow.collect { event ->
                        bumpInboxVersionLocked()
                        _messageInserts.emit(event)
                    }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    println(
                        "RealtimeCoordinator: message listener failed (attempt $attempt): " +
                            e.redactedRestMessage(),
                    )
                    delay(minOf(30_000L, 500L * attempt))
                } finally {
                    runCatching { sub?.detach() }
                    if (messageSub === sub) messageSub = null
                }
            }
        }
    }

    private fun startConnectionsListenerLocked(userId: String) {
        connectionsCollectJob = scope.launch {
            var debounceJob: Job? = null
            try {
                val channel = SupabaseConfig.client.channel("app:connections:$userId")
                connectionsChannel = channel
                merge(
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "connections" }
                        .filter { it is PostgresAction.Insert }
                        .map { },
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "connection_archives" }.map { },
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "connection_hidden" }.map { },
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "connection_core" }.map { },
                ).onEach {
                    debounceJob?.cancel()
                    debounceJob = scope.launch {
                        delay(CONNECTIONS_DEBOUNCE_MS)
                        bumpInboxVersionLocked()
                        _connectionJunctionChanged.emit(Unit)
                    }
                }.launchIn(this)
                channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "connections" }
                    .filter { it is PostgresAction.Update }
                    .onEach {
                        bumpInboxVersionLocked()
                    }
                    .launchIn(this)
                channel.subscribe()
            } catch (e: Exception) {
                println("RealtimeCoordinator: connections listener failed: ${e.redactedRestMessage()}")
            }
        }
    }

    private const val CONNECTIONS_DEBOUNCE_MS = 400L
}
