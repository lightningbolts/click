package compose.project.click.click.deeplink

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionDeepLinkRouterTest {
    private val uuid = "11111111-2222-3333-4444-555555555555"

    @AfterTest
    fun tearDown() {
        ConnectionDeepLinkRouter.consume()
    }

    @Test
    fun plainUniversalLink_queuesUserIdWithoutToken() {
        assertTrue(ConnectionDeepLinkRouter.handleIncomingUrl("https://joinclick.co/c/$uuid"))
        val pending = ConnectionDeepLinkRouter.consume()
        assertEquals(ConnectionInvocation(userId = uuid), pending)
        assertNull(ConnectionDeepLinkRouter.pendingConnection.value)
    }

    @Test
    fun tokenUniversalLink_keepsTokenExpAndVenue() {
        val url = "https://joinclick.co/c/$uuid?token=abc123&exp=9999999999999&iat=1&venue_id=hub-1"
        assertTrue(ConnectionDeepLinkRouter.handleIncomingUrl(url))
        val pending = ConnectionDeepLinkRouter.pendingConnection.value
        assertEquals(uuid, pending?.userId)
        assertEquals("abc123", pending?.token)
        assertEquals(9999999999999L, pending?.exp)
        assertEquals("hub-1", pending?.venueId)
        assertEquals(uuid, ConnectionDeepLinkRouter.parseConnectionUserId(url))
    }

    @Test
    fun tAlias_isAcceptedAsToken() {
        val invocation = ConnectionDeepLinkRouter.parseConnectionInvocation("https://joinclick.co/c/$uuid?t=tok&exp=9999999999999")
        assertEquals("tok", invocation?.token)
        assertEquals(uuid, invocation?.userId)
    }

    @Test
    fun tokenWithoutExp_stillCarriesToken() {
        val invocation = ConnectionDeepLinkRouter.parseConnectionInvocation("click://c/$uuid?token=tok&venue=v1")
        assertEquals(uuid, invocation?.userId)
        assertEquals("tok", invocation?.token)
        assertNull(invocation?.exp)
        assertEquals("v1", invocation?.venueId)
    }

    @Test
    fun customSchemeConnect_parsesUserId() {
        assertEquals(uuid, ConnectionDeepLinkRouter.parseConnectionUserId("click://connect/$uuid"))
        assertEquals(uuid, ConnectionDeepLinkRouter.parseConnectionUserId("click://c/$uuid"))
    }

    @Test
    fun unrelatedUrl_isRejected() {
        assertFalse(ConnectionDeepLinkRouter.handleIncomingUrl("https://joinclick.co/hub/abc"))
        assertNull(ConnectionDeepLinkRouter.pendingConnection.value)
    }
}
