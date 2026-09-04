package compose.project.click.click.data

import compose.project.click.click.data.models.CachedAppSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubRevocationPersistenceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun cachedSnapshotWithoutRevocations_remainsBackwardCompatible() {
        val snapshot = json.decodeFromString<CachedAppSnapshot>("{}")

        assertEquals(emptySet(), snapshot.revokedHubIds)
    }

    @Test
    fun revokedHubIds_arePersistedAndBoundedWithoutDroppingA16EventBurst() {
        val ids = (0..MAX_PERSISTED_HUB_ACCESS_REVOCATIONS + 16).map { "hub_$it" }
        val bounded = boundedHubAccessRevocationIds(ids)
        val encoded = json.encodeToString(CachedAppSnapshot(revokedHubIds = bounded))
        val restored = json.decodeFromString<CachedAppSnapshot>(encoded)

        assertEquals(MAX_PERSISTED_HUB_ACCESS_REVOCATIONS, restored.revokedHubIds.size)
        assertFalse("hub_0" in restored.revokedHubIds)
        assertTrue("hub_${MAX_PERSISTED_HUB_ACCESS_REVOCATIONS + 16}" in restored.revokedHubIds)
    }
}
