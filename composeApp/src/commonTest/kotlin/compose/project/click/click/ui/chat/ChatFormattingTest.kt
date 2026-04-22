@file:OptIn(kotlin.time.ExperimentalTime::class)

package compose.project.click.click.ui.chat

import compose.project.click.click.data.models.Message
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks down the pure formatting helpers extracted out of ConnectionsScreen.kt.
 *
 * These are the strings users actually read in the connection list, chat
 * timeline day separators, and in-chat call-log system rows — any silent
 * regression would surface as a confusing UX (wrong relative time, wrong
 * call-log label, wrong day-bucket grouping).
 */
class ChatFormattingTest {

    // region formatCallDurationForLog

    @Test
    fun formatCallDurationForLog_zeroSeconds() {
        assertEquals("0s", formatCallDurationForLog(0))
    }

    @Test
    fun formatCallDurationForLog_negativeInputClampsToZero() {
        assertEquals("0s", formatCallDurationForLog(-42))
    }

    @Test
    fun formatCallDurationForLog_secondsOnlyBelowMinute() {
        assertEquals("5s", formatCallDurationForLog(5))
        assertEquals("59s", formatCallDurationForLog(59))
    }

    @Test
    fun formatCallDurationForLog_includesPaddedSecondsWhenMinutePresent() {
        assertEquals("1m 00s", formatCallDurationForLog(60))
        assertEquals("1m 05s", formatCallDurationForLog(65))
        assertEquals("12m 34s", formatCallDurationForLog(754))
    }

    // endregion

    // region formatChatAudioDuration / formatChatAudioPositionMs

    @Test
    fun audioDuration_prefersLiveMsOverFallbackSec() {
        // 5 seconds live player time beats the stale fallback.
        assertEquals("0:05", formatChatAudioDuration(durationMs = 5_000, fallbackSec = 99))
    }

    @Test
    fun audioDuration_fallsBackToMetadataWhenLiveMsIsZero() {
        assertEquals("0:30", formatChatAudioDuration(durationMs = 0, fallbackSec = 30))
    }

    @Test
    fun audioDuration_returnsZeroWhenNoSourceAvailable() {
        assertEquals("0:00", formatChatAudioDuration(durationMs = 0, fallbackSec = null))
        assertEquals("0:00", formatChatAudioDuration(durationMs = -500, fallbackSec = 0))
    }

    @Test
    fun audioDuration_padsSecondsAndRendersMinutes() {
        assertEquals("1:05", formatChatAudioDuration(durationMs = 65_000, fallbackSec = null))
        assertEquals("12:00", formatChatAudioDuration(durationMs = 720_000, fallbackSec = null))
    }

    @Test
    fun audioPosition_dropsSubSecondRemainder() {
        assertEquals("0:00", formatChatAudioPositionMs(0))
        assertEquals("0:00", formatChatAudioPositionMs(999))
        assertEquals("0:01", formatChatAudioPositionMs(1_500))
    }

    @Test
    fun audioPosition_clampsNegativeToZero() {
        assertEquals("0:00", formatChatAudioPositionMs(-5_000))
    }

    @Test
    fun audioPosition_rendersMinutesAndPaddedSeconds() {
        assertEquals("2:07", formatChatAudioPositionMs(2L * 60_000 + 7_500))
    }

    // endregion

    // region formatVibeCheckTime

    @Test
    fun formatVibeCheckTime_padsZeroMinutesAndSeconds() {
        assertEquals("00:00", formatVibeCheckTime(0))
        assertEquals("00:05", formatVibeCheckTime(5_000))
    }

    @Test
    fun formatVibeCheckTime_dropsSubSecondRemainder() {
        assertEquals("00:05", formatVibeCheckTime(5_999))
    }

    @Test
    fun formatVibeCheckTime_twoDigitMinutes() {
        assertEquals("10:30", formatVibeCheckTime((10 * 60 + 30) * 1000L))
    }

