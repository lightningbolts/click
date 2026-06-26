package compose.project.click.click.encounter

import compose.project.click.click.data.storage.FakeTokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingEncounterQueueTest {

    @Test
    fun enqueue_persistsHeardTokensAndDrainsFromSnapshot() = runTest {
        val storage = FakeTokenStorage(jwt = "jwt")
        val queue = PendingEncounterQueue(storage)

        queue.enqueue(
            myToken = "1234",
            heardTokens = listOf("5678", "9012"),
            latitude = 37.77,
            longitude = -122.42,
        )

        val reloaded = PendingEncounterQueue(storage)
        reloaded.hydrate()

        val snapshot = reloaded.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals("1234", snapshot.first().myToken)
        assertEquals(listOf("5678", "9012"), snapshot.first().heardTokens)
    }
}
