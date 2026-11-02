package compose.project.click.click.events.tickets

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/*
 * Ticketing models, parsed from click-web (`app/api/beacons/[beaconId]/tickets/...`, `app/api/orders/[orderId]`,
 * `app/api/me/tickets`, `lib/server/ticketing/offerings.ts`, `ownedTickets.ts`). Mirrors iOS
 * `Click/Core/Beacons/Ticketing.swift`. Web owns ticketing: prices, stock and limits always come from the server.
 */

/** An event's ticketing at a glance, as `GET /api/beacons/{id}` sends it (`beacon.ticketing`). A ticket replaces the RSVP. */
data class EventTicketing(
    /** `draft`, `ready`, `sales_open`, `sales_paused` or `sales_closed`. */
    val status: String,
    val cancelled: Boolean,
    /** The cheapest ticket in minor units, null when nothing is listed. */
    val fromAmount: Int?,
    val currency: String,
    /** Something is on sale right now. */
    val available: Boolean,
    /** Live tickets the viewer holds. */
    val myTicketCount: Int,
)

/** A ticket type as a buyer sees it. */
data class TicketOffering(
    val id: String,
    val name: String,
    val description: String?,
    /** Minor units; 0 is free. */
    val unitAmount: Int,
    val currency: String,
    val availability: TicketAvailability,
    /** Only when 10 or fewer are left. */
    val remaining: Int?,
    /** Most the viewer can pick now (order limit, stock and per-person limit combined). */
    val maxQuantity: Int,
    val salesStartAtMs: Long?,
) {
    val isFree: Boolean get() = unitAmount == 0
}

enum class TicketAvailability(
    val wire: String,
) {
    ON_SALE("on_sale"),
    SOLD_OUT("sold_out"),
    NOT_STARTED("not_started"),
    ENDED("ended"),
    PAUSED("paused"),
    ;

    companion object {
        /** A state this build doesn't know can't be bought, so it reads as paused (iOS). */
        fun fromWire(raw: String?): TicketAvailability = entries.firstOrNull { it.wire == raw } ?: PAUSED
    }
}

enum class OwnedTicketStatus(
    val wire: String,
) {
    VALID("valid"),
    CHECKED_IN("checked_in"),
    REFUNDED("refunded"),
    VOID("void"),
    ;

    companion object {
        fun fromWire(raw: String?): OwnedTicketStatus? = entries.firstOrNull { it.wire == raw }
    }
}

/** A ticket as its owner sees it. The credential is present only while the ticket admits. */
data class OwnedTicket(
    val id: String,
    val beaconId: String,
    val orderId: String,
    val status: OwnedTicketStatus,
    val tierName: String,
    val ticketNumber: String,
    val checkedInAtMs: Long?,
    val credentialUrl: String?,
    val code: String?,
)

/** The event a wallet group belongs to. */
data class TicketEvent(
    val beaconId: String,
    val title: String,
    val startAtMs: Long?,
    val endAtMs: Long?,
    val timeZone: String?,
    val locationName: String?,
    val imageUrl: String?,
    val visualSeed: String,
    val cancelled: Boolean,
)

data class MyTicketsGroup(
    val event: TicketEvent,
    val tickets: List<OwnedTicket>,
)

enum class TicketScope(
    val wire: String,
) {
    UPCOMING("upcoming"),
    PAST("past"),
}

/** What the buyer sees after checkout. Webhooks, not the return from Stripe, decide this. */
enum class TicketOrderOutcome { CONFIRMED, PENDING, CANCELED, FAILED, EXPIRED, REFUNDED }

data class TicketOrder(
    val id: String,
    val beaconId: String,
    val outcome: TicketOrderOutcome,
    val ticketCount: Int,
)

/** Mirrors web `orderOutcome` (and iOS `TicketOrder.outcome`). */
fun ticketOrderOutcome(
    orderState: String,
    fulfillmentState: String,
): TicketOrderOutcome =
    when (orderState) {
        "paid", "partially_refunded", "disputed" ->
            if (fulfillmentState == "fulfilled") TicketOrderOutcome.CONFIRMED else TicketOrderOutcome.PENDING
        "refunded" -> TicketOrderOutcome.REFUNDED
        "canceled" -> TicketOrderOutcome.CANCELED
        "payment_failed" -> TicketOrderOutcome.FAILED
        "expired" -> TicketOrderOutcome.EXPIRED
        else -> TicketOrderOutcome.PENDING
    }

/** What `POST .../tickets/checkout` answered. */
sealed interface CheckoutStart {
    val orderId: String

