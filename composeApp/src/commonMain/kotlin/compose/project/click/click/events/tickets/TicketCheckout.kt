package compose.project.click.click.events.tickets

import compose.project.click.click.openInAppBrowser
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Stripe's hosted checkout opened in a Custom Tab, and the wait for Click to confirm the order. Mirrors iOS
 * `Click/Features/Events/Tickets/TicketCheckout.swift`. Stripe sends the buyer back to
 * `https://joinclick.co/e/{id}/tickets/return?order={orderId}[&canceled=1]` (web `eventTicketsReturnPath`); the
 * `/e/` App Link brings that into the app. The return only says the buyer finished; webhooks decide the order.
 */

sealed interface CheckoutReturn {
    data class Completed(
        val orderId: String,
    ) : CheckoutReturn

    data object Canceled : CheckoutReturn
}

/** A return from Stripe for one event. */
data class TicketCheckoutCallback(
    val beaconId: String,
    val result: CheckoutReturn,
)

private val RETURN_HOSTS = setOf("joinclick.co", "www.joinclick.co")
private val RETURN_PATH = Regex("""^/e/([^/]+)/tickets/return/?$""")

/** Must match web `eventTicketsReturnPath`. */
fun ticketCheckoutReturnPath(beaconId: String): String = "/e/$beaconId/tickets/return"

/**
 * Reads Stripe's return link. Null when [url] isn't a ticket return link at all; [CheckoutReturn.Canceled] when the
 * buyer backed out or the order id is missing (iOS `TicketCheckout.parse`).
 */
fun parseTicketCheckoutCallback(
    url: String,
    extraHosts: Set<String> = emptySet(),
): TicketCheckoutCallback? {
    val parsed = runCatching { Url(url.trim()) }.getOrNull() ?: return null
    if (parsed.protocol.name != "https") return null
    val host = parsed.host.lowercase()
    if (host !in RETURN_HOSTS && host !in extraHosts) return null
    val beaconId =
        RETURN_PATH
            .find(parsed.encodedPath)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() } ?: return null
    val canceled = parsed.parameters["canceled"] == "1"
    val order = parsed.parameters["order"]?.trim().orEmpty()
    val result = if (canceled || order.isEmpty()) CheckoutReturn.Canceled else CheckoutReturn.Completed(order)
    return TicketCheckoutCallback(beaconId, result)
}

/**
 * Only an https page with a host is opened; anything else from the server is treated as a failed checkout. The host
 * itself isn't pinned: which processor page to open is the server's call.
 */
fun isOpenableCheckoutUrl(url: String): Boolean {
    val parsed = runCatching { Url(url.trim()) }.getOrNull() ?: return false
    return parsed.protocol.name == "https" && parsed.host.isNotBlank() && url.trim().startsWith("https://", ignoreCase = true)
}

/** Opens Stripe's page. Behind an interface so tests (and other platforms) don't need a browser. */
fun interface CheckoutLauncher {
    /** True when a browser took the URL. */
    fun open(url: String): Boolean

    companion object {
        /** A Custom Tab on Android (see `openInAppBrowser`). */
        val Default = CheckoutLauncher { openInAppBrowser(it) }
    }
}

/** Holds the latest return from Stripe until the picker that started checkout consumes it. */
object TicketCheckoutReturns {
    private val _pending = MutableStateFlow<TicketCheckoutCallback?>(null)
    val pending: StateFlow<TicketCheckoutCallback?> = _pending.asStateFlow()

    /** Called for incoming App Links. True when [url] was a ticket return (so it isn't routed as a plain event link). */
    fun handleIncomingUrl(url: String): Boolean {
        val callback = parseTicketCheckoutCallback(url) ?: return false
        _pending.value = callback
        return true
    }

    /** Takes the return for [beaconId], if one arrived. */
    fun consume(beaconId: String): CheckoutReturn? {
        val current = _pending.value ?: return null
        if (current.beaconId != beaconId) return null
        _pending.value = null
        return current.result
    }

    fun clear() {
        _pending.value = null
    }
}

/** What to do once the buyer is back from Stripe's page. */
sealed interface AfterCheckout {
    /** Wait for Click to confirm [orderId]. */
    data class Confirm(
        val orderId: String,
    ) : AfterCheckout

    /** Back to the picker, selection kept; tickets were released. */
    data object BackToPicker : AfterCheckout
}

/**
 * The buyer is back. A completed return goes to the order poll. A cancel, or closing the tab with no return
 * (common when App Links aren't verified and Stripe's return page loaded in the tab), releases the held tickets; if
 * the server refuses the release the order is already paid or settling, so we wait for it instead of dropping it.
 */
suspend fun resolveCheckoutReturn(
    orderId: String,
    returned: CheckoutReturn?,
    client: TicketingClient,
): AfterCheckout {
    if (returned is CheckoutReturn.Completed) return AfterCheckout.Confirm(returned.orderId)
    return try {
        client.releaseOrder(orderId)
        AfterCheckout.BackToPicker
    } catch (e: CancellationException) {
        throw e
    } catch (e: TicketingException) {
        if (e.isRefusal && !e.isUnavailable) AfterCheckout.Confirm(orderId) else AfterCheckout.BackToPicker
    } catch (_: Exception) {
        AfterCheckout.BackToPicker
    }
}

