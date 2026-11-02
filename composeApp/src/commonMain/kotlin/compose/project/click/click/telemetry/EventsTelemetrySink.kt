package compose.project.click.click.telemetry

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Body of `POST /api/telemetry/events` (click-web `app/api/telemetry/events/route.ts`). No `props`:
 * iOS sends none for these events, and the server allowlists keys anyway.
 */
@Serializable
data class ProductEventBody(
    val event: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
) {
    /**
     * The JSON actually sent. Absent fields are omitted, never `null`: the route's zod schema
     * marks them `.optional()`, which rejects an explicit null with a 400.
     */
    fun toWireJson(): String = productEventWireJson.encodeToString(serializer(), this)
}

private val productEventWireJson =
    Json {
        explicitNulls = false
        encodeDefaults = true
    }

/** Small key-value store that survives process death (Android: plain prefs; nothing here is PII). */
interface EventsTelemetryStorage {
    fun get(key: String): String?

    fun set(
        key: String,
        value: String?,
    )
}

class InMemoryEventsTelemetryStorage : EventsTelemetryStorage {
    val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun set(
        key: String,
        value: String?,
    ) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/**
 * Pilot product events only a client can see (click-ios `Click/Core/Telemetry/ProductTelemetry.swift`):
 * the install, one app open per local day, and opening an event recap. No IDs, content or
 * coordinates; the server drops events for users outside the `pilot_analytics` cohort and answers 202.
 *
 * Events are queued (bounded, persisted) and sent by [flush]; the route needs a signed-in user,
 * so a 401, a 429, a 5xx or a network failure keeps them for the next flush. The friction batcher
 * can't carry these: it posts a different body to `/api/telemetry/friction`.
 */
class EventsTelemetrySink(
    private val storage: EventsTelemetryStorage,
    /** Posts one body; returns the HTTP status, or null on a network failure. */
    private val post: suspend (ProductEventBody) -> Int?,
    private val appVersion: () -> String? = { null },
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val queueSerializer = ListSerializer(ProductEventBody.serializer())

    /** First launch of this install (kept queued until a signed-in flush can send it). */
    suspend fun installedIfNeeded() =
        mutex.withLock {
            if (storage.get(KEY_INSTALL_SENT) == "1") return@withLock
            storage.set(KEY_INSTALL_SENT, "1")
            enqueueLocked(body(EVENT_INSTALL))
        }

    /** At most once per local calendar day, across process death. */
    suspend fun appOpened() =
        mutex.withLock {
            val day =
                now()
                    .toLocalDateTime(timeZone())
                    .date
                    .toString()
            if (storage.get(KEY_LAST_OPEN_DAY) == day) return@withLock
            storage.set(KEY_LAST_OPEN_DAY, day)
            enqueueLocked(body(EVENT_APP_OPEN))
        }

    /** One per recap visit (iOS `EventRecapModel.load`, once the recap is revealed). */
    suspend fun recapOpened() = mutex.withLock { enqueueLocked(body(EVENT_RECAP_OPENED)) }

    /** Sends everything queued, oldest first. Stops at the first retryable failure and keeps the rest. */
    suspend fun flush() =
        mutex.withLock {
            var pending = readQueueLocked()
            while (pending.isNotEmpty()) {
                val status = runCatching { post(pending.first()) }.getOrNull()
                if (status == null || isRetryable(status)) break
                // 2xx, or a 4xx the server will never accept: done with it either way.
                pending = pending.drop(1)
                writeQueueLocked(pending)
            }
        }

    internal fun pendingForTest(): List<ProductEventBody> = readQueueLocked()

    private fun body(event: String) =
        ProductEventBody(
            event = event,
            appVersion = appVersion()?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_APP_VERSION),
            occurredAt = now().toString(),
        )

    private fun enqueueLocked(body: ProductEventBody) {
        writeQueueLocked((readQueueLocked() + body).takeLast(MAX_QUEUE))
    }

    private fun readQueueLocked(): List<ProductEventBody> =
        storage
            .get(KEY_QUEUE)
            ?.let { raw ->
                runCatching { json.decodeFromString(queueSerializer, raw) }.getOrNull()
            }.orEmpty()

    private fun writeQueueLocked(queue: List<ProductEventBody>) {
        storage.set(KEY_QUEUE, if (queue.isEmpty()) null else json.encodeToString(queueSerializer, queue))
    }

    companion object {
        const val PATH = "/api/telemetry/events"
        const val EVENT_INSTALL = "install"
        const val EVENT_APP_OPEN = "app_open"
        const val EVENT_RECAP_OPENED = "recap_opened"
        internal const val KEY_INSTALL_SENT = "telemetry.product.install_sent"
        internal const val KEY_LAST_OPEN_DAY = "telemetry.product.last_open_day"
        internal const val KEY_QUEUE = "telemetry.product.queue"
        internal const val MAX_QUEUE = 20
        internal const val MAX_APP_VERSION = 32

        /** Not signed in yet, rate limited, or the server is down: try again on the next flush. */
        internal fun isRetryable(status: Int): Boolean = status == 401 || status == 408 || status == 429 || status >= 500
    }
}