    // endregion

    // region formatMessageTime

    private fun timestampFor(hour: Int, minute: Int): Long {
        val zone = TimeZone.currentSystemDefault()
        val dt = LocalDateTime(2026, 1, 15, hour, minute, 0, 0)
        return dt.toInstant(zone).toEpochMilliseconds()
    }

    @Test
    fun formatMessageTime_midnightRendersAs12Am() {
        assertEquals("12:00 AM", formatMessageTime(timestampFor(0, 0)))
        assertEquals("12:30 AM", formatMessageTime(timestampFor(0, 30)))
    }

    @Test
    fun formatMessageTime_noonRendersAs12Pm() {
        assertEquals("12:00 PM", formatMessageTime(timestampFor(12, 0)))
    }

    @Test
    fun formatMessageTime_amHours() {
        assertEquals("1:00 AM", formatMessageTime(timestampFor(1, 0)))
        assertEquals("11:59 AM", formatMessageTime(timestampFor(11, 59)))
    }

    @Test
    fun formatMessageTime_pmHours() {
        assertEquals("1:00 PM", formatMessageTime(timestampFor(13, 0)))
        assertEquals("11:59 PM", formatMessageTime(timestampFor(23, 59)))
    }

    @Test
    fun formatMessageTime_padsSingleDigitMinutes() {
        assertEquals("3:07 PM", formatMessageTime(timestampFor(15, 7)))
    }

    // endregion

    // region messageDayKey

    @Test
    fun messageDayKey_groupsTimestampsByLocalCalendarDay() {
        val zone = TimeZone.currentSystemDefault()
        val morning = LocalDateTime(2026, 3, 14, 8, 0).toInstant(zone).toEpochMilliseconds()
        val evening = LocalDateTime(2026, 3, 14, 23, 59).toInstant(zone).toEpochMilliseconds()
        val nextDay = LocalDateTime(2026, 3, 15, 0, 1).toInstant(zone).toEpochMilliseconds()

        assertEquals(messageDayKey(morning), messageDayKey(evening))
        assertFalse(messageDayKey(morning) == messageDayKey(nextDay))
    }

    @Test
    fun messageDayKey_format() {
        val zone = TimeZone.currentSystemDefault()
        val ts = LocalDateTime(2026, 3, 14, 8, 0).toInstant(zone).toEpochMilliseconds()
        assertEquals("2026-3-14", messageDayKey(ts))
    }

    // endregion

    // region formatConversationDayLabel

    private fun localMs(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        val zone = TimeZone.currentSystemDefault()
        return LocalDateTime(year, month, day, hour, minute, 0, 0).toInstant(zone).toEpochMilliseconds()
    }

    @Test
    fun conversationDayLabel_todayForSameDate() {
        val now = localMs(2026, 3, 14, 15, 0)
        val morning = localMs(2026, 3, 14, 8, 0)
        assertEquals("Today", formatConversationDayLabel(morning, nowMs = now))
    }

    @Test
    fun conversationDayLabel_yesterdayForOneDayBack() {
        val now = localMs(2026, 3, 14, 15, 0)
        val yesterday = localMs(2026, 3, 13, 22, 0)
        assertEquals("Yesterday", formatConversationDayLabel(yesterday, nowMs = now))
    }

    @Test
    fun conversationDayLabel_weekdayNameWithinTheLastWeek() {
        // 2026-03-14 is a Saturday. 3 days earlier (2026-03-11) is a Wednesday.
        val now = localMs(2026, 3, 14, 15, 0)
        val wed = localMs(2026, 3, 11, 10, 0)
        val label = formatConversationDayLabel(wed, nowMs = now)
        assertEquals("Wednesday", label)
    }

