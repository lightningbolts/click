package compose.project.click.click.ui.screens

import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.data.storage.TokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NearbySortPreferenceTest {
    @Test
    fun storedValuesMatchIosRawValues() {
        assertEquals(
            listOf("relevance", "distance", "rising", "new", "alphabetical"),
            NearbySort.entries.map { it.storageValue },
        )
    }

    @Test
    fun unknownOrMissingValuesFallBackToRelevance() {
        assertEquals(NearbySort.Relevance, NearbySort.fromStorage(null))
        assertEquals(NearbySort.Relevance, NearbySort.fromStorage(""))
        assertEquals(NearbySort.Relevance, NearbySort.fromStorage("popular"))
        assertEquals(NearbySort.Rising, NearbySort.fromStorage("rising"))
        assertEquals(NearbySort.New, NearbySort.fromStorage(" new "))
    }

    @Test
    fun theChoiceSurvivesANewStoreRead() =
        runTest {
            val storage = FakeTokenStorage()
            assertEquals(NearbySort.Relevance, NearbySortPreference.load(storage))
            NearbySortPreference.save(storage, NearbySort.Alphabetical)
            assertEquals(NearbySort.Alphabetical, NearbySortPreference.load(storage))
            NearbySort.entries.forEach { sort ->
                NearbySortPreference.save(storage, sort)
                assertEquals(sort, NearbySortPreference.load(storage))
            }
        }

    @Test
    fun aFailingStoreKeepsTheDefault() =
        runTest {
            val broken =
                object : TokenStorage by FakeTokenStorage() {
                    override suspend fun getNearbySort(): String? = error("store unavailable")

                    override suspend fun saveNearbySort(value: String?): Unit = error("store unavailable")
                }
            NearbySortPreference.save(broken, NearbySort.Distance)
            assertEquals(NearbySort.Relevance, NearbySortPreference.load(broken))
        }
}
