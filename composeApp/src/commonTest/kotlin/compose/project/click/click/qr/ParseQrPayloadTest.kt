package compose.project.click.click.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseQrPayloadTest {

    private val fixtureUuid = "11111111-2222-3333-4444-555555555555"

    @Test
    fun branchA_universalLink_extractsUuid() {
        val url = "https://click-us.vercel.app/c/$fixtureUuid"
        assertEquals(fixtureUuid, parseQrPayload(url))
    }

    @Test
    fun branchA_httpWithQuery_extractsUuid() {
        val url = "https://click-us.vercel.app/c/$fixtureUuid?utm_source=qr"
        assertEquals(fixtureUuid, parseQrPayload(url))
    }

    @Test
    fun branchB_legacyJson_extractsUserId() {
        val json = """{"userId":"$fixtureUuid","name":"Alex"}"""
        assertEquals(fixtureUuid, parseQrPayload(json))
    }

    @Test
    fun legacyConnectUrl_delegatedViaParseQrCode_notParseQrPayloadAlone() {
        val url = "https://click-us.vercel.app/connect/$fixtureUuid"
        assertNull(parseQrPayload(url))
        val parsed = parseQrCode(url)
        assertEquals(fixtureUuid, (parsed as QrParseResult.Legacy).userId)
    }

    @Test
    fun tokenJson_notMatchedByPolyglotParser() {
        val json = """{"token":"abc","userId":"$fixtureUuid","exp":9999999999999}"""
        assertNull(parseQrPayload(json))
        val parsed = parseQrCode(json)
        assertEquals(fixtureUuid, (parsed as QrParseResult.TokenBased).payload.userId)
    }

    @Test
    fun tokenUniversalLink_routesThroughTokenFlow() {
        val url = "https://click-us.vercel.app/c/$fixtureUuid?token=abc123&exp=9999999999999&iat=123"
        assertNull(parseQrPayload(url))
        val parsed = parseQrCode(url) as QrParseResult.TokenBased
        assertEquals(fixtureUuid, parsed.payload.userId)
        assertEquals("abc123", parsed.payload.token)
        assertEquals(9999999999999L, parsed.payload.exp)
        assertEquals(123L, parsed.payload.issuedAt)
    }

    @Test
    fun tokenUniversalLink_supportsAppClipAliasesAndVenue() {
        val url = "https://click-us.vercel.app/c/$fixtureUuid?qt=abc123&expires_at=9999999999999&venue_id=hub-1"
        val parsed = parseQrCode(url) as QrParseResult.TokenBased
        assertEquals(fixtureUuid, parsed.payload.userId)
        assertEquals("abc123", parsed.payload.token)
        assertEquals("hub-1", parsed.payload.venueId)
    }

    @Test
    fun garbage_returnsNull() {
        assertNull(parseQrPayload("not-a-valid-code"))
        assertNull(parseQrPayload(""))
        assertNull(parseQrPayload("   "))
    }

    @Test
    fun legacyJson_blankUserId_returnsNull() {
        assertNull(parseQrPayload("""{"userId":"","name":"x"}"""))
    }
}
