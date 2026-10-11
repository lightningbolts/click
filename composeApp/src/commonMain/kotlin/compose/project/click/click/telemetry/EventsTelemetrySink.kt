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

/** Body of `POST /api/telemetry/events` (click-web `app/api/telemetry/events/route.ts`). */
@Serializable
data class ProductEventBody(
    val event: String,
    val props: Map<String, String>? = null,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
)

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
 * Pilot product events only a client can see (iOS `ProductTelemetry`): the install, one app open
 * per local day, and opening an event recap. No IDs, content or coordinates; the server drops
 * events for users outside the `pilot_analytics` cohort and always answers 202.
 *
 * Events are queued (bounded, persisted) and sent by [flush]; the route needs a signed-in user,
 * so a 401 or a network failure keeps them for the next flush. The friction batcher can't carry
 * these: it posts a different body to `/api/telemetry/friction`.
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
            enqueueLocked(body(EVENT_INSTALL, null))
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
            enqueueLocked(body(EVENT_APP_OPEN, null))
        }

    /** One per recap visit. [access] is a short slug (e.g. `attendee`); anything else is dropped. */
    suspend fun recapOpened(access: String? = null) =
        mutex.withLock {
            val props = access?.takeIf { PROP_VALUE.matches(it) }?.let { mapOf("access" to it) }
            enqueueLocked(body(EVENT_RECAP_OPENED, props))
        }

    /** Sends everything queued. Stops at the first 401/network failure and keeps the rest. */
    suspend fun flush() =
        mutex.withLock {
            var pending = readQueueLocked()
            while (pending.isNotEmpty()) {
                val status = runCatching { post(pending.first()) }.getOrNull()
                if (status == null || status == 401 || status == 429 || status >= 500) break
                // 2xx, or a 4xx the server will never accept: done with it either way.
                pending = pending.drop(1)
                writeQueueLocked(pending)
            }
        }

    internal fun pendingForTest(): List<ProductEventBody> = readQueueLocked()

    private fun body(
        event: String,
        props: Map<String, String>?,
    ) = ProductEventBody(
        event = event,
        props = props,
        appVersion = appVersion()?.trim()?.takeIf { it.isNotEmpty() }?.take(32),
        occurredAt = now().toString(),
    )

    private fun enqueueLocked(body: ProductEventBody) {
        writeQueueLocked((readQueueLocked() + body).takeLast(MAX_QUEUE))
    }

    private fun readQueueLocked(): List<ProductEventBody> =
        storage.get(KEY_QUEUE)?.let { raw ->
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
        private val PROP_VALUE = Regex("^[a-z0-9_]{1,32}$")
    }
}
