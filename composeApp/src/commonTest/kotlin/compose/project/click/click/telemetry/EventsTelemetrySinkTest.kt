package compose.project.click.click.telemetry

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
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
    ) {
        val posted = mutableListOf<ProductEventBody>()

        fun sink() =
            EventsTelemetrySink(
                storage = storage,
                post = { body ->
                    if (status != null) posted += body
                    status
                },
                appVersion = { "grouchy" },
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
            h.status = 202
            sink.flush()
            assertTrue(sink.pendingForTest().isEmpty())
            assertEquals(listOf("install"), h.posted.map { it.event })
        }

    @Test
    fun bodyMatchesTheRouteAndCarriesNoPii() =
        runTest {
            val h = Harness(now = morning)
            val sink = h.sink()
            sink.recapOpened(access = "attendee")
            sink.recapOpened(access = "Not A Slug!")
            sink.flush()
            val (first, second) = h.posted
            assertEquals("recap_opened", first.event)
            assertEquals("android", first.platform)
            assertEquals("grouchy", first.appVersion)
            assertEquals("2026-09-21T14:13:20Z", first.occurredAt)
            assertEquals(mapOf("access" to "attendee"), first.props)
            assertNull(second.props)
        }

    @Test
    fun queueIsBounded() =
        runTest {
            val h = Harness(now = morning, status = 401)
            val sink = h.sink()
            repeat(EventsTelemetrySink.MAX_QUEUE + 5) { sink.recapOpened() }
            assertEquals(EventsTelemetrySink.MAX_QUEUE, sink.pendingForTest().size)
        }
}
