package compose.project.click.click.data.flags

import compose.project.click.click.data.storage.FakeTokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FlagCacheStorage(
    inner: TokenStorage = FakeTokenStorage(),
) : TokenStorage by inner {
    var cache: String? = null

    override suspend fun saveFeatureFlagsCache(json: String?) {
        cache = json
    }

    override suspend fun getFeatureFlagsCache(): String? = cache
}

class FeatureFlagsTest {
    // Shape from click-web app/api/me/features/route.ts.
    private val serverBody: JsonElement =
        Json.parseToJsonElement(
            """
            {"features":{
              "drops_develop":{"enabled":true,"config":{"develop_hours":"24"}},
              "shared_drops":{"enabled":true,"config":{"develop_hours":1}},
              "click_places":{"enabled":false,"config":{}},
              "ticket_wallet":{"enabled":true,"config":{}},
              "event_drops":{"enabled":"true","config":{}},
              "future_flag":{"enabled":true}
            }}
            """.trimIndent(),
        )

    @AfterTest
    fun tearDown() = runTest { FeatureFlags.reset(null) }

    @Test
    fun everythingIsOffByDefault() {
        FeatureFlag.entries.forEach { assertFalse(FeatureFlagsSnapshot.OFF.isEnabled(it), it.wireKey) }
    }

    @Test
    fun parsesOnlyLiteralTrueAsEnabled() {
        val parsed = parseFeatureFlagsResponse(serverBody)!!
        assertEquals(setOf("drops_develop", "shared_drops", "ticket_wallet", "future_flag"), parsed.keys)
    }

    @Test
    fun malformedBodiesAreRejected() {
        assertNull(parseFeatureFlagsResponse(Json.parseToJsonElement("""{"error":"Unauthorized"}""")))
        assertNull(parseFeatureFlagsResponse(Json.parseToJsonElement("""[]""")))
        assertEquals(emptyMap(), parseFeatureFlagsResponse(Json.parseToJsonElement("""{"features":{"x":1}}""")))
    }

    @Test
    fun configNumbersAcceptNumbersAndNumericStrings() {
        val snap = FeatureFlagsSnapshot("u1", parseFeatureFlagsResponse(serverBody)!!)
        assertEquals(24.0, snap.configNumber(FeatureFlag.DROPS_DEVELOP, "develop_hours", 1.0))
        assertEquals(1.0, snap.configNumber(FeatureFlag.SHARED_DROPS, "develop_hours", 24.0))
        assertEquals(360.0, snap.configNumber(FeatureFlag.SOUNDTRACK_PRESENCE, "heartbeat_seconds", 360.0))
    }

    @Test
    fun refreshEnablesAndPersistsPerUser() =
        runTest {
            val storage = FlagCacheStorage()
            FeatureFlags.refresh("u1", storage) { Result.success(serverBody) }
            assertTrue(FeatureFlags.isEnabled(FeatureFlag.DROPS_DEVELOP))
            assertTrue(FeatureFlags.isEnabled(FeatureFlag.TICKET_WALLET))
            assertFalse(FeatureFlags.isEnabled(FeatureFlag.CLICK_PLACES))
            assertFalse(FeatureFlags.isEnabled(FeatureFlag.EVENT_DROPS))

            // Cold start: the cached flags paint before any network call.
            FeatureFlags.reset(null)
            FeatureFlags.restore("u1", storage)
            assertTrue(FeatureFlags.isEnabled(FeatureFlag.DROPS_DEVELOP))
        }

    @Test
    fun anotherUsersCacheIsNeverRestored() =
        runTest {
            val storage = FlagCacheStorage()
            FeatureFlags.refresh("u1", storage) { Result.success(serverBody) }
            FeatureFlags.reset(null)
            FeatureFlags.restore("u2", storage)
            assertFalse(FeatureFlags.isEnabled(FeatureFlag.DROPS_DEVELOP))
            assertEquals("u2", FeatureFlags.state.value.userId)
        }

    @Test
    fun failedOrMalformedRefreshKeepsLastKnownFlags() =
        runTest {
            val storage = FlagCacheStorage()
            FeatureFlags.refresh("u1", storage) { Result.success(serverBody) }
            FeatureFlags.refresh("u1", storage) { Result.failure(IllegalStateException("offline")) }
            assertTrue(FeatureFlags.isEnabled(FeatureFlag.DROPS_DEVELOP))
            FeatureFlags.refresh("u1", storage) { Result.success(Json.parseToJsonElement("""{"oops":true}""")) }
            assertTrue(FeatureFlags.isEnabled(FeatureFlag.DROPS_DEVELOP))
        }

    @Test
    fun signOutTurnsEverythingOffAndDropsTheCache() =
        runTest {
            val storage = FlagCacheStorage()
            FeatureFlags.refresh("u1", storage) { Result.success(serverBody) }
            FeatureFlags.reset(storage)
            assertFalse(FeatureFlags.isEnabled(FeatureFlag.DROPS_DEVELOP))
            assertNull(storage.cache)
        }

    @Test
    fun corruptCacheIsIgnored() {
        assertNull(decodeFeatureFlagsCache("not json", "u1"))
        assertNull(decodeFeatureFlagsCache("""{"user_id":"u1"}""", "u1"))
        val ok = decodeFeatureFlagsCache("""{"user_id":"u1","features":{"shared_drops":{}}}""", "u1")
        assertTrue(ok!!.isEnabled(FeatureFlag.SHARED_DROPS))
    }
}
