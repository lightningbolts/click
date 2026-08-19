package compose.project.click.click.data.repository

import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.chat.attachments.ChatAttachmentValidator
import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.auth.LocalSessionCache
import compose.project.click.click.data.CHAT_ATTACHMENTS_BUCKET
import compose.project.click.click.data.CHAT_MEDIA_BUCKET
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.notifications.ChatPushNotifier
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import compose.project.click.click.data.AppDataManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import compose.project.click.click.util.compressOutgoingChatImageForUpload
import compose.project.click.click.util.chatMediaDispatcher
import compose.project.click.click.util.isHardAuthFailure
import compose.project.click.click.util.isOfflineNetworkFailure
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.chatMediaVaultLocalPath
import compose.project.click.click.util.imageVaultFileExtension
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.vaultCacheExtension
import compose.project.click.click.util.writeChatMediaVaultFile

internal suspend fun SupabaseChatRepository.disposeGlobalPresenceSession(session: SupabaseChatRepository.GlobalPresenceSession) {
    session.jobs.forEach { it.cancel() }
    session.scope.cancel()
    runCatching { supabase.realtime.removeChannel(session.channel) }
}

internal fun SupabaseChatRepository.userIdFromPresence(p: Presence): String? {
    fun fromObject(obj: JsonObject): String? {
        val el = obj["userId"] ?: obj["user_id"] ?: return null
        return (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    fromObject(p.state)?.let { return it }
    val nested = p.state["state"]?.let { it as? JsonObject } ?: return null
    return fromObject(nested)
}

internal suspend fun SupabaseChatRepository.disposeEphemeralSession(session: SupabaseChatRepository.ChatEphemeralSession) {
    session.jobs.forEach { it.cancel() }
    session.scope.cancel()
    runCatching { supabase.realtime.removeChannel(session.channel) }
}

internal fun SupabaseChatRepository.watchPresenceRealtimeReconnect(userId: String) {
    if (presenceReconnectJob?.isActive == true) return
    presenceReconnectJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        var everConnected = false
        var lostConnection = false
        supabase.realtime.status.collect { status ->
            when (status) {
                Realtime.Status.DISCONNECTED,
                Realtime.Status.CONNECTING,
                -> {
                    if (everConnected) lostConnection = true
                }

                Realtime.Status.CONNECTED -> {
                    if (lostConnection) {
                        lostConnection = false
                        delay(750)
                        val session = globalPresenceMutex.withLock { globalPresenceSession }
                        if (session == null || session.trackedUserId != userId) {
                            everConnected = true
                            return@collect
                        }
                        println("ChatRepository: Realtime reconnected — refreshing global presence")
                        globalPresenceMutex.withLock {
                            globalPresenceSession?.let { disposeGlobalPresenceSession(it) }
                            globalPresenceSession = null
                        }
                        startGlobalPresence(userId)
                    }
                    everConnected = true
                }
            }
        }
    }
}

internal suspend fun SupabaseChatRepository.awaitEphemeralSession(chatId: String): SupabaseChatRepository.ChatEphemeralSession? {
    repeat(EPHEMERAL_SESSION_WAIT_STEPS) {
        ephemeralMutex.withLock { ephemeralSessions[chatId] }?.let { return it }
        delay(EPHEMERAL_SESSION_POLL_MS)
    }
    return null
}

internal suspend fun SupabaseChatRepository.findConnectionIdBetween(userA: String, userB: String): String? {
    if (userA.isBlank() || userB.isBlank()) return null
    return try {
        supabase.from("connections")
            .select(columns = Columns.list("id", "user_ids")) {
                filter {
                    contains("user_ids", listOf(userA, userB))
                    isIn("status", listOf("active", "kept"))
                }
                limit(8)
            }
            .decodeList<SupabaseChatRepository.ConnectionUserIdsRow>()
            .firstOrNull {
                it.user_ids.size == 2 &&
                    it.user_ids.contains(userA) &&
                    it.user_ids.contains(userB)
            }
            ?.id
    } catch (e: Exception) {
        println("ChatRepository: findConnectionIdBetween failed: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun SupabaseChatRepository.startGlobalPresenceImpl(userId: String) {
    if (userId.isBlank()) return
    globalPresenceMutex.withLock {
        globalPresenceSession?.let { existing ->
            if (existing.trackedUserId == userId) return@withLock
            disposeGlobalPresenceSession(existing)
            globalPresenceSession = null
        }
        _presenceHealth.value = PresenceHealth.Connecting

        val channel = supabase.channel(GLOBAL_PRESENCE_CHANNEL) {
            presence {
                key = userId
            }
        }

        val presenceKeysOnline = mutableSetOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val presenceFlow = channel.presenceChangeFlow()

        /** Register before subscribe so the initial presence sync is not dropped (matches web `sync` handler). */
        val presenceJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                presenceFlow.collect { action ->
                    action.leaves.keys.forEach { key -> presenceKeysOnline.remove(key) }
                    action.joins.keys.forEach { key -> presenceKeysOnline.add(key) }
                    action.joins.values.forEach { p ->
                        userIdFromPresence(p)?.let { presenceKeysOnline.add(it) }
                    }
                    action.leaves.values.forEach { p ->
                        userIdFromPresence(p)?.let { presenceKeysOnline.remove(it) }
                    }
                    _onlineUsers.value = presenceKeysOnline.toSet()
                }
            } catch (e: CancellationException) {
                // Session torn down — propagate so the outer scope finishes cleanly.
                throw e
            } catch (e: Exception) {
                // Don't silently swallow: broken presence flow needs visibility in logs
                // (redacted) so on-call can correlate with transport errors. Flip the
                // health flag so the UI can dim online dots / show a subtle affordance.
                println("ChatRepository: presence flow collection failed: ${e.redactedRestMessage()}")
                _presenceHealth.value = PresenceHealth.Degraded
            }
        }

        try {
            if (!channel.subscribeWithTimeout()) {
                _presenceHealth.value = PresenceHealth.Degraded
                presenceJob.cancel()
                scope.cancel()
                runCatching { supabase.realtime.removeChannel(channel) }
                return@withLock
            }
            channel.track(buildJsonObject { put("userId", userId) })
            _presenceHealth.value = PresenceHealth.Online
        } catch (e: Exception) {
            println("ChatRepository: startGlobalPresence failed: ${e.redactedRestMessage()}")
            _presenceHealth.value = PresenceHealth.Degraded
            presenceJob.cancel()
            scope.cancel()
            runCatching { supabase.realtime.removeChannel(channel) }
            return@withLock
        }

        val presenceRefreshJob = scope.launch {
            while (isActive) {
                delay(PRESENCE_TRACK_REFRESH_MS)
                if (
                    supabase.realtime.status.value != Realtime.Status.CONNECTED ||
                    channel.status.value != RealtimeChannel.Status.SUBSCRIBED
                ) {
                    continue
                }
                runCatching {
                    channel.track(buildJsonObject { put("userId", userId) })
                }
            }
        }

        globalPresenceSession = SupabaseChatRepository.GlobalPresenceSession(
            channel = channel,
            trackedUserId = userId,
            scope = scope,
            jobs = listOf(presenceJob, presenceRefreshJob),
        )
    }
    watchPresenceRealtimeReconnect(userId)
}

internal suspend fun SupabaseChatRepository.joinChatEphemeralChannelImpl(chatId: String, currentUserId: String, peerUserId: String) {
    val generation = ephemeralMutex.withLock {
        ephemeralSessions[chatId]?.let { existing ->
            if (existing.peerUserId == peerUserId) return
            disposeEphemeralSession(existing)
            ephemeralSessions.remove(chatId)
        }
        val next = (ephemeralJoinGeneration[chatId] ?: 0) + 1
        ephemeralJoinGeneration[chatId] = next
        next
    }

    val channel = supabase.channel("chat:$chatId") {
        broadcast {
            receiveOwnBroadcasts = false
        }
        presence {
            key = currentUserId
        }
    }

    val typingFlow = MutableSharedFlow<TypingStatus>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val peerOnline = MutableStateFlow(false)
    /** Presence keys are configured as each client's user id; diff joins/leaves are authoritative. */
    val presenceKeysOnline = mutableSetOf<String>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val broadcastFlow = channel.broadcastFlow<SupabaseChatRepository.TypingBroadcastPayload>(event = "typing")
    val presenceFlow = channel.presenceChangeFlow()

    val presenceJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            presenceFlow.collect { action ->
                action.leaves.keys.forEach { key -> presenceKeysOnline.remove(key) }
                action.joins.keys.forEach { key -> presenceKeysOnline.add(key) }
                action.joins.values.forEach { p ->
                    userIdFromPresence(p)?.let { presenceKeysOnline.add(it) }
                }
                action.leaves.values.forEach { p ->
                    userIdFromPresence(p)?.let { presenceKeysOnline.remove(it) }
                }
                peerOnline.value = peerUserId in presenceKeysOnline
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ChatRepository: ephemeral presence flow failed: ${e.redactedRestMessage()}")
        }
    }

    suspend fun abandonJoin() {
        presenceJob.cancel()
        scope.cancel()
        runCatching { supabase.realtime.removeChannel(channel) }
    }

    try {
        // Subscribe outside the mutex so leaveChatEphemeralChannel is not blocked for 8s.
        if (!channel.subscribeWithTimeout()) {
            println("ChatRepository: join chat ephemeral subscribe timed out")
            abandonJoin()
            return
        }
        channel.track(buildJsonObject { put("userId", currentUserId) })
    } catch (e: CancellationException) {
        abandonJoin()
        throw e
    } catch (e: Exception) {
        println("ChatRepository: join chat ephemeral failed: ${e.redactedRestMessage()}")
        abandonJoin()
        return
    }

    val broadcastJob = scope.launch {
        try {
            broadcastFlow.collect { payload ->
                if (payload.userId != currentUserId) {
                    typingFlow.emit(TypingStatus(userId = payload.userId, isTyping = true))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("ChatRepository: typing broadcast flow failed: ${e.redactedRestMessage()}")
        }
    }

    val presenceRefreshJob = scope.launch {
        while (isActive) {
            delay(PRESENCE_TRACK_REFRESH_MS)
            if (
                supabase.realtime.status.value != Realtime.Status.CONNECTED ||
                channel.status.value != RealtimeChannel.Status.SUBSCRIBED
            ) {
                continue
            }
            runCatching {
                channel.track(buildJsonObject { put("userId", currentUserId) })
            }
        }
    }

    val session = SupabaseChatRepository.ChatEphemeralSession(
        channel = channel,
        peerUserId = peerUserId,
        typingFlow = typingFlow,
        peerOnline = peerOnline,
        scope = scope,
        jobs = listOf(broadcastJob, presenceJob, presenceRefreshJob),
    )

    ephemeralMutex.withLock {
        if (ephemeralJoinGeneration[chatId] != generation) {
            disposeEphemeralSession(session)
            return
        }
        ephemeralSessions[chatId]?.let { disposeEphemeralSession(it) }
        ephemeralSessions[chatId] = session
    }
}
