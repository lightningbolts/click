package compose.project.click.click.events.tickets

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicketingParsingTest {
    private fun json(raw: String) = Json.parseToJsonElement(raw)

    @Test
    fun parsesBeaconTicketing() {
        val t =
            parseBeaconTicketing(
                json(
                    """{"beacon":{"id":"b","ticketing":{"status":"sales_open","cancelled":false,"from_amount":1200,
                    "currency":"eur","available":true,"my_ticket_count":2}}}""",
                ),
            )
        assertEquals(EventTicketing("sales_open", false, 1200, "eur", true, 2), t)
    }

    @Test
    fun nullTicketingMeansRsvpEvent() {
        assertNull(parseBeaconTicketing(json("""{"beacon":{"id":"b","ticketing":null}}""")))
        assertNull(parseBeaconTicketing(json("""{"beacon":{"id":"b"}}""")))
        assertNull(parseEventTicketing(json("""{"cancelled":false}""")))
    }

    @Test
    fun ticketingDefaults() {
        val t = parseEventTicketing(json("""{"status":"draft","from_amount":null}"""))!!
        assertEquals("usd", t.currency)
        assertNull(t.fromAmount)
        assertFalse(t.available)
        assertEquals(0, t.myTicketCount)
    }

    @Test
    fun parsesOfferings() {
        val list =
            parseTicketOfferings(
                json(
                    """{"tiers":[
                    {"id":"t1","name":"General","description":"Entry","unit_amount":1500,"currency":"usd",
                     "availability":"on_sale","remaining":3,"max_quantity":4,"sales_start_at":null},
                    {"id":"t2","name":"Later","unit_amount":0,"availability":"not_started","max_quantity":0,
                     "sales_start_at":"2026-11-10T18:00:00.000Z"},
                    {"id":"t3","name":"Mystery","availability":"members_only"},
                    {"name":"no id"}
                    ]}""",
                ),
            )
        assertEquals(listOf("t1", "t2", "t3"), list.map { it.id })
        assertEquals(TicketAvailability.ON_SALE, list[0].availability)
        assertEquals(3, list[0].remaining)
        assertEquals(4, list[0].maxQuantity)
        assertTrue(list[1].isFree)
        assertEquals(1_794_333_600_000L, list[1].salesStartAtMs)
        // A state this build doesn't know can't be bought.
        assertEquals(TicketAvailability.PAUSED, list[2].availability)
        assertEquals(0, list[2].maxQuantity)
    }

    @Test
    fun parsesOwnedTicketsAndDropsUnknownStatus() {
        val list =
            parseOwnedTickets(
                json(
                    """{"tickets":[
                    {"id":"k1","beacon_id":"b","order_id":"o1","status":"valid","tier_name":"VIP","ticket_number":"A-1",
                     "checked_in_at":null,"credential_url":"https://joinclick.co/p/x","code":"ABCD"},
                    {"id":"k2","beacon_id":"b","status":"checked_in","checked_in_at":"2026-11-01T20:00:00Z","credential_url":null},
                    {"id":"k3","beacon_id":"b","status":"transferred"}
                    ]}""",
                ),
            )
        assertEquals(2, list.size)
        assertEquals("VIP", list[0].tierName)
        assertEquals("https://joinclick.co/p/x", list[0].credentialUrl)
        assertEquals("Ticket", list[1].tierName)
        assertEquals(OwnedTicketStatus.CHECKED_IN, list[1].status)
        assertEquals(1_793_563_200_000L, list[1].checkedInAtMs)
        assertNull(list[1].credentialUrl)
    }

    @Test
    fun parsesWalletGroups() {
        val groups =
            parseMyTicketsGroups(
                json(
                    """{"groups":[
                    {"event":{"beacon_id":"b1","title":"Show","start_at":"2026-11-10T02:00:00Z","timezone":"America/Los_Angeles",
                      "location_name":"Hall","cancelled":true},
                     "tickets":[{"id":"k1","beacon_id":"b1","status":"valid"}]},
                    {"event":{"title":"no id"},"tickets":[]}
                    ]}""",
                ),
            )
        assertEquals(1, groups.size)
        val e = groups[0].event
        assertEquals("Show", e.title)
        assertEquals("b1", e.visualSeed)
        assertTrue(e.cancelled)
        assertEquals("America/Los_Angeles", e.timeZone)
        assertEquals(1, groups[0].tickets.size)
    }

    @Test
    fun orderOutcomeMirrorsWeb() {
        assertEquals(TicketOrderOutcome.CONFIRMED, ticketOrderOutcome("paid", "fulfilled"))
        assertEquals(TicketOrderOutcome.PENDING, ticketOrderOutcome("paid", "pending"))
        assertEquals(TicketOrderOutcome.CONFIRMED, ticketOrderOutcome("partially_refunded", "fulfilled"))
        assertEquals(TicketOrderOutcome.CONFIRMED, ticketOrderOutcome("disputed", "fulfilled"))
        assertEquals(TicketOrderOutcome.REFUNDED, ticketOrderOutcome("refunded", "fulfilled"))
        assertEquals(TicketOrderOutcome.CANCELED, ticketOrderOutcome("canceled", ""))
        assertEquals(TicketOrderOutcome.FAILED, ticketOrderOutcome("payment_failed", ""))
        assertEquals(TicketOrderOutcome.EXPIRED, ticketOrderOutcome("expired", ""))
        assertEquals(TicketOrderOutcome.PENDING, ticketOrderOutcome("checkout_created", ""))
    }

    @Test
    fun parsesOrder() {
        val o =
            parseTicketOrder(
                json("""{"order":{"id":"o1","beacon_id":"b","order_state":"paid","fulfillment_state":"fulfilled","ticket_count":3}}"""),
            )
        assertEquals(TicketOrder("o1", "b", TicketOrderOutcome.CONFIRMED, 3), o)
        assertNull(parseTicketOrder(json("""{"order":{}}""")))
    }

    @Test
    fun parsesCheckoutStart() {
        assertEquals(
            CheckoutStart.Checkout("o1", "https://checkout.stripe.com/c/pay/cs_1"),
            parseCheckoutStart(json("""{"order_id":"o1","checkout_url":"https://checkout.stripe.com/c/pay/cs_1"}""")),
        )
        assertEquals(CheckoutStart.Fulfilled("o2"), parseCheckoutStart(json("""{"order_id":"o2","status":"fulfilled"}""")))
        assertNull(parseCheckoutStart(json("""{"checkout_url":"https://x"}""")))
    }

    @Test
    fun errorMappingByStatus() {
        assertTrue(TicketingException.fromResponse(401, null).signedOut)
        assertEquals("Sign in to continue.", TicketingException.fromResponse(401, null).userMessage)
        assertTrue(TicketingException.fromResponse(403, null).isUnavailable)
        assertTrue(TicketingException.fromResponse(404, null).isUnavailable)
        val conflict =
            TicketingException.fromResponse(409, json("""{"error":"Checkout unavailable","code":"insufficient_inventory","remaining":2}"""))
        assertEquals("Only 2 left. We updated your selection.", conflict.userMessage)
        assertTrue(conflict.isRefusal)
        val soldOut = TicketingException.fromResponse(409, json("""{"code":"insufficient_inventory","remaining":0}"""))
        assertEquals("Sold out.", soldOut.userMessage)
        val unknown = TicketingException.fromResponse(400, json("""{"error":"Duplicate ticket tier in items","code":"weird"}"""))
        assertEquals("Duplicate ticket tier in items", unknown.userMessage)
        val server = TicketingException.fromResponse(502, json("""{"error":"Checkout failed"}"""))
        assertFalse(server.isRefusal)
        assertEquals("Something went wrong. Try again.", server.userMessage)
        assertEquals("Check your connection and try again.", TicketingException("network").userMessage)
        assertFalse(TicketingException("network").isRefusal)
        assertEquals("Get free and paid tickets in separate orders.", TicketingException("mixed_order").userMessage)
    }

    @Test
    fun priceFormatting() {
        assertEquals("Free", TicketPrice.money(0, "usd"))
        assertEquals("$15.00", TicketPrice.money(1500, "usd"))
        assertEquals("$12.50", TicketPrice.money(1250, "USD"))
        assertEquals("$1,305.05", TicketPrice.money(130505, "usd"))
        assertEquals("$12", TicketPrice.from(1200, "usd"))
        assertEquals("$12.50", TicketPrice.from(1250, "usd"))
        assertEquals("€9.99", TicketPrice.money(999, "eur"))
        assertEquals("CHF 20.00", TicketPrice.money(2000, "chf"))
    }

    @Test
    fun buttonStates() {
        val base = EventTicketing("sales_open", false, 1200, "usd", true, 0)
        assertEquals(TicketButtonState("Get tickets · from $12", true), ticketButtonState(base))
        assertEquals(TicketButtonState("Claim free tickets", true), ticketButtonState(base.copy(fromAmount = 0)))
        assertEquals(TicketButtonState("Get tickets", true), ticketButtonState(base.copy(fromAmount = null)))
        assertEquals(TicketButtonState("Event cancelled", false), ticketButtonState(base.copy(cancelled = true)))
        assertEquals(TicketButtonState("Sold out", false), ticketButtonState(base.copy(available = false)))
        assertEquals("Sales ended", ticketButtonState(base.copy(available = false, status = "sales_closed")).title)
        assertEquals("Sales paused", ticketButtonState(base.copy(available = false, status = "sales_paused")).title)
        assertEquals("Not on sale yet", ticketButtonState(base.copy(available = false, status = "ready")).title)
    }

    @Test
    fun offeringDetailCopy() {
        val day: (Long) -> String = { "Nov 10" }
        assertEquals("$15.00", ticketOfferingDetail(offering("a"), day))
        assertEquals("$15.00 · 3 left", ticketOfferingDetail(offering("a", remaining = 3), day))
        assertEquals("Free · Sold out", ticketOfferingDetail(offering("a", amount = 0, availability = TicketAvailability.SOLD_OUT), day))
        assertEquals(
            "$15.00 · Not on sale yet",
            ticketOfferingDetail(offering("a", availability = TicketAvailability.NOT_STARTED), day),
        )
        assertEquals(
            "$15.00 · On sale Nov 10",
            ticketOfferingDetail(offering("a", availability = TicketAvailability.NOT_STARTED).copy(salesStartAtMs = 1L), day),
        )
    }

    private fun ticket(
        status: OwnedTicketStatus,
        credential: String? = "https://joinclick.co/p/x",
        tier: String = "GA",
    ) = OwnedTicket("k", "b", "o", status, tier, "1", null, credential, null)

    @Test
    fun ticketDisplayStates() {
        assertEquals(TicketDisplay.QR, ticketDisplay(ticket(OwnedTicketStatus.VALID), false))
        assertEquals(TicketDisplay.VOID, ticketDisplay(ticket(OwnedTicketStatus.VALID, credential = null), false))
        assertEquals(TicketDisplay.CANCELLED, ticketDisplay(ticket(OwnedTicketStatus.VALID), true))
        assertEquals(TicketDisplay.CHECKED_IN, ticketDisplay(ticket(OwnedTicketStatus.CHECKED_IN), false))
        assertEquals(TicketDisplay.REFUNDED, ticketDisplay(ticket(OwnedTicketStatus.REFUNDED), false))
    }

    private fun group(
        id: String,
        start: Long?,
        tickets: List<OwnedTicket> = emptyList(),
    ) = MyTicketsGroup(TicketEvent(id, id, start, null, null, null, null, id, false), tickets)

    @Test
    fun walletOrderingAndSummary() {
        val groups = listOf(group("late", 300), group("none", null), group("early", 100))
        assertEquals(listOf("early", "late", "none"), sortTicketGroups(groups, TicketScope.UPCOMING).map { it.event.beaconId })
        assertEquals(listOf("late", "early", "none"), sortTicketGroups(groups, TicketScope.PAST).map { it.event.beaconId })
        assertEquals("1 ticket · GA", ticketGroupSummary(group("a", 1, listOf(ticket(OwnedTicketStatus.VALID)))))
        assertEquals(
            "2 tickets",
            ticketGroupSummary(group("a", 1, listOf(ticket(OwnedTicketStatus.VALID), ticket(OwnedTicketStatus.VALID, tier = "VIP")))),
        )
    }
}