    @Test
    fun conversationDayLabel_monthAndDayWhenOlderThanAWeekSameYear() {
        val now = localMs(2026, 3, 14, 15, 0)
        val old = localMs(2026, 1, 5, 10, 0)
        assertEquals("Jan 5", formatConversationDayLabel(old, nowMs = now))
    }

    @Test
    fun conversationDayLabel_monthDayYearWhenDifferentYear() {
        val now = localMs(2026, 3, 14, 15, 0)
        val lastYear = localMs(2024, 12, 25, 10, 0)
        assertEquals("Dec 25, 2024", formatConversationDayLabel(lastYear, nowMs = now))
    }

    // endregion

    // region callLogLabel

    private fun callLogMessage(
        state: String?,
        durationSeconds: Int? = null,
    ): Message = Message(
        id = "m1",
        user_id = "u1",
        content = "",
        timeCreated = 0L,
        messageType = "call_log",
        metadata = buildJsonObject {
            if (state != null) put("call_state", JsonPrimitive(state))
            if (durationSeconds != null) put("duration_seconds", JsonPrimitive(durationSeconds))
        },
    )

    @Test
    fun callLogLabel_missedCallMarkedMissed() {
        val (label, isMissed) = callLogLabel(callLogMessage("missed"))
        assertEquals("Missed Voice Call", label)
        assertTrue(isMissed)
    }

    @Test
    fun callLogLabel_declinedCallNotMissed() {
        val (label, isMissed) = callLogLabel(callLogMessage("declined"))
        assertEquals("Declined Call", label)
        assertFalse(isMissed)
    }

    @Test
    fun callLogLabel_completedCallIncludesDuration() {
        val (label, isMissed) = callLogLabel(callLogMessage("completed", durationSeconds = 125))
        assertEquals("Call Ended • 2m 05s", label)
        assertFalse(isMissed)
    }

    @Test
    fun callLogLabel_completedCallWithMissingDurationShowsZeroSeconds() {
        val (label, isMissed) = callLogLabel(callLogMessage("completed"))
        assertEquals("Call Ended • 0s", label)
        assertFalse(isMissed)
    }

    @Test
    fun callLogLabel_unknownStateFallsBackToCall() {
        val (label, isMissed) = callLogLabel(callLogMessage("ringing"))
        assertEquals("Call", label)
        assertFalse(isMissed)
    }

    @Test
    fun callLogLabel_missingMetadataFallsBackToCall() {
        val msg = Message(
            id = "m1",
            user_id = "u1",
            content = "",
            timeCreated = 0L,
            messageType = "call_log",
            metadata = null,
        )
        val (label, isMissed) = callLogLabel(msg)
        assertEquals("Call", label)
        assertFalse(isMissed)
    }

    // endregion

    // region formatConnectionListTimestamp
    //
    // Returns wall-clock-relative strings based on Clock.System.now(). We
    // can't easily stub the clock here without a bigger refactor, so we
    // just assert shape and bucket boundaries at roughly-now timestamps.

    @Test
    fun connectionListTimestamp_nearNowIsJustNow() {
        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        assertEquals("Just now", formatConnectionListTimestamp(nowMs - 1_000L))
    }

    @Test
    fun connectionListTimestamp_producesShortSuffixBuckets() {
        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val fiveMinutesAgo = nowMs - 5L * 60_000L - 500L
        val twoHoursAgo = nowMs - 2L * 3_600_000L - 500L
        val threeDaysAgo = nowMs - 3L * 86_400_000L - 500L
        val twoWeeksAgo = nowMs - 14L * 86_400_000L

        assertTrue(formatConnectionListTimestamp(fiveMinutesAgo).endsWith("m ago"))
        assertTrue(formatConnectionListTimestamp(twoHoursAgo).endsWith("h ago"))
        assertTrue(formatConnectionListTimestamp(threeDaysAgo).endsWith("d ago"))
        assertTrue(formatConnectionListTimestamp(twoWeeksAgo).endsWith("w ago"))
    }

    // endregion
}
