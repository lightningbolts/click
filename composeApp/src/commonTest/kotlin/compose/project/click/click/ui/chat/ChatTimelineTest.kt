@file:OptIn(kotlin.time.ExperimentalTime::class)

package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageDeliveryState
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks down the pure timeline-entry builders used by the chat
 * LazyColumn. Day-separator insertion is the critical invariant: a
 * regression causes either "Today" repeating mid-list or missing
 * labels at day boundaries.
 */
class ChatTimelineTest {

    private val zone: TimeZone = TimeZone.currentSystemDefault()

    private fun tsLocal(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute, 0, 0)
            .toInstant(zone)
            .toEpochMilliseconds()

    private fun user(id: String = "u1"): User = User(id = id, name = "User $id")

    private fun mwu(id: String, timestamp: Long, userId: String = "u1"): MessageWithUser =
        MessageWithUser(
            message = Message(
                id = id,
                user_id = userId,
                content = "m-$id",
                timeCreated = timestamp,
            ),
            user = user(userId),
            isSent = userId == "u1",
        )

    // region buildChatTimelineEntries (oldest-first)

    @Test
    fun oldestFirst_emptyInputProducesEmptyOutput() {
        assertTrue(buildChatTimelineEntries(emptyList()).isEmpty())
    }

