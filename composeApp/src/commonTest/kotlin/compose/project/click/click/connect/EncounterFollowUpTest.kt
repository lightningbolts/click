package compose.project.click.click.connect

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G4-7: stillness, refinement, the moment-equality invariant and the confirm/cancel contract. */
class EncounterFollowUpTest {
    private val t0 = 1_790_000_000_000L

    private fun north(
        accuracy: Double,
        offsetS: Double,
        meters: Double,
    ) = LocationFix(
        latitude = 47.6101 + meters / 111_320,
        longitude = -122.3421,
        horizontalAccuracyMeters = accuracy,
        observedAtMs = t0 + (offsetS * 1000).toLong(),
    )

    private class FakeFeed : StillLocationFeed {
        var onFix: ((LocationFix) -> Unit)? = null
        var stopped = false

        override fun start(
            onFix: (LocationFix) -> Unit,
            onLinearAcceleration: (x: Double, y: Double, z: Double) -> Unit,
        ): Boolean {
            this.onFix = onFix
            return true
        }

        override fun stop() {
            stopped = true
        }
    }

    @Test
    fun stillnessNeedsThreeSustainedSeconds() {
        val detector = StillnessDetector(samplesPerSecond = 10)
        val walking = 0.2 * 9.80665
        // One lift (one active second) then calm: never "moved".
        repeat(10) { detector.record(walking, 0.0, 0.0) }
        repeat(10) { detector.record(0.01, 0.0, 0.0) }
        assertFalse(detector.hasMoved)
        repeat(30) { detector.record(walking, 0.0, 0.0) }
        assertTrue(detector.hasMoved)
    }

    @Test
    fun stillnessThresholdIsPointOneTwoG() {
        val detector = StillnessDetector(samplesPerSecond = 10)
        repeat(50) { detector.record(0.11 * 9.80665, 0.0, 0.0) }
        assertFalse(detector.hasMoved)
    }

    @Test
    fun locationBodyMomentEqualsTheObservationMoment() {
        val snapshot =
            ConnectionCaptureSnapshot(
                momentMs = t0 + 123,
                startedAtMs = t0 - 2000,
                finishedAtMs = t0 + 500,
                location = north(14.0, 0.0, 0.0),
                locationFixes = listOf(north(14.0, 0.0, 0.0)),
            )
        val observation = ConnectionSensorObservation.build("tap", snapshot, true, null, t0 + 600, 0)
        val body = EncounterFollowUp.locationBody(north(5.0, 9.0, 2.0), snapshot.momentMs, listOf("c1"))
        assertEquals(
            observation["connection_moment"]!!.jsonPrimitive.content,
            body["connection_moment"]!!.jsonPrimitive.content,
        )
        assertEquals("2026-09-21T14:13:20.123Z", body["connection_moment"]!!.jsonPrimitive.content)
        assertEquals(listOf("c1"), body["connection_ids"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(5.0, body["gps_horizontal_accuracy_m"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun excellentOrNoLocationNeverStartsAFollowUp() =
        runTest {
            val excellent =
                ConnectionCaptureSnapshot(momentMs = t0, startedAtMs = t0, finishedAtMs = t0, location = north(4.0, 0.0, 0.0))
            assertNull(EncounterFollowUp.begin(excellent, includeLocation = true, scope = this, feed = FakeFeed(), send = { _, _ -> }))
            val coarse = excellent.copy(location = north(20.0, 0.0, 0.0))
            assertNull(EncounterFollowUp.begin(coarse, includeLocation = false, scope = this, feed = FakeFeed(), send = { _, _ -> }))
        }

    @Test
    fun refinedFixIsSentOnceConfirmed() =
        runTest {
            var clock = t0
            val feed = FakeFeed()
            val sent = mutableListOf<Pair<String, String>>()
            val followUp =
                EncounterFollowUp(
                    originalFix = north(14.0, -0.5, 0.0),
                    momentMs = t0,
                    feed = feed,
                    send = { path, body -> sent += path to body },
                    now = { clock },
                    sleep = { clock += it },
                )
            assertTrue(followUp.start(backgroundScope))
            feed.onFix!!(north(9.0, 4.0, 3.0))
            feed.onFix!!(north(5.0, 9.0, 2.0))
            followUp.confirm(listOf("c1", "c1", "c2"))
            followUp.run()
            assertEquals(5.0, followUp.refinedAccuracyMeters.value)
            val (path, raw) = sent.single()
            assertEquals("/api/connections/encounter-location", path)
            val body = Json.parseToJsonElement(raw).jsonObject
            assertEquals(listOf("c1", "c2"), body["connection_ids"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(isoMillis(t0), body["connection_moment"]!!.jsonPrimitive.content)
            assertTrue(feed.stopped)
        }

    @Test
    fun disagreeingFixIsDroppedAndNothingSent() =
        runTest {
            var clock = t0
            val feed = FakeFeed()
            val sent = mutableListOf<String>()
            val followUp =
                EncounterFollowUp(north(14.0, -0.5, 0.0), t0, feed, { _, b -> sent += b }, { clock }, { clock += it })
            followUp.start(backgroundScope)
            feed.onFix!!(north(5.0, 8.0, 60.0))
            followUp.confirm(listOf("c1"))
            followUp.run()
            assertNull(followUp.refinedAccuracyMeters.value)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun cancelledFollowUpSendsNothingButConfirmedOneIgnoresCancel() =
        runTest {
            var clock = t0
            val feed = FakeFeed()
            val sent = mutableListOf<String>()
            val cancelled = EncounterFollowUp(north(14.0, -0.5, 0.0), t0, feed, { _, b -> sent += b }, { clock }, { clock += it })
            cancelled.start(backgroundScope)
            feed.onFix!!(north(5.0, 9.0, 2.0))
            cancelled.cancel()
            cancelled.run()
            assertTrue(sent.isEmpty())

            clock = t0
            val feed2 = FakeFeed()
            val confirmed = EncounterFollowUp(north(14.0, -0.5, 0.0), t0, feed2, { _, b -> sent += b }, { clock }, { clock += it })
            confirmed.start(backgroundScope)
            feed2.onFix!!(north(5.0, 9.0, 2.0))
            confirmed.confirm(listOf("c1"))
            confirmed.cancel()
            confirmed.run()
            assertNotNull(sent.singleOrNull())
        }

    @Test
    fun unconfirmedWithinTheWindowSendsNothing() =
        runTest {
            var clock = t0
            val feed = FakeFeed()
            val sent = mutableListOf<String>()
            val followUp = EncounterFollowUp(north(14.0, -0.5, 0.0), t0, feed, { _, b -> sent += b }, { clock }, { clock += it })
            followUp.start(backgroundScope)
            feed.onFix!!(north(5.0, 9.0, 2.0))
            followUp.run()
            assertTrue(sent.isEmpty())
            assertTrue(clock >= t0 + EncounterFollowUp.CONFIRMATION_WINDOW_MS)
        }
}
