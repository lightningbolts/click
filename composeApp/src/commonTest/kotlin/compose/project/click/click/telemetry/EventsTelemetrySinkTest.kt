package compose.project.click.click.telemetry

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class EventsTelemetrySinkTest {
    // iOS ProductTelemetryTests fixture.
    private val morning = Instant.fromEpochSeconds(1_790_000_000)

    private class Harness(
        val storage: InMemoryEventsTelemetryStorage = InMemoryEventsTelemetryStorage(),
        var status: Int? = 202,
        var now: Instant,
        var version: String? = "1.4.0",
    ) {
        /** Bodies the server accepted (2xx); rejected or offline attempts are not deliveries. */
        val posted = mutableListOf<ProductEventBody>()
        var attempts = 0

        fun sink() =
            EventsTelemetrySink(
                storage = storage,
                post = { body ->
                    attempts++
                    if (status in 200..299) posted += body
                    status
                },
                appVersion = { version },
                now = { now },
                timeZone = { TimeZone.UTC },
            )
    }

    @Test
    fun installOncePerInstallAndAppOpenOncePerDay() =
        runTest {
            val h = Harness(now = morning)
            val sink = h.sink()
            sink.installedIfNeeded()
            sink.installedIfNeeded()
            sink.appOpened()
            h.now = morning + 3_600.seconds
            sink.appOpened()
            h.now = morning + 86_400.seconds
            sink.appOpened()
            sink.flush()
            assertEquals(listOf("install", "app_open", "app_open"), h.posted.map { it.event })
        }

    @Test
    fun appOpenIsOncePerDayAcrossProcessDeath() =
        runTest {
            val h = Harness(now = morning)
            h.sink().appOpened()
            // A new process reads the same storage.
            h.sink().appOpened()
            h.sink().installedIfNeeded()
            h.sink().installedIfNeeded()
            h.sink().flush()
            assertEquals(listOf("app_open", "install"), h.posted.map { it.event })
        }

    @Test
    fun localDayFollowsTheTimeZone() =
        runTest {
            val storage = InMemoryEventsTelemetryStorage()
            var zone: TimeZone = TimeZone.UTC
            val posted = mutableListOf<String>()
            val sink =
                EventsTelemetrySink(
                    storage = storage,
                    post = { body ->
                        posted += body.event
                        202
                    },
                    now = { morning },
                    timeZone = { zone },
                )
            sink.appOpened()
            // Same instant, but already the next calendar day at UTC+14.
            zone = TimeZone.of("Pacific/Kiritimati")
            sink.appOpened()
            sink.flush()
            assertEquals(listOf("app_open", "app_open"), posted)
        }

    @Test
    fun unauthorizedOrOfflineKeepsEventsQueued() =
        runTest {
            val h = Harness(now = morning, status = 401)
            val sink = h.sink()
            sink.installedIfNeeded()
            sink.flush()
            assertEquals(1, sink.pendingForTest().size)
            h.status = null
            sink.flush()
            assertEquals(1, sink.pendingForTest().size)
            h.status = 503
            sink.flush()
            assertEquals(1, sink.pendingForTest().size)
            h.status = 202
            sink.flush()
            assertTrue(sink.pendingForTest().isEmpty())
            assertEquals(listOf("install"), h.posted.map { it.event })
        }

    @Test
    fun aRejectedBodyIsDroppedSoItCannotBlockTheQueue() =
        runTest {
            val h = Harness(now = morning, status = 400)
            val sink = h.sink()
            sink.installedIfNeeded()
            sink.appOpened()
            sink.flush()
            assertEquals(2, h.attempts)
            assertTrue(sink.pendingForTest().isEmpty())
        }

    @Test
    fun retryableStatuses() {
        listOf(401, 408, 429, 500, 503).forEach { assertTrue(EventsTelemetrySink.isRetryable(it), "$it") }
        listOf(200, 202, 400, 403, 404, 413).forEach { assertFalse(EventsTelemetrySink.isRetryable(it), "$it") }
    }

    @Test
    fun bodyMatchesTheRoute() =
        runTest {
            val h = Harness(now = morning)
            val sink = h.sink()
            sink.recapOpened()
            sink.flush()
            val body = h.posted.single()
            assertEquals("recap_opened", body.event)
            assertEquals("android", body.platform)
            assertEquals("1.4.0", body.appVersion)
            assertEquals("2026-09-21T14:13:20Z", body.occurredAt)
        }

    @Test
    fun wireJsonOmitsAbsentFieldsAndCarriesNoPii() =
        runTest {
            val h = Harness(now = morning, version = null)
            val sink = h.sink()
            sink.installedIfNeeded()
            sink.flush()
            val wire = Json.parseToJsonElement(h.posted.single().toWireJson()).jsonObject
            // zod `.optional()` rejects an explicit null, so absent fields must not appear at all.
            assertEquals(setOf("event", "platform", "occurred_at"), wire.keys)
            assertEquals(JsonPrimitive("install"), wire["event"])
            assertEquals(JsonPrimitive("android"), wire["platform"])
        }

    @Test
    fun wireJsonCarriesAppVersionWhenKnown() {
        val wire =
            Json
                .parseToJsonElement(
                    ProductEventBody(event = "app_open", appVersion = "1.4.0", occurredAt = "2026-09-21T14:13:20Z").toWireJson(),
                ).jsonObject
        assertEquals(
            JsonObject(
                mapOf(
                    "event" to JsonPrimitive("app_open"),
                    "platform" to JsonPrimitive("android"),
                    "app_version" to JsonPrimitive("1.4.0"),
                    "occurred_at" to JsonPrimitive("2026-09-21T14:13:20Z"),
                ),
            ),
            wire,
        )
    }

    @Test
    fun appVersionIsTrimmedAndCappedAt32() =
        runTest {
            val h = Harness(now = morning, version = "  " + "9".repeat(40) + "  ")
            val sink = h.sink()
            sink.installedIfNeeded()
            sink.flush()
            assertEquals(
                32,
                h.posted
                    .single()
                    .appVersion
                    ?.length,
            )
            h.version = "   "
            sink.recapOpened()
            sink.flush()
            assertNull(h.posted.last().appVersion)
        }

    @Test
    fun appVersionNameDropsTheBuildNumber() {
        assertEquals("1.2", appVersionNameOf("1.2 (8)"))
        assertEquals("1.2", appVersionNameOf("1.2"))
        assertNull(appVersionNameOf(""))
        assertNull(appVersionNameOf(" (8)"))
    }

    @Test
    fun queueIsBounded() =
        runTest {
            val h = Harness(now = morning, status = 401)
            val sink = h.sink()
            repeat(EventsTelemetrySink.MAX_QUEUE + 5) { sink.recapOpened() }
            assertEquals(EventsTelemetrySink.MAX_QUEUE, sink.pendingForTest().size)
        }

    @Test
    fun corruptQueueIsTreatedAsEmpty() =
        runTest {
            val h = Harness(now = morning)
            h.storage.set(EventsTelemetrySink.KEY_QUEUE, "{not json")
            val sink = h.sink()
            sink.installedIfNeeded()
            sink.flush()
            assertEquals(listOf("install"), h.posted.map { it.event })
        }
}
