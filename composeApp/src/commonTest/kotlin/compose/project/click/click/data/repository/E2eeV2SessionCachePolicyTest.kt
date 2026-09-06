package compose.project.click.click.data.repository

import kotlin.test.Test
import kotlin.test.assertContentEquals

class E2eeV2SessionCachePolicyTest {
    @Test
    fun logoutZeroizesEveryCachedEpochKey() {
        val currentEpoch = byteArrayOf(1, 2, 3, 4)
        val previousEpoch = byteArrayOf(5, 6, 7, 8)

        zeroizeE2eeV2EpochKeys(
            listOf(
                listOf(currentEpoch, previousEpoch),
                listOf(currentEpoch),
            ),
        )

        assertContentEquals(byteArrayOf(0, 0, 0, 0), currentEpoch)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), previousEpoch)
    }
}