/** The order confirmation's states (iOS `OrderConfirmationModel.State`). */
sealed interface OrderConfirmationState {
    data object Confirming : OrderConfirmationState

    data class Confirmed(
        val ticketCount: Int,
    ) : OrderConfirmationState

    data object Canceled : OrderConfirmationState

    data object Failed : OrderConfirmationState

    data object Expired : OrderConfirmationState

    data object Refunded : OrderConfirmationState

    data object StillConfirming : OrderConfirmationState
}

internal fun confirmationStateFor(order: TicketOrder): OrderConfirmationState? =
    when (order.outcome) {
        TicketOrderOutcome.CONFIRMED -> OrderConfirmationState.Confirmed(order.ticketCount)
        TicketOrderOutcome.PENDING -> null
        TicketOrderOutcome.CANCELED -> OrderConfirmationState.Canceled
        TicketOrderOutcome.FAILED -> OrderConfirmationState.Failed
        TicketOrderOutcome.EXPIRED -> OrderConfirmationState.Expired
        TicketOrderOutcome.REFUNDED -> OrderConfirmationState.Refunded
    }

internal const val ORDER_POLL_FAST_MS = 1_000L
internal const val ORDER_POLL_SLOW_MS = 3_000L
internal const val ORDER_POLL_FAST_WINDOW_MS = 10_000L
internal const val ORDER_POLL_LIMIT_MS = 60_000L

/**
 * Waits for Click to confirm an order: every second for 10 s, then every 3 s, giving up at 60 s with
 * [OrderConfirmationState.StillConfirming] (the payment may still clear; the tickets then show on the event page).
 * A dropped poll is retried; a 404 / ticketing-off answer stops waiting. Mirrors iOS `OrderConfirmationModel.run`.
 */
suspend fun pollOrderConfirmation(
    orderId: String,
    fetch: suspend (String) -> TicketOrder,
    sleep: suspend (Long) -> Unit,
    nowMs: () -> Long,
): OrderConfirmationState {
    val start = nowMs()
    while (true) {
        try {
            confirmationStateFor(fetch(orderId))?.let { return it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TicketingException) {
            if (e.isUnavailable) return OrderConfirmationState.StillConfirming
        } catch (_: Exception) {
            // A dropped poll: try again.
        }
        val waited = nowMs() - start
        val interval = if (waited < ORDER_POLL_FAST_WINDOW_MS) ORDER_POLL_FAST_MS else ORDER_POLL_SLOW_MS
        if (waited + interval > ORDER_POLL_LIMIT_MS) return OrderConfirmationState.StillConfirming
        sleep(interval)
    }
}

/** Title and message per state (copy matches iOS `OrderConfirmationView` / web's checkout return page). */
data class OrderConfirmationCopy(
    val title: String,
    val message: String,
    /** The button: null while confirming. */
    val action: OrderConfirmationAction?,
)

sealed interface OrderConfirmationAction {
    val label: String

    data class Done(
        override val label: String,
    ) : OrderConfirmationAction

    data object Retry : OrderConfirmationAction {
        override val label: String = "Try again"
    }
}

fun orderConfirmationCopy(state: OrderConfirmationState): OrderConfirmationCopy =
    when (state) {
        OrderConfirmationState.Confirming ->
            OrderConfirmationCopy("Confirming your order…", "This usually takes a few seconds.", null)
        is OrderConfirmationState.Confirmed ->
            OrderConfirmationCopy(
                "You’re in",
                if (state.ticketCount == 1) "Your ticket is ready." else "Your ${state.ticketCount} tickets are ready.",
                OrderConfirmationAction.Done(if (state.ticketCount == 1) "See your ticket" else "See your tickets"),
            )
        OrderConfirmationState.Canceled ->
            OrderConfirmationCopy("No charge was made", "You left checkout before paying.", OrderConfirmationAction.Retry)
        OrderConfirmationState.Failed ->
            OrderConfirmationCopy(
                "Payment didn’t go through",
                "No charge was made. You can try again with another card.",
                OrderConfirmationAction.Retry,
            )
        OrderConfirmationState.Expired ->
            OrderConfirmationCopy(
                "Checkout timed out",
                "Your tickets were released and no charge was made.",
                OrderConfirmationAction.Retry,
            )
        OrderConfirmationState.Refunded ->
            OrderConfirmationCopy(
                "Your payment was refunded",
                "We couldn’t issue these tickets, so the charge was reversed in full.",
                OrderConfirmationAction.Done("Done"),
            )
        OrderConfirmationState.StillConfirming ->
            OrderConfirmationCopy(
                "Still confirming…",
                "Your payment is still processing. Your tickets will show up on the event page as soon as it clears.",
                OrderConfirmationAction.Done("Done"),
            )
    }
