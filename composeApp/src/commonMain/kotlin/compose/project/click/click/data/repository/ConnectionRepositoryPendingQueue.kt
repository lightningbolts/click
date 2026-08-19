@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.project.click.click.data.repository

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.PendingConnectionDraft
import compose.project.click.click.data.models.newPendingConnectionId
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlin.math.*

internal suspend fun ConnectionRepository.syncPendingConnectionsImpl(): Int {
    val queue = loadPendingConnectionQueue()
    if (queue.isEmpty()) {
        AppDataManager.setPendingConnectionsCount(0)
        return 0
    }

    val remaining = mutableListOf<PendingConnectionDraft>()
    var syncedCount = 0
    var needsRefresh = false

    queue.forEach { draft ->
        val result =
            runCatching {
                withTimeout(ConnectionRepository.CONNECTION_TIMEOUT_MS) {
                    createConnectionOnline(draft.request)
                }
            }.getOrElse { Result.failure(it) }

        if (result.isSuccess) {
            val connection = result.getOrNull()?.connection ?: return@forEach
            val otherUser = getUserById(draft.request.userId2).getOrNull()
            AppDataManager.replaceLocalConnection(draft.localId, connection, otherUser)
            syncedCount++
            needsRefresh = true
        } else {
            val error = result.exceptionOrNull()
            if (shouldDropQueuedDraft(error)) {
                AppDataManager.removeConnection(draft.localId)
                needsRefresh = true
            } else {
                remaining += draft
            }
        }
    }

    savePendingConnectionQueue(remaining)
    AppDataManager.setPendingConnectionsCount(remaining.size)

    if (needsRefresh) {
        AppDataManager.refresh(force = true)
    }

    return syncedCount
}

internal suspend fun ConnectionRepository.queuePendingConnection(request: ConnectionRequest): Connection {
    val queue = loadPendingConnectionQueue().toMutableList()
    val existing =
        queue.firstOrNull {
            it.request.userId1 == request.userId1 &&
                it.request.userId2 == request.userId2 &&
                it.request.connectionMethod == request.connectionMethod
        }
    if (existing != null) {
        AppDataManager.setPendingConnectionsCount(queue.size)
        return existing.toPlaceholderConnection(AppDataManager.locationPreferences.value.includeInInsightsEnabled)
    }

    val normalizedRequest =
        request.copy(
            qrToken = null,
            tokenAgeMs = null,
            skipQrTokenRedeem = false,
            preflightConnectionId = null,
            preflightEncounterLogged = null,
        )
    val draft =
        PendingConnectionDraft(
            localId = newPendingConnectionId(),
            request = normalizedRequest,
            queuedAt = Clock.System.now().toEpochMilliseconds(),
        )
    queue += draft
    savePendingConnectionQueue(queue)
    AppDataManager.setPendingConnectionsCount(queue.size)
    return draft.toPlaceholderConnection(AppDataManager.locationPreferences.value.includeInInsightsEnabled)
}

internal suspend fun ConnectionRepository.loadPendingConnectionQueue(): List<PendingConnectionDraft> {
    val queueJson = tokenStorage.getPendingConnectionQueue()
    if (queueJson.isNullOrBlank()) return emptyList()

    return runCatching {
        json.decodeFromString<List<PendingConnectionDraft>>(queueJson)
    }.getOrElse {
        println("ConnectionRepository: Failed to decode pending queue: ${it.message}")
        emptyList()
    }
}

internal suspend fun ConnectionRepository.savePendingConnectionQueue(queue: List<PendingConnectionDraft>) {
    val serialized = if (queue.isEmpty()) null else json.encodeToString(queue)
    tokenStorage.savePendingConnectionQueue(serialized)
}

internal fun ConnectionRepository.shouldQueueOffline(
    request: ConnectionRequest,
    error: Throwable?,
): Boolean {
    if (error == null) return false

    val message = error.message?.lowercase().orEmpty()
    val isRetryableNetworkFailure =
        message.contains("timeout") ||
            message.contains("timed out") ||
            message.contains("network") ||
            message.contains("socket") ||
            message.contains("unable to resolve host") ||
            message.contains("failed to connect") ||
            message.contains("offline") ||
            message.contains("unreachable")

    if (!isRetryableNetworkFailure) return false

    return request.connectionMethod == "nfc" ||
        request.connectionMethod == "proximity" ||
        request.connectionMethod == "qr"
}

internal fun ConnectionRepository.shouldDropQueuedDraft(error: Throwable?): Boolean {
    val message = error?.message?.lowercase().orEmpty()
    return message.contains("already exists") ||
        message.contains("cannot connect with yourself") ||
        message.contains("invalid qr code") ||
        message.contains("already used") ||
        message.contains("expired") ||
        message.contains("same physical location")
}