    /** Free tickets: issued already. */
    data class Fulfilled(
        override val orderId: String,
    ) : CheckoutStart

    /** Paid tickets: Stripe's hosted page to open. */
    data class Checkout(
        override val orderId: String,
        val url: String,
    ) : CheckoutStart
}

// region Parsing

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

private fun JsonObject.instantMs(key: String): Long? =
    str(key)?.let { raw -> runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull() }

private fun JsonObject.rows(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()

/** `beacon.ticketing`; null when the event doesn't sell tickets (or ticketing is off on the server). */
fun parseEventTicketing(row: JsonElement?): EventTicketing? {
    val o = row.obj() ?: return null
    val status = o.str("status") ?: return null
    return EventTicketing(
        status = status,
        cancelled = o.bool("cancelled") ?: false,
        fromAmount = o.int("from_amount"),
        currency = o.str("currency") ?: "usd",
        available = o.bool("available") ?: false,
        myTicketCount = o.int("my_ticket_count") ?: 0,
    )
}

/** `GET /api/beacons/{id}` → `{beacon: {..., ticketing}}`. */
fun parseBeaconTicketing(root: JsonElement?): EventTicketing? =
    parseEventTicketing(
        root
            .obj()
            ?.get("beacon")
            .obj()
            ?.get("ticketing"),
    )

fun parseTicketOffering(o: JsonObject): TicketOffering? {
    val id = o.str("id") ?: return null
    val name = o.str("name") ?: return null
    return TicketOffering(
        id = id,
        name = name,
        description = o.str("description"),
        unitAmount = o.int("unit_amount") ?: 0,
        currency = o.str("currency") ?: "usd",
        availability = TicketAvailability.fromWire(o.str("availability")),
        remaining = o.int("remaining"),
        maxQuantity = o.int("max_quantity") ?: 0,
        salesStartAtMs = o.instantMs("sales_start_at"),
    )
}

/** `GET .../tickets/tiers` → `{tiers: [...]}`. */
fun parseTicketOfferings(root: JsonElement?): List<TicketOffering> =
    root
        .obj()
        ?.rows("tiers")
        .orEmpty()
        .mapNotNull(::parseTicketOffering)

fun parseOwnedTicket(o: JsonObject): OwnedTicket? {
    val id = o.str("id") ?: return null
    val beaconId = o.str("beacon_id") ?: return null
    val status = OwnedTicketStatus.fromWire(o.str("status")) ?: return null
    return OwnedTicket(
        id = id,
        beaconId = beaconId,
        orderId = o.str("order_id").orEmpty(),
        status = status,
        tierName = o.str("tier_name") ?: "Ticket",
        ticketNumber = o.str("ticket_number").orEmpty(),
        checkedInAtMs = o.instantMs("checked_in_at"),
        credentialUrl = o.str("credential_url")?.takeIf { it.isNotBlank() },
        code = o.str("code"),
    )
}

/** `GET /api/beacons/{id}/tickets` → `{tickets: [...]}`. */
fun parseOwnedTickets(root: JsonElement?): List<OwnedTicket> =
    root
        .obj()
        ?.rows("tickets")
        .orEmpty()
        .mapNotNull(::parseOwnedTicket)

fun parseTicketEvent(row: JsonElement?): TicketEvent? {
    val o = row.obj() ?: return null
    val id = o.str("beacon_id") ?: return null
    return TicketEvent(
        beaconId = id,
        title = o.str("title") ?: "Event",
        startAtMs = o.instantMs("start_at"),
        endAtMs = o.instantMs("end_at"),
        timeZone = o.str("timezone"),
        locationName = o.str("location_name"),
        imageUrl = o.str("image_url"),
        visualSeed = o.str("visual_seed") ?: id,
        cancelled = o.bool("cancelled") ?: false,
    )
}

/** `GET /api/me/tickets` → `{groups: [{event, tickets}]}`. */
fun parseMyTicketsGroups(root: JsonElement?): List<MyTicketsGroup> =
    root.obj()?.rows("groups").orEmpty().mapNotNull { row ->
        parseTicketEvent(row["event"])?.let { event ->
            MyTicketsGroup(event = event, tickets = row.rows("tickets").mapNotNull(::parseOwnedTicket))
        }
    }

/** `GET /api/orders/{id}` → `{order: {...}}`. */
fun parseTicketOrder(root: JsonElement?): TicketOrder? {
    val o = root.obj()?.get("order").obj() ?: return null
    val id = o.str("id") ?: return null
    return TicketOrder(
        id = id,
        beaconId = o.str("beacon_id").orEmpty(),
        outcome = ticketOrderOutcome(o.str("order_state").orEmpty(), o.str("fulfillment_state").orEmpty()),
        ticketCount = o.int("ticket_count") ?: 0,
    )
}

/** `POST .../tickets/checkout` → `{order_id, checkout_url}` (paid) or `{order_id, status: "fulfilled"}` (free). */
fun parseCheckoutStart(root: JsonElement?): CheckoutStart? {
    val o = root.obj() ?: return null
    val orderId = o.str("order_id")?.takeIf { it.isNotBlank() } ?: return null
    val url = o.str("checkout_url")?.trim()?.takeIf { it.isNotEmpty() }
    return if (url != null) CheckoutStart.Checkout(orderId, url) else CheckoutStart.Fulfilled(orderId)
}

// endregion

// region Errors

/** A refused or failed ticketing request, worded as web words it (iOS `TicketingError`). */
class TicketingException(
    val code: String?,
    val remaining: Int? = null,
    /** The server's own sentence, shown when the code has no local copy. */
    val serverMessage: String? = null,
    val signedOut: Boolean = false,
) : Exception(code ?: serverMessage ?: "ticketing") {
    /** Ticketing is off on this server (dark: 404, or `ticketing_disabled`): nothing to show, not a failure. */
    val isUnavailable: Boolean get() = code == "not_found" || code == "ticketing_disabled"

    /** The server refused this specific request (sold out, limits, price change…), as opposed to a network or unknown failure. */
    val isRefusal: Boolean get() = code != null && code != "network"

    val userMessage: String
        get() {
            if (code == "insufficient_inventory") {
                val left = remaining
                return if (left == null || left <= 0) "Sold out." else "Only $left left. We updated your selection."
            }
            code?.let { MESSAGES[it] }?.let { return it }
            if (signedOut) return "Sign in to continue."
            return serverMessage?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
        }

    override val message: String get() = userMessage

    companion object {
        private val MESSAGES =
            mapOf(
                "network" to "Check your connection and try again.",
                "price_changed" to "The price changed. Check the new total.",
                "sales_ended" to "Sales for this event have closed.",
                "sales_not_open" to "Sales for this event have closed.",
                "sales_not_started" to "Sales haven’t started yet.",
                "over_user_limit" to "You’ve reached the ticket limit for this event.",
                "over_order_limit" to "That’s more tickets than one order allows.",
                "tier_inactive" to "That ticket isn’t available anymore.",
                "tier_not_found" to "That ticket isn’t available anymore.",
                "event_cancelled" to "This event was cancelled.",
                "organizer_not_ready" to "Tickets aren’t on sale yet.",
                "mixed_order" to "Get free and paid tickets in separate orders.",
                "ticketing_disabled" to "Tickets aren’t available right now.",
            )

        /**
         * Maps a non-2xx response to an error (iOS `TicketingError.from`): 401 signed out, 403 is the only refusal a buyer
         * gets (ticketing off), 404 not found, 400/409/422 carry `{error, code, remaining}`.
         */
        fun fromResponse(
            status: Int,
            body: JsonElement?,
        ): TicketingException {
            val o = body.obj()
            return when (status) {
                401 -> TicketingException(code = null, signedOut = true)
                403 -> TicketingException(code = "ticketing_disabled")
                404 -> TicketingException(code = "not_found")
                400, 409, 410, 422 ->
                    TicketingException(code = o?.str("code"), remaining = o?.int("remaining"), serverMessage = o?.str("error"))
                408 -> TicketingException(code = "network")
                else -> TicketingException(code = null)
            }
        }
    }
}

// endregion

// region Copy

/** Money for integer minor units, as web `money.ts` shows it (en-US). */
object TicketPrice {
    /** "$15.00", "$12.50", "Free". */
    fun money(
        cents: Int,
        currency: String,
    ): String = if (cents == 0) "Free" else format(cents, currency, wholeOnly = false)

    /** Compact, for "from" labels: "$12", "$12.50", "Free". */
    fun from(
        cents: Int,
        currency: String,
    ): String = if (cents == 0) "Free" else format(cents, currency, wholeOnly = cents % 100 == 0)

    /** en-US currency symbols (what `Intl.NumberFormat('en-US')` prints); others show the ISO code. */
    private val SYMBOLS =
        mapOf(
            "USD" to "$",
            "EUR" to "€",
            "GBP" to "£",
            "CAD" to "CA$",
            "AUD" to "A$",
            "NZD" to "NZ$",
            "MXN" to "MX$",
            "JPY" to "¥",
            "INR" to "₹",
        )

    private fun format(
        cents: Int,
        currency: String,
        wholeOnly: Boolean,
    ): String {
        val code = currency.uppercase()
        val negative = cents < 0
        val abs = if (negative) -cents.toLong() else cents.toLong()
        val whole = groupThousands((abs / 100).toString())
        val number = if (wholeOnly) whole else "$whole.${(abs % 100).toString().padStart(2, '0')}"
        val symbol = SYMBOLS[code]
        val body = if (symbol != null) "$symbol$number" else "$code $number"
        return if (negative) "-$body" else body
    }

    private fun groupThousands(digits: String): String =
        digits
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
}

/** The event's primary action when it sells tickets (iOS `TicketButton.state`). */
data class TicketButtonState(
    val title: String,
    val enabled: Boolean,
)

fun ticketButtonState(ticketing: EventTicketing): TicketButtonState {
    if (ticketing.cancelled) return TicketButtonState("Event cancelled", enabled = false)
    if (ticketing.available) {
        val from = ticketing.fromAmount ?: return TicketButtonState("Get tickets", enabled = true)
        return if (from == 0) {
            TicketButtonState("Claim free tickets", enabled = true)
        } else {
            TicketButtonState("Get tickets · from ${TicketPrice.from(from, ticketing.currency)}", enabled = true)
        }
    }
    return when (ticketing.status) {
        "sales_open" -> TicketButtonState("Sold out", enabled = false)
        "sales_closed" -> TicketButtonState("Sales ended", enabled = false)
        "sales_paused" -> TicketButtonState("Sales paused", enabled = false)
        else -> TicketButtonState("Not on sale yet", enabled = false)
    }
}

/** One offering's second line in the picker (iOS `TicketPickerSheet.detail`). [day] formats a date like "Nov 3". */
fun ticketOfferingDetail(
    offering: TicketOffering,
    day: (Long) -> String,
): String {
    val price = TicketPrice.money(offering.unitAmount, offering.currency)
    return when (offering.availability) {
        TicketAvailability.ON_SALE -> offering.remaining?.let { "$price · $it left" } ?: price
        TicketAvailability.SOLD_OUT -> "$price · Sold out"
        TicketAvailability.ENDED -> "$price · Sales ended"
        TicketAvailability.PAUSED -> "$price · Sales paused"
        TicketAvailability.NOT_STARTED ->
            offering.salesStartAtMs?.let { "$price · On sale ${day(it)}" } ?: "$price · Not on sale yet"
    }
}

/** What a ticket shows at the door: its QR, or why it no longer admits (iOS `TicketPassContent.ticketDisplay`). */
enum class TicketDisplay { QR, CHECKED_IN, REFUNDED, CANCELLED, VOID }

fun ticketDisplay(
    ticket: OwnedTicket,
    eventCancelled: Boolean,
): TicketDisplay {
    if (eventCancelled) return TicketDisplay.CANCELLED
    return when (ticket.status) {
        OwnedTicketStatus.VALID -> if (ticket.credentialUrl == null) TicketDisplay.VOID else TicketDisplay.QR
        OwnedTicketStatus.CHECKED_IN -> TicketDisplay.CHECKED_IN
        OwnedTicketStatus.REFUNDED -> TicketDisplay.REFUNDED
        OwnedTicketStatus.VOID -> TicketDisplay.VOID
    }
}

/** Me → Tickets order: upcoming soonest first, past most recent first; undated events last (iOS `TicketsView.sections`). */
fun sortTicketGroups(
    groups: List<MyTicketsGroup>,
    scope: TicketScope,
): List<MyTicketsGroup> {
    val dated = groups.filter { it.event.startAtMs != null }
    val undated = groups.filter { it.event.startAtMs == null }
    val sorted =
        if (scope == TicketScope.UPCOMING) {
            dated.sortedBy { it.event.startAtMs }
        } else {
            dated.sortedByDescending { it.event.startAtMs }
        }
    return sorted + undated
}

/** "2 tickets · General", or "3 tickets" when the tiers differ (iOS `TicketGroupRow.summary`). */
fun ticketGroupSummary(group: MyTicketsGroup): String {
    val count = group.tickets.size
    val noun = if (count == 1) "ticket" else "tickets"
    val tiers = group.tickets.map { it.tierName }.toSet()
    return if (tiers.size == 1) "$count $noun · ${tiers.first()}" else "$count $noun"
}

// endregion
