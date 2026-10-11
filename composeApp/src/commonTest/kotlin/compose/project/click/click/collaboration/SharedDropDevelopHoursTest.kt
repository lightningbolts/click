package compose.project.click.click.collaboration

import compose.project.click.click.core.FeatureFlagsState
import compose.project.click.click.core.parseFeatureFlags
import compose.project.click.click.ui.drops.sharedDropDisclosure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `shared_drops.config.develop_hours` drives the develop copy (sheet 08 G2-3 / G1-1). */
class SharedDropDevelopHoursTest {
    private fun flags(config: String) =
        FeatureFlagsState(
            userId = "u1",
            features = parseFeatureFlags("""{"features":{"shared_drops":{"enabled":true,"config":$config}}}""").orEmpty(),
        )

    @Test
    fun defaultsToServerSeedWhenFlagsHaveNotLoaded() {
        assertEquals(1, sharedDropDevelopHours(FeatureFlagsState.Off))
        assertEquals(1, sharedDropDevelopHours(flags("{}")))
    }

    @Test
    fun readsDevelopHoursFromConfig() {
        assertEquals(3, sharedDropDevelopHours(flags("""{"develop_hours":3}""")))
    }

    @Test
    fun disclosureCopyFollowsConfig() {
        assertTrue(sharedDropDisclosure(1).startsWith("It develops for them in an hour,"))
        assertTrue(
            sharedDropDisclosure(sharedDropDevelopHours(flags("""{"develop_hours":3}"""))).startsWith("It develops for them in 3 hours,"),
        )
    }
}