    @Test
    fun oldestFirst_singleDayProducesOneSeparatorFollowedByMessages() {
        val day = tsLocal(2026, 4, 1, 9, 0)
        val later = tsLocal(2026, 4, 1, 15, 0)
        val entries = buildChatTimelineEntries(
            listOf(mwu("a", day), mwu("b", later))
        )

        assertEquals(3, entries.size)
        assertTrue(entries[0] is ChatTimelineEntry.DaySeparator)
        assertTrue(entries[1] is ChatTimelineEntry.MessageEntry)
        assertTrue(entries[2] is ChatTimelineEntry.MessageEntry)
        assertEquals("a", (entries[1] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id)
        assertEquals("b", (entries[2] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id)
    }

    @Test
    fun oldestFirst_insertsSeparatorWhenDayChangesForward() {
        val day1 = tsLocal(2026, 4, 1, 22, 0)
        val day2 = tsLocal(2026, 4, 2, 7, 0)
        val day3 = tsLocal(2026, 4, 5, 7, 0)
        val entries = buildChatTimelineEntries(
            listOf(mwu("a", day1), mwu("b", day2), mwu("c", day3))
        )

        // Expect: [sep1, a, sep2, b, sep3, c]
        assertEquals(6, entries.size)
        val positions = entries.mapIndexedNotNull { i, e ->
            if (e is ChatTimelineEntry.DaySeparator) i else null
        }
        assertEquals(listOf(0, 2, 4), positions)
    }

    @Test
    fun oldestFirst_entryKeysAreStableAndUnique() {
        val day1 = tsLocal(2026, 4, 1, 22, 0)
        val day2 = tsLocal(2026, 4, 2, 7, 0)
        val entries = buildChatTimelineEntries(
            listOf(mwu("a", day1), mwu("b", day2))
        )
        val keys = entries.map { it.key }
        assertEquals(keys.toSet().size, keys.size, "Keys must be unique, got $keys")
    }

    // endregion

    // region buildChatTimelineEntriesNewestFirst (reverseLayout)

    @Test
    fun newestFirst_emptyInputProducesEmptyOutput() {
        assertTrue(buildChatTimelineEntriesNewestFirst(emptyList()).isEmpty())
    }

    @Test
    fun newestFirst_singleDayHasTrailingSeparatorOnly() {
        val first = tsLocal(2026, 4, 1, 9, 0)
        val second = tsLocal(2026, 4, 1, 15, 0)
        // Input is oldest-first; helper reverses it internally.
        val entries = buildChatTimelineEntriesNewestFirst(
            listOf(mwu("a", first), mwu("b", second))
        )
        // Expected newest-first order: b, a, trailing separator.
        assertEquals(3, entries.size)
        assertTrue(entries[0] is ChatTimelineEntry.MessageEntry)
        assertEquals(
            "b",
            (entries[0] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id,
        )
        assertTrue(entries[1] is ChatTimelineEntry.MessageEntry)
        assertEquals(
            "a",
            (entries[1] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id,
        )
        assertTrue(entries[2] is ChatTimelineEntry.DaySeparator)
    }

    @Test
    fun newestFirst_insertsSeparatorsBetweenDayBoundariesAndOneTrailing() {
        val day1a = tsLocal(2026, 4, 1, 10, 0)
        val day1b = tsLocal(2026, 4, 1, 18, 0)
        val day2 = tsLocal(2026, 4, 2, 9, 0)
        val day3 = tsLocal(2026, 4, 5, 9, 0)

        val entries = buildChatTimelineEntriesNewestFirst(
            listOf(mwu("a", day1a), mwu("b", day1b), mwu("c", day2), mwu("d", day3))
        )
        // Newest-first walk: d, sep(day2), c, sep(day1), b, a, sep(day1-tail)
        assertEquals(7, entries.size)
        assertEquals(
            "d",
            (entries[0] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id,
        )
        assertTrue(entries[1] is ChatTimelineEntry.DaySeparator)
        assertEquals(
            "c",
            (entries[2] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id,
        )
        assertTrue(entries[3] is ChatTimelineEntry.DaySeparator)
        assertEquals(
            "b",
            (entries[4] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id,
        )
        assertEquals(
            "a",
            (entries[5] as ChatTimelineEntry.MessageEntry).messageWithUser.message.id,
        )
        assertTrue(entries[6] is ChatTimelineEntry.DaySeparator)
    }

    @Test
    fun newestFirst_trailingSeparatorUsesOldestMessageTimestampForItsDay() {
        // The trailing separator should label the oldest day in the window,
        // not the newest message's day — otherwise the label at the bottom
        // of the list would lie.
        val day1a = tsLocal(2026, 4, 1, 9, 0)
        val day1b = tsLocal(2026, 4, 1, 18, 0)
        val day2 = tsLocal(2026, 4, 2, 9, 0)

        val entries = buildChatTimelineEntriesNewestFirst(
            listOf(mwu("a", day1a), mwu("b", day1b), mwu("c", day2))
        )
        val trailing = entries.last() as ChatTimelineEntry.DaySeparator
        // The trailing separator is labeled using the earliest message's day
        // (day1). formatConversationDayLabel depends on "now", so we just
        // verify the key is stamped with the day1 dayKey, not the day2 one.
        assertTrue(
            trailing.key.endsWith(messageDayKey(day1a)),
            "Trailing separator key should end with day1 key, got ${trailing.key}",
        )
    }

    @Test
    fun newestFirst_keysAreUnique() {
        val day1 = tsLocal(2026, 4, 1, 9, 0)
        val day2 = tsLocal(2026, 4, 2, 9, 0)
        val entries = buildChatTimelineEntriesNewestFirst(
            listOf(mwu("a", day1), mwu("b", day2))
        )
        val keys = entries.map { it.key }
        assertEquals(keys.toSet().size, keys.size, "Keys must be unique, got $keys")
    }

    @Test
    fun stableRowKey_matchesOptimisticAndDeliveredOutboundWithSameLocalSentAt() {
        val t = 1_700_000_000_000L
        val optimistic = MessageWithUser(
            message = Message(
                id = "temp-$t-1",
                user_id = "u1",
                content = "hi",
                timeCreated = t,
                localSentAt = t,
                deliveryState = MessageDeliveryState.PENDING,
            ),
            user = user("u1"),
            isSent = true,
        )
        val delivered = MessageWithUser(
            message = Message(
                id = "real-uuid",
                user_id = "u1",
                content = "hi",
                timeCreated = t + 80,
                localSentAt = t,
                deliveryState = MessageDeliveryState.SENT,
            ),
            user = user("u1"),
            isSent = true,
        )
        assertEquals(chatBubbleStableRowKey(optimistic), chatBubbleStableRowKey(delivered))
    }

    // endregion
}
