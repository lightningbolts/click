package compose.project.click.click.collaboration

import compose.project.click.click.data.api.DropDevelopResultDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ClickDropDevelopBookTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val pending = ClickDropRevealRef("p", now + 60.seconds)
    private val ready = ClickDropRevealRef("r", now - 3600.seconds)
    private val fresh = ClickDropRevealRef("f", now - 2.seconds)
    private val unknown = ClickDropRevealRef("u", null)

    private fun result(
        id: String,
        status: String = "developed",
        developedAt: String? = "2026-10-01T12:00:01Z",
        url: String? = "https://o/$id",
    ) = DropDevelopResultDto(kind = "chat", id = id, status = status, developedAt = developedAt, url = url)

    @Test
    fun statesFollowRevealAndDevelopedAt() {
        val book = ClickDropDevelopBook(developedAt = mapOf("r" to now))
        assertEquals(ClickDropDevelopState.Pending(pending.revealAt), book.state(pending, now))
        assertEquals(ClickDropDevelopState.Developed, book.state(ready, now))
        assertEquals(ClickDropDevelopState.Ready, book.state(fresh, now))
        assertEquals(ClickDropDevelopState.Pending(null), book.state(unknown, now))
    }

    @Test
    fun readyLeavesOutPendingDevelopedAndInFlight() {
        val book = ClickDropDevelopBook(developedAt = mapOf("r" to now), developing = setOf("x"))
        val x = ClickDropRevealRef("x", now - 10.seconds)
        assertEquals(listOf(fresh), book.ready(listOf(pending, ready, fresh, x, unknown), now))
    }

    @Test
    fun liveDevelopOnlyTakesDropsRevealedInTheLastFiveSeconds() {
        val book = ClickDropDevelopBook()
        assertEquals(listOf(fresh), book.justRevealed(listOf(ready, fresh, pending), now))
        assertEquals(emptyList(), book.justRevealed(listOf(fresh), now + 3.seconds))
    }

    @Test
    fun developResultsMarkDevelopedDropsFreshAndLeavePendingOnes() {
        val book =
            ClickDropDevelopBook()
                .startDeveloping(listOf("a", "b", "c"))
                .applyDevelopResults(
                    requested = listOf("a", "b", "c"),
                    results = listOf(result("a"), result("b", status = "pending"), result("c", status = "not_found")),
                    now = now,
                )
        assertEquals(mapOf("a" to Instant.parse("2026-10-01T12:00:01Z")), book.developedAt)
        assertEquals(setOf("a"), book.fresh)
        assertTrue(book.developing.isEmpty())
    }

    @Test
    fun developResultsKeepTheFirstDevelopedAtAndDoNotBloomTwice() {
        val first = Instant.parse("2026-09-01T00:00:00Z")
        val book =
            ClickDropDevelopBook(developedAt = mapOf("a" to first))
                .applyDevelopResults(listOf("a", "b"), listOf(result("a"), result("b", developedAt = null)), now)
        assertEquals(first, book.developedAt["a"])
        assertEquals(now, book.developedAt["b"])
        assertEquals(setOf("b"), book.fresh)
    }

    @Test
    fun resultsForDropsNotAskedForAreIgnored() {
        val book = ClickDropDevelopBook().applyDevelopResults(listOf("a"), listOf(result("z")), now)
        assertFalse(book.isDeveloped("z"))
    }

    @Test
    fun aFailedDevelopClearsInFlight() {
        val book = ClickDropDevelopBook().startDeveloping(listOf("a")).failDeveloping(listOf("a"))
        assertTrue(book.developing.isEmpty())
    }

    @Test
    fun viewsMergeWithoutBloomAndMarkAskedDropsChecked() {
        val book = ClickDropDevelopBook().mergeViews(listOf("r", "f"), mapOf("r" to now))
        assertTrue(book.isDeveloped("r"))
        assertTrue(book.fresh.isEmpty())
        assertEquals(setOf("r", "f"), book.checked)
        assertEquals(emptyList(), book.needingCheck(listOf(ready, fresh, pending), now))
        assertEquals(setOf("r"), book.uncheck(listOf("f")).checked)
    }

    @Test
    fun needingCheckOnlyAsksAboutRevealedUnknownDrops() {
        assertEquals(listOf(ready, fresh), ClickDropDevelopBook().needingCheck(listOf(pending, ready, fresh, unknown), now))
    }

    @Test
    fun aLocalOriginalCountsAsDevelopedWithoutBloom() {
        val book = ClickDropDevelopBook().markDevelopedLocally("r", now)
        assertEquals(ClickDropDevelopState.Developed, book.state(ready, now))
        assertTrue(book.fresh.isEmpty())
        assertEquals(book, book.markDevelopedLocally("r", now + 5.seconds))
    }

    @Test
    fun shownEndsTheBloom() {
        val book = ClickDropDevelopBook(fresh = setOf("a"))
        assertTrue(book.shown("a").fresh.isEmpty())
        assertEquals(book, book.shown("missing"))
    }

    @Test
    fun developAllLabelNeedsMoreThanOneReadyDrop() {
        assertNull(clickDropDevelopAllLabel(0))
        assertNull(clickDropDevelopAllLabel(1))
        assertEquals("Develop all (3)", clickDropDevelopAllLabel(3))
    }

    @Test
    fun nextRevealDelayWaitsForTheSoonestPendingDrop() {
        val soon = ClickDropRevealRef("s", now + 10.seconds)
        assertEquals(10_500L, clickDropNextRevealDelayMs(listOf(pending, soon, ready), now))
        assertEquals(60_000L, clickDropNextRevealDelayMs(listOf(ready, unknown), now))
        assertEquals(60_000L, clickDropNextRevealDelayMs(listOf(ClickDropRevealRef("far", now + 3600.seconds)), now))
    }
}

