package compose.project.click.click.events

import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClickPassTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String): PassScan = json.decodeFromString(PassScanResponse.serializer(), body).toPassScan()

    private fun scanned(
        result: String,
        extra: String = "",
    ): PassScanLook =
        passScanLook(
            PassScanOutcome.Scanned(decode("""{"result":"$result","attendee":{"user_id":"u1","name":"Alex Chen"}$extra}""")),
            TimeZone.UTC,
        )

    @Test
    fun everyWireResult_decodes() {
        val expected =
            mapOf(
                "checked_in" to PassScanResult.CHECKED_IN,
                "already_checked_in" to PassScanResult.ALREADY_CHECKED_IN,
                "not_going" to PassScanResult.NOT_GOING,
                "wrong_event" to PassScanResult.WRONG_EVENT,
                "invalid" to PassScanResult.INVALID,
                "refunded" to PassScanResult.REFUNDED,
                "event_cancelled" to PassScanResult.EVENT_CANCELLED,
            )
        expected.forEach { (wire, result) -> assertEquals(result, decode("""{"result":"$wire"}""").result, wire) }
    }

    @Test
    fun unknownResult_isInvalid() {
        assertEquals(PassScanResult.INVALID, decode("""{"result":"something_new"}""").result)
        assertEquals(PassScanResult.INVALID, PassScanResult.fromWire(null))
    }

    @Test
    fun ticketScan_carriesTierAndHolder() {
        // iOS EventDetailTests.ticketScan fixture.
        val scan =
            decode("""{"result":"refunded","tier_name":"VIP","attendee":{"user_id":"u1","name":"Alex Chen"},"checked_in_at":null}""")
        assertEquals(PassScanResult.REFUNDED, scan.result)
        assertEquals("VIP", scan.tierName)
        assertEquals("Alex Chen", scan.holder?.name)
    }

    @Test
    fun passScan_withoutTierOrHolder() {
        val scan = decode("""{"result":"invalid","attendee":null,"checked_in_at":null}""")
        assertNull(scan.tierName)
        assertNull(scan.holder)
        assertNull(decode("""{"result":"checked_in","tier_name":"  "}""").tierName)
    }

    @Test
    fun scannerCopy_matchesTheTable() {
        assertEquals(
            PassScanLook("Checked in", "12 here now", PassScanTone.SUCCESS),
            scanned("checked_in", ""","check_in_count":12"""),
        )
        val already = scanned("already_checked_in", ""","checked_in_at":"2026-10-08T02:05:00Z"""")
        assertEquals("Already checked in", already.title)
        assertEquals(PassScanTone.WARNING, already.tone)
        val detail = already.detail.orEmpty()
        assertTrue(detail.startsWith("At ") && detail.endsWith(". Make sure it's them."), detail)
        assertEquals(
            PassScanLook("Not on the list", "Their RSVP isn't active for this event.", PassScanTone.ERROR),
            scanned("not_going"),
        )
        assertEquals(PassScanLook("Pass for another event", null, PassScanTone.WARNING), scanned("wrong_event"))
        assertEquals(PassScanLook("Not a Click Pass", null, PassScanTone.ERROR), scanned("invalid"))
        assertEquals(PassScanLook("Refunded", "This ticket was refunded.", PassScanTone.ERROR), scanned("refunded"))
        assertEquals(
            PassScanLook("Event cancelled", "Tickets for this event no longer admit anyone.", PassScanTone.ERROR),
            scanned("event_cancelled"),
        )
        assertEquals(
            PassScanLook("Couldn't check this pass", "Offline", PassScanTone.ERROR),
            passScanLook(PassScanOutcome.Failed("Offline")),
        )
    }

    @Test
    fun scannerSubtitle() {
        assertEquals("3 checked in so far", passScannerSubtitle(3))
        assertEquals("Guests find it on the event page once they RSVP.", passScannerSubtitle(null))
    }

    @Test
    fun scanGate_ignoresTheSameValueForThreeSeconds() {
        assertEquals(3_000L, PASS_SCAN_REREAD_WINDOW_MS)
        val gate = PassScanGate()
        assertTrue(gate.shouldSend("pass-a", nowMs = 10_000))
        assertFalse(gate.shouldSend("pass-a", nowMs = 10_500))
        assertFalse(gate.shouldSend("pass-a", nowMs = 12_999))
        assertTrue(gate.shouldSend("pass-a", nowMs = 13_000))
    }

    @Test
    fun scanGate_letsADifferentValueThroughAtOnce() {
        val gate = PassScanGate()
        assertTrue(gate.shouldSend("pass-a", nowMs = 10_000))
        assertTrue(gate.shouldSend("pass-b", nowMs = 10_100))
        assertTrue(gate.shouldSend("pass-a", nowMs = 10_200))
    }

    @Test
    fun scanGate_resetAfterNetworkFailure_allowsImmediateRetry() {
        val gate = PassScanGate()
        assertTrue(gate.shouldSend("pass-a", nowMs = 10_000))
        gate.reset()
        assertTrue(gate.shouldSend("pass-a", nowMs = 10_001))
    }

    private val hour = 60L * 60_000L
    private val start = 1_791_424_800_000L // 2026-10-08T02:00:00Z
    private val schedule = EventSchedule(startEpochMs = start, endEpochMs = start + 2 * hour)
    private val pass = ClickPass(credentialUrl = "https://joinclick.co/e/e1?pass=abc", code = "K7P-4QX")

    @Test
    fun polling_onlyWithinAnHourOfStartAndBeforeTheEnd() {
        assertEquals(4_000L, CLICK_PASS_POLL_MS)
        assertFalse(clickPassShouldPoll(pass, schedule, start - 2 * hour))
        assertFalse(clickPassShouldPoll(pass, schedule, start - hour))
        assertTrue(clickPassShouldPoll(pass, schedule, start - hour + 1))
        assertTrue(clickPassShouldPoll(pass, schedule, start + hour))
        assertFalse(clickPassShouldPoll(pass, schedule, start + 2 * hour))
    }

    @Test
    fun polling_stopsOnceCheckedInOrWithoutAPassOrSchedule() {
        val inside = start - 10 * 60_000L
        assertFalse(clickPassShouldPoll(pass.copy(checkedInAt = "2026-10-08T01:55:00Z"), schedule, inside))
        assertFalse(clickPassShouldPoll(null, schedule, inside))
        assertFalse(clickPassShouldPoll(pass, null, inside))
    }

    @Test
    fun passStatusLine() {
        assertEquals("Going", clickPassStatusLine(pass, live = false))
        assertEquals("Going · show this at the door", clickPassStatusLine(pass, live = true))
        val checkedIn = pass.copy(checkedInAt = "2026-10-08T02:05:00Z")
        val line = clickPassStatusLine(checkedIn, live = true, timeZone = TimeZone.UTC)
        assertTrue(line.startsWith("You're in · checked in "), line)
        assertEquals("You're checked in", clickPassCardSubtitle(checkedIn))
        assertEquals("Show it at the door", clickPassCardSubtitle(pass))
    }

    @Test
    fun passDecodes_fromTheRoute() {
        val decoded =
            json.decodeFromString(
                ClickPass.serializer(),
                """{"credential_url":"https://joinclick.co/e/e1?pass=abc","code":"K7P-4QX","checked_in_at":null,"wallet_available":true}""",
            )
        assertEquals("K7P-4QX", decoded.code)
        assertFalse(decoded.isCheckedIn)
        assertTrue(decoded.walletAvailable)
    }
}
