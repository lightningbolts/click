package compose.project.click.click.data.repository

import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeaconPagingTest {
    private fun beacon(id: String) =
        MapBeacon(id = id, kind = MapBeaconKind.OTHER, latitude = 1.0, longitude = 2.0, metadata = MapBeaconMetadata())

    private fun page(
        vararg ids: String,
        next: String? = null,
    ) = BeaconPage(ids.map { beacon(it) }, next)

    @Test
    fun parseBeaconPage_readsBeaconsAndNextCursor() {
        val body =
            """{"beacons":[{"id":"b1","beacon_type":"event","lat":1,"lon":2,"rsvp_count":12}],"radius_meters":5000,"next_cursor":"abc"}"""
        val parsed = parseBeaconPage(body)
        assertEquals(listOf("b1"), parsed.beacons.map { it.id })
        assertEquals(12, parsed.beacons.single().rsvpCount)
        assertEquals("abc", parsed.nextCursor)
    }

    @Test
    fun parseBeaconPage_nullOrEmptyCursorEndsPaging() {
        assertNull(parseBeaconPage("""{"beacons":[],"next_cursor":null}""").nextCursor)
        assertNull(parseBeaconPage("""{"beacons":[],"next_cursor":""}""").nextCursor)
        assertNull(parseBeaconPage("""{"beacons":[]}""").nextCursor)
    }

    @Test
    fun parseBeaconPage_legacyShapesStillParse() {
        assertEquals(1, parseBeaconPage("""[{"id":"b1","lat":1,"lon":2}]""").beacons.size)
        assertEquals(1, parseBeaconPage("""{"data":[{"id":"b1","lat":1,"lon":2}]}""").beacons.size)
        assertTrue(parseBeaconPage("").beacons.isEmpty())
        assertTrue(parseBeaconPage("not json").beacons.isEmpty())
    }

    @Test
    fun reducer_shortPageWithCursorKeepsPaging() {
        // A page can be short (cut before visibility filtering) while more remain.
        val state = BeaconPagingState().reduce(BeaconPagingEvent.PageLoaded(page("a", next = "c1")))
        assertFalse(state.done)
        assertEquals("c1", state.nextCursorToFetch)
    }

    @Test
    fun reducer_nullCursorIsTheEnd() {
        val state =
            BeaconPagingState()
                .reduce(BeaconPagingEvent.PageLoaded(page("a", next = "c1")))
                .reduce(BeaconPagingEvent.PageLoaded(page("b")))
        assertTrue(state.done)
        assertNull(state.nextCursorToFetch)
        assertEquals(listOf("a", "b"), state.beacons.map { it.id })
    }

    @Test
    fun reducer_dropsRepeatedRows() {
        val state =
            BeaconPagingState()
                .reduce(BeaconPagingEvent.PageLoaded(page("a", "b", next = "c1")))
                .reduce(BeaconPagingEvent.PageLoaded(page("b", "c")))
        assertEquals(listOf("a", "b", "c"), state.beacons.map { it.id })
    }

    @Test
    fun reducer_stopsAtPageCeiling() {
        var state = BeaconPagingState(maxPages = 2)
        state = state.reduce(BeaconPagingEvent.PageLoaded(page("a", next = "c1")))
        state = state.reduce(BeaconPagingEvent.PageLoaded(page("b", next = "c2")))
        assertTrue(state.done)
        assertNull(state.nextCursorToFetch)
    }

    @Test
    fun reducer_laterFailureKeepsLoadedRows() {
        val state =
            BeaconPagingState()
                .reduce(BeaconPagingEvent.PageLoaded(page("a", next = "c1")))
                .reduce(BeaconPagingEvent.PageFailed)
        assertTrue(state.done)
        assertFalse(state.failed)
        assertEquals(listOf("a"), state.beacons.map { it.id })
    }

    @Test
    fun reducer_firstPageFailureFails() {
        val state = BeaconPagingState().reduce(BeaconPagingEvent.PageFailed)
        assertTrue(state.failed)
        assertTrue(state.done)
    }

    @Test
    fun pageBeacons_followsCursorsAndReportsEachPage() =
        runTest {
            val requested = mutableListOf<String?>()
            val reported = mutableListOf<Int>()
            val pages = mapOf<String?, BeaconPage>(null to page("a", next = "c1"), "c1" to page("b", next = "c2"), "c2" to page("c"))
            val result =
                pageBeacons(onPage = { reported += it.size }) { cursor ->
                    requested += cursor
                    Result.success(pages.getValue(cursor))
                }
            assertEquals(listOf(null, "c1", "c2"), requested)
            assertEquals(listOf(1, 2, 3), reported)
            assertEquals(listOf("a", "b", "c"), result.getOrThrow().map { it.id })
        }

    @Test
    fun pageBeacons_firstPageErrorIsAFailure() =
        runTest {
            val result = pageBeacons { Result.failure(IllegalStateException("boom")) }
            assertTrue(result.isFailure)
            assertEquals("boom", result.exceptionOrNull()?.message)
        }

    @Test
    fun pageBeacons_laterErrorKeepsWhatLoaded() =
        runTest {
            val result =
                pageBeacons { cursor ->
                    if (cursor == null) Result.success(page("a", next = "c1")) else Result.failure(IllegalStateException("x"))
                }
            assertEquals(listOf("a"), result.getOrThrow().map { it.id })
        }

    @Test
    fun pageBeacons_honoursMaxPages() =
        runTest {
            var calls = 0
            pageBeacons(maxPages = 3) {
                calls++
                Result.success(page("p$calls", next = "next$calls"))
            }
            assertEquals(3, calls)
        }
}