class ClickDropBloomTest {
    @Test
    fun matchesTheIosTiming() {
        assertEquals(listOf(12, 24, 48, 96), ClickDropBloom.LAYER_BLOCKS)
        assertEquals(920, ClickDropBloom.DURATION_MS)
    }

    @Test
    fun eachStageStartsAStaggerAfterTheOneBefore() {
        assertEquals(0f, ClickDropBloom.stageScale(0, 0))
        assertTrue(ClickDropBloom.stageScale(0, 1) > 0f)
        assertEquals(0f, ClickDropBloom.stageScale(1, 140))
        assertTrue(ClickDropBloom.stageScale(1, 141) > 0f)
        assertEquals(0f, ClickDropBloom.stageScale(3, 420))
    }

    @Test
    fun stagesEaseOutAndFinishAfterTheBloom() {
        val half = ClickDropBloom.stageScale(0, 250)
        assertTrue(half > 0.5f, "ease-out is past halfway at half time, was $half")
        assertEquals(1f, ClickDropBloom.stageScale(0, 500))
        assertEquals(1f, ClickDropBloom.stageScale(3, 920))
        var last = 0f
        for (ms in 0L..920L step 20) {
            val scale = ClickDropBloom.stageScale(2, ms)
            assertTrue(scale >= last)
            last = scale
        }
        assertFalse(ClickDropBloom.isFinished(919))
        assertTrue(ClickDropBloom.isFinished(920))
    }

    @Test
    fun aFinishedMaskCoversTheCornersInsideItsSoftEdge() {
        val w = 300f
        val h = 400f
        val radius = ClickDropBloom.stageRadius(3, ClickDropBloom.DURATION_MS.toLong(), w, h)
        val halfDiagonal = 250f
        assertTrue(radius * ClickDropBloom.SOFT_EDGE_START >= halfDiagonal)
        assertEquals(0f, ClickDropBloom.stageRadius(3, 0, w, h))
    }
}
