package compose.project.click.click.events.tickets

/** Scriptable [TicketingClient] for tests. */
internal class FakeTicketingClient(
    var offeringsResult: () -> List<TicketOffering> = { emptyList() },
    var checkoutResult: (List<TicketLine>) -> CheckoutStart = { CheckoutStart.Fulfilled("order-1") },
    var orderResults: MutableList<() -> TicketOrder> = mutableListOf(),
    var releaseResult: () -> Unit = {},
    var myTicketsResult: (TicketScope) -> List<MyTicketsGroup> = { emptyList() },
) : TicketingClient {
    val released = mutableListOf<String>()
    val submitted = mutableListOf<List<TicketLine>>()
    var offeringsCalls = 0
    var orderCalls = 0

    override suspend fun eventTicketing(beaconId: String): EventTicketing? = null

    override suspend fun offerings(beaconId: String): List<TicketOffering> {
        offeringsCalls++
        return offeringsResult()
    }

    override suspend fun startCheckout(
        beaconId: String,
        items: List<TicketLine>,
    ): CheckoutStart {
        submitted += items
        return checkoutResult(items)
    }

    override suspend fun order(orderId: String): TicketOrder {
        orderCalls++
        val next = if (orderResults.size > 1) orderResults.removeAt(0) else orderResults.first()
        return next()
    }

    override suspend fun releaseOrder(orderId: String) {
        released += orderId
        releaseResult()
    }

    override suspend fun eventTickets(beaconId: String): List<OwnedTicket> = emptyList()

    override suspend fun myTickets(scope: TicketScope): List<MyTicketsGroup> = myTicketsResult(scope)
}

internal fun offering(
    id: String,
    amount: Int = 1500,
    availability: TicketAvailability = TicketAvailability.ON_SALE,
    max: Int = 4,
    remaining: Int? = null,
) = TicketOffering(
    id = id,
    name = "Tier $id",
    description = null,
    unitAmount = amount,
    currency = "usd",
    availability = availability,
    remaining = remaining,
    maxQuantity = max,
    salesStartAtMs = null,
)

internal fun order(
    outcome: TicketOrderOutcome,
    count: Int = 2,
) = TicketOrder(id = "order-1", beaconId = "b", outcome = outcome, ticketCount = count)
