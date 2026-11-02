package compose.project.click.click.events.tickets

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicketCheckoutTest {
    @AfterTest
    fun reset() = TicketCheckoutReturns.clear()

    // region Return URL

    @Test
    fun completedReturn() {
        assertEquals(
            TicketCheckoutCallback("b1", CheckoutReturn.Completed("o1")),
            parseTicketCheckoutCallback("https://joinclick.co/e/b1/tickets/return?order=o1"),
        )
        assertEquals(
            TicketCheckoutCallback("b1", CheckoutReturn.Completed("o1")),
            parseTicketCheckoutCallback("https://www.joinclick.co/e/b1/tickets/return/?order=o1"),
        )
    }

    @Test
    fun canceledOrMissingOrderReadsAsCanceled() {
        assertEquals(
            CheckoutReturn.Canceled,
            parseTicketCheckoutCallback("https://joinclick.co/e/b1/tickets/return?order=o1&canceled=1")?.result,
        )
        assertEquals(CheckoutReturn.Canceled, parseTicketCheckoutCallback("https://joinclick.co/e/b1/tickets/return")?.result)
        assertEquals(CheckoutReturn.Canceled, parseTicketCheckoutCallback("https://joinclick.co/e/b1/tickets/return?order=")?.result)
    }

    @Test
    fun otherLinksAreNotReturns() {
        assertNull(parseTicketCheckoutCallback("https://joinclick.co/e/b1"))
        assertNull(parseTicketCheckoutCallback("https://joinclick.co/e/b1/tickets"))
        assertNull(parseTicketCheckoutCallback("http://joinclick.co/e/b1/tickets/return?order=o1"))
        assertNull(parseTicketCheckoutCallback("https://evil.example/e/b1/tickets/return?order=o1"))
        assertNull(parseTicketCheckoutCallback("click://e/b1"))
        assertNull(parseTicketCheckoutCallback("not a url"))
        assertEquals(
            "b1",
            parseTicketCheckoutCallback(
                "https://staging.example/e/b1/tickets/return?order=o",
                extraHosts = setOf("staging.example"),
            )?.beaconId,
        )
    }

    @Test
    fun returnPathMatchesWeb() {
        assertEquals("/e/abc/tickets/return", ticketCheckoutReturnPath("abc"))
    }

    @Test
    fun onlyHttpsCheckoutUrlsOpen() {
        assertTrue(isOpenableCheckoutUrl("https://checkout.stripe.com/c/pay/cs_test_1#frag"))
        assertFalse(isOpenableCheckoutUrl("http://checkout.stripe.com/c/pay"))
        assertFalse(isOpenableCheckoutUrl("javascript:alert(1)"))
        assertFalse(isOpenableCheckoutUrl("intent://x#Intent;end"))
        assertFalse(isOpenableCheckoutUrl(""))
    }

    @Test
    fun returnRouterHoldsPerEvent() {
        assertFalse(TicketCheckoutReturns.handleIncomingUrl("https://joinclick.co/e/b1"))
        assertTrue(TicketCheckoutReturns.handleIncomingUrl("https://joinclick.co/e/b1/tickets/return?order=o1"))
        assertNull(TicketCheckoutReturns.consume("other"))
        assertEquals(CheckoutReturn.Completed("o1"), TicketCheckoutReturns.consume("b1"))
        assertNull(TicketCheckoutReturns.consume("b1"))
    }

    @Test
    fun checkoutBodyHasOnlyTiersAndQuantities() {
        val body = checkoutRequestBody(listOf(TicketLine("t1", 2), TicketLine("t2", 0)))
        assertEquals(Json.parseToJsonElement("""{"items":[{"ticket_tier_id":"t1","quantity":2}]}"""), body)
    }

    // endregion

    // region After checkout

    @Test
    fun completedReturnGoesToPoll() =
        runTest {
            val client = FakeTicketingClient()
            assertEquals(AfterCheckout.Confirm("o9"), resolveCheckoutReturn("o1", CheckoutReturn.Completed("o9"), client))
            assertTrue(client.released.isEmpty())
        }

    @Test
    fun closedTabReleasesHeldTickets() =
        runTest {
            val client = FakeTicketingClient()
            assertEquals(AfterCheckout.BackToPicker, resolveCheckoutReturn("o1", null, client))
            assertEquals(AfterCheckout.BackToPicker, resolveCheckoutReturn("o2", CheckoutReturn.Canceled, client))
            assertEquals(listOf("o1", "o2"), client.released)
        }

    @Test
    fun refusedReleaseMeansOrderIsSettlingSoWeWait() =
        runTest {
            val client = FakeTicketingClient(releaseResult = { throw TicketingException("order_not_cancellable") })
            assertEquals(AfterCheckout.Confirm("o1"), resolveCheckoutReturn("o1", null, client))
        }

    @Test
    fun networkFailureOnReleaseKeepsPicker() =
        runTest {
            val client = FakeTicketingClient(releaseResult = { throw TicketingException("network") })
            assertEquals(AfterCheckout.BackToPicker, resolveCheckoutReturn("o1", null, client))
            client.releaseResult = { throw TicketingException("not_found") }
            assertEquals(AfterCheckout.BackToPicker, resolveCheckoutReturn("o1", null, client))
        }

    // endregion

    // region Poll

    private class FakeClock {
        var now = 0L
        val sleeps = mutableListOf<Long>()

        suspend fun sleep(ms: Long) {
            sleeps += ms
            now += ms
        }
    }

    @Test
    fun confirmsOnFirstPoll() =
        runTest {
            val clock = FakeClock()
            val result = pollOrderConfirmation("o", { order(TicketOrderOutcome.CONFIRMED, 3) }, clock::sleep) { clock.now }
            assertEquals(OrderConfirmationState.Confirmed(3), result)
            assertTrue(clock.sleeps.isEmpty())
        }

    @Test
    fun pendingThenPaid() =
        runTest {
            val clock = FakeClock()
            var calls = 0
            val result =
                pollOrderConfirmation(
                    "o",
                    { if (++calls < 4) order(TicketOrderOutcome.PENDING) else order(TicketOrderOutcome.CONFIRMED, 1) },
                    clock::sleep,
                ) { clock.now }
            assertEquals(OrderConfirmationState.Confirmed(1), result)
            assertEquals(listOf(1_000L, 1_000L, 1_000L), clock.sleeps)
        }

    @Test
    fun terminalOutcomes() =
        runTest {
            val clock = FakeClock()
            val cases =
                mapOf(
                    TicketOrderOutcome.CANCELED to OrderConfirmationState.Canceled,
                    TicketOrderOutcome.FAILED to OrderConfirmationState.Failed,
                    TicketOrderOutcome.EXPIRED to OrderConfirmationState.Expired,
                    TicketOrderOutcome.REFUNDED to OrderConfirmationState.Refunded,
                )
            cases.forEach { (outcome, state) ->
                assertEquals(state, pollOrderConfirmation("o", { order(outcome) }, clock::sleep) { clock.now })
            }
        }

    @Test
    fun backsOffThenGivesUpAtSixtySeconds() =
        runTest {
            val clock = FakeClock()
            val result = pollOrderConfirmation("o", { order(TicketOrderOutcome.PENDING) }, clock::sleep) { clock.now }
            assertEquals(OrderConfirmationState.StillConfirming, result)
            // Every second for the first 10 s, then every 3 s, never past 60 s.
            assertEquals(List(10) { 1_000L }, clock.sleeps.take(10))
            assertTrue(clock.sleeps.drop(10).all { it == 3_000L })
            assertTrue(clock.now <= 60_000L)
            assertEquals(58_000L, clock.now)
        }

    @Test
    fun droppedPollsAreRetried() =
        runTest {
            val clock = FakeClock()
            var calls = 0
            val result =
                pollOrderConfirmation(
                    "o",
                    {
                        calls++
                        if (calls == 1) throw TicketingException("network")
                        if (calls == 2) throw IllegalStateException("boom")
                        order(TicketOrderOutcome.CONFIRMED)
                    },
                    clock::sleep,
                ) { clock.now }
            assertEquals(OrderConfirmationState.Confirmed(2), result)
            assertEquals(3, calls)
        }

    @Test
    fun notFoundStopsWaiting() =
        runTest {
            val clock = FakeClock()
            var calls = 0
            val result =
                pollOrderConfirmation("o", {
                    calls++
                    throw TicketingException("not_found")
                }, clock::sleep) { clock.now }
            assertEquals(OrderConfirmationState.StillConfirming, result)
            assertEquals(1, calls)
        }

    @Test
    fun confirmationCopy() {
        assertNull(orderConfirmationCopy(OrderConfirmationState.Confirming).action)
        val one = orderConfirmationCopy(OrderConfirmationState.Confirmed(1))
        assertEquals("You’re in", one.title)
        assertEquals("Your ticket is ready.", one.message)
        assertEquals("See your ticket", one.action?.label)
        assertEquals("Your 3 tickets are ready.", orderConfirmationCopy(OrderConfirmationState.Confirmed(3)).message)
        assertEquals(OrderConfirmationAction.Retry, orderConfirmationCopy(OrderConfirmationState.Failed).action)
        assertEquals(OrderConfirmationAction.Retry, orderConfirmationCopy(OrderConfirmationState.Expired).action)
        assertEquals(OrderConfirmationAction.Done("Done"), orderConfirmationCopy(OrderConfirmationState.StillConfirming).action)
        assertEquals(OrderConfirmationAction.Done("Done"), orderConfirmationCopy(OrderConfirmationState.Refunded).action)
    }

    // endregion
}
