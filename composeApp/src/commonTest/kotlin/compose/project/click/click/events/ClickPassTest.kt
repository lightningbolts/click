package compose.project.click.click.events // pragma: allowlist secret

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClickPassTest {
    private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun parsesPass() {
        // click-web GET /api/beacons/{id}/pass
        val pass =
            parseClickPass(
                obj("""{"credential_url":"https://joinclick.co/pass/abc","code":"K7Q-2PX","checked_in_at":null,"wallet_available":true}"""),
            )!!
        assertEquals("https://joinclick.co/pass/abc", pass.credentialUrl)
        assertEquals("K7Q-2PX", pass.code)
        assertNull(pass.checkedInAtMs)
        assertNull(parseClickPass(obj("""{"code":"x"}""")), "no credential, no pass")
    }

    @Test
    fun parsesScanAndUnknownResultsAreInvalid() {
        val scan =
            parsePassScan(
                obj(
                    """{"result":"already_checked_in","attendee":{"user_id":"u","name":"Lena","avatar_url":null},
                       "checked_in_at":"2026-11-07T03:42:00Z"}""",
                ),
            )
        assertEquals(PassScanResult.ALREADY_CHECKED_IN, scan.result)
        assertEquals("Lena", scan.holderName)
        assertEquals(PassScanResult.INVALID, parsePassScan(obj("""{"result":"???"}""")).result)
    }

    @Test
    fun verdictsMatchIos() {
        val clock: (Long) -> String = { "7:42 PM" }

        fun v(
            result: PassScanResult,
            count: Int? = null,
            at: Long? = null,
        ) = PassScan(result, null, null, at, count).verdict(clock)
        assertEquals(PassVerdict("Checked in", "12 here now", true), v(PassScanResult.CHECKED_IN, count = 12))
        assertEquals(
            PassVerdict("Already checked in", "At 7:42 PM. Make sure it's them.", false),
            v(PassScanResult.ALREADY_CHECKED_IN, at = 1),
        )
        assertEquals("Not on the list", v(PassScanResult.NOT_GOING).title)
        assertEquals("Pass for another event", v(PassScanResult.WRONG_EVENT).title)
        assertEquals("Refunded", v(PassScanResult.REFUNDED).title)
        assertEquals("Event cancelled", v(PassScanResult.EVENT_CANCELLED).title)
        assertEquals(PassVerdict("Not a Click Pass", null, false), v(PassScanResult.INVALID))
    }
}
