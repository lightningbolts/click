package compose.project.click.click.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class OlderMessagesPaginationTest {

    @Test
    fun nullFetch_keepsHasMore() {
        assertEquals(
            OlderMessagesPageOutcome.KeepHasMore,
            olderMessagesPageOutcome(fetched = null, authReadyAfterRetry = true),
        )
    }

    @Test
    fun emptyWithoutAuth_keepsHasMore() {
        assertEquals(
            OlderMessagesPageOutcome.KeepHasMore,
            olderMessagesPageOutcome(fetched = emptyList<Any>(), authReadyAfterRetry = false),
        )
    }

    @Test
    fun emptyAfterAuth_isEndOfHistory() {
        assertEquals(
            OlderMessagesPageOutcome.EndOfHistory,
            olderMessagesPageOutcome(fetched = emptyList<Any>(), authReadyAfterRetry = true),
        )
    }

    @Test
    fun nonEmpty_merges() {
        assertEquals(
            OlderMessagesPageOutcome.MergePage,
            olderMessagesPageOutcome(fetched = listOf(1), authReadyAfterRetry = true),
        )
    }
}
