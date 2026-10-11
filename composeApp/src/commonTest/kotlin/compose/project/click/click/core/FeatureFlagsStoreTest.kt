package compose.project.click.click.core

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagsStoreTest {
    // Shape of click-web `GET /api/me/features` (resolveAllFeatures).
    private val body =
        """
        {"features":{
          "shared_drops":{"enabled":true,"config":{"daily_cap":3,"develop_hours":1,"teaser":"pixelated"}},
          "event_history":{"enabled":true,"config":{"recap_card_hours":48}},
          "click_places":{"enabled":false,"config":{}},
          "pilot_analytics":{"enabled":true,"config":{}}
        }}
        """.trimIndent()

    private fun store(
        persistence: FeatureFlagsPersistence = InMemoryFeatureFlagsPersistence(),
        response: () -> String? = { body },
    ) = FeatureFlagsStore(persistence = { persistence }, fetch = { response() })

    @AfterTest
    fun resetGate() {
        FeatureFlagGate.isEnabled = { false }
    }

    @Test
    fun everythingIsOffByDefault() {
        val flags = store()
        FeatureKeys.all.forEach { assertFalse(flags.isEnabled(it)) }
        assertFalse(flags.isEnabled("not_a_flag"))
        assertFalse(FeatureFlagGate.isEnabled(FeatureKeys.SHARED_DROPS))
    }

    @Test
    fun refreshEnablesOnlyServerEnabledFlags() =
        runTest {
            val flags = store()
            flags.restore("u1")
            assertTrue(flags.refresh())
            assertTrue(flags.isEnabled(FeatureKeys.SHARED_DROPS))
            assertTrue(flags.isEnabled(FeatureKeys.EVENT_HISTORY))
            assertFalse(flags.isEnabled(FeatureKeys.CLICK_PLACES))
            assertFalse(flags.isEnabled(FeatureKeys.DROPS_DEVELOP))
        }

    @Test
    fun refreshWithoutUserDoesNothing() =
        runTest {
            val flags = store()
            assertFalse(flags.refresh())
            assertFalse(flags.isEnabled(FeatureKeys.SHARED_DROPS))
        }

    @Test
    fun persistedFlagsRoundTripPerUser() =
        runTest {
            val disk = InMemoryFeatureFlagsPersistence()
            val first = store(disk)
            first.restore("u1")
            first.refresh()

            // A cold launch paints from disk before any network.
            val relaunched = store(disk) { null }
            relaunched.restoreAtLaunch()
            assertEquals("u1", relaunched.state.value.userId)
            assertTrue(relaunched.isEnabled(FeatureKeys.SHARED_DROPS))

            // Another account on the same device starts from its own (empty) flags.
            relaunched.restore("u2")
            assertFalse(relaunched.isEnabled(FeatureKeys.SHARED_DROPS))
        }

    @Test
    fun malformedOrFailedResponseKeepsLastKnownFlags() =
        runTest {
            var response: String? = body
            val flags = store { response }
            flags.restore("u1")
            flags.refresh()
            response = "{not json"
            assertFalse(flags.refresh())
            assertTrue(flags.isEnabled(FeatureKeys.SHARED_DROPS))
            response = null
            assertFalse(flags.refresh())
            assertTrue(flags.isEnabled(FeatureKeys.SHARED_DROPS))
        }

    @Test
    fun malformedJsonGivesAllOff() {
        assertNull(parseFeatureFlags("{not json"))
        assertNull(parseFeatureFlags("[]"))
        assertNull(parseFeatureFlags(""))
        val disk = InMemoryFeatureFlagsPersistence().apply { write("u1", "garbage") }
        val flags = store(disk)
        flags.restore("u1")
        FeatureKeys.all.forEach { assertFalse(flags.isEnabled(it)) }
    }

    @Test
    fun signOutClearsFlagsAndStorage() =
        runTest {
            val disk = InMemoryFeatureFlagsPersistence()
            val flags = store(disk)
            flags.restore("u1")
            flags.refresh()
            flags.clear()
            assertFalse(flags.isEnabled(FeatureKeys.SHARED_DROPS))
            assertNull(flags.state.value.userId)
            assertNull(disk.read("u1"))
            assertNull(disk.lastUserId())
        }

    @Test
    fun gateReadsTheStore() =
        runTest {
            val flags = store()
            FeatureFlagGate.isEnabled = flags::isEnabled
            flags.restore("u1")
            flags.refresh()
            assertTrue(FeatureFlagGate.isEnabled(FeatureKeys.SHARED_DROPS))
            assertFalse(FeatureFlagGate.isEnabled("unknown_key"))
        }

    @Test
    fun configAccessorsUseServerValuesAndSeedDefaults() =
        runTest {
            val flags = store()
            flags.restore("u1")
            flags.refresh()
            val state = flags.state.value
            assertEquals(
                SharedDropsConfig(developHours = 1, dailyCap = 3, stripWindowHours = 24, stripMin = 25, stripMax = 150),
                state.sharedDrops,
            )
            assertEquals(48, state.eventHistory.recapCardHours)
            // Missing configs fall back to the server seeds.
            assertEquals(EventDropsConfig(perUserCap = 10, revealHourLocal = 10, absenteeLimit = 6), state.eventDrops)
            assertEquals(12, state.soundtrackPresence.heartbeatTtlMinutes)
            assertEquals(AlertConfirmationsConfig(ttlMinutes = 120, radiusMeters = 300), state.alertConfirmations)
            assertEquals(100, state.clickPlaces.gpsMaxAccuracyMeters)
            assertEquals(45, state.clickPlaces.pulseCooldownMinutes)
        }

    @Test
    fun outOfRangeConfigFallsBackToDefault() {
        val features =
            parseFeatureFlags(
                """{"features":{"shared_drops":{"enabled":true,"config":{"daily_cap":-4,"strip_max":"120","develop_hours":"x"}}}}""",
            )!!
        val config = FeatureFlagsState("u1", features).sharedDrops
        assertEquals(3, config.dailyCap)
        assertEquals(120, config.stripMax)
        assertEquals(1, config.developHours)
    }
}
