package compose.project.click.click.events.tickets

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicketPickerTest {
    private val paid = offering("paid", amount = 1500, max = 2)
    private val vip = offering("vip", amount = 4000, max = 4)
    private val free = offering("free", amount = 0, max = 3)
    private val soldOut = offering("gone", availability = TicketAvailability.SOLD_OUT, max = 0)

    private fun ready(vararg offerings: TicketOffering) = TicketPickerState(offerings = offerings.toList(), phase = TicketPickerPhase.Ready)

    @Test
    fun incrementCapsAtMaxQuantity() {
        var s = ready(paid)
        repeat(5) { s = s.incremented("paid") }
        assertEquals(2, s.quantity("paid"))
        assertFalse(s.canIncrement("paid"))
    }

    @Test
    fun cannotPickWhatIsNotOnSale() {
        val s = ready(soldOut).incremented("gone")
        assertEquals(0, s.count)
        assertFalse(ready(soldOut).canIncrement("unknown"))
    }

    @Test
    fun pickingFreeClearsPaidAndViceVersa() {
        var s = ready(paid, vip, free).incremented("paid").incremented("vip")
        assertEquals(2, s.count)
        s = s.incremented("free")
        assertEquals(mapOf("free" to 1), s.selection)
        s = s.incremented("vip")
        assertEquals(mapOf("vip" to 1), s.selection)
    }

    @Test
    fun decrementRemovesAtZero() {
        val s = ready(paid).incremented("paid").decremented("paid")
        assertTrue(s.selection.isEmpty())
        assertEquals(s, s.decremented("paid"))
    }

    @Test
    fun noChangesWhileSubmitting() {
        val s = ready(paid).incremented("paid").copy(phase = TicketPickerPhase.Submitting)
        assertEquals(s, s.incremented("paid"))
        assertEquals(s, s.decremented("paid"))
    }

    @Test
    fun totalsAndCta() {
        assertEquals("Get tickets", ready(paid).ctaTitle)
        val two = ready(paid, vip).incremented("paid").incremented("vip")
        assertEquals(5500, two.total)
        assertEquals("Checkout · $55.00", two.ctaTitle)
        assertEquals("Claim free ticket", ready(free).incremented("free").ctaTitle)
        assertEquals("Claim free tickets", ready(free).incremented("free").incremented("free").ctaTitle)
        assertTrue(ready(free).incremented("free").isFree)
        assertTrue(ready(paid, free).mixesKinds)
        assertFalse(ready(paid, vip).mixesKinds)
        assertEquals(listOf(TicketLine("paid", 1), TicketLine("vip", 1)), two.lines)
    }

    @Test
    fun clampKeepsOnlyWhatIsStillOnSale() {
        val clamped =
            clampSelection(
                mapOf("paid" to 5, "gone" to 1, "missing" to 2, "vip" to 1),
                listOf(paid, soldOut, vip.copy(maxQuantity = 0)),
            )
        assertEquals(mapOf("paid" to 2), clamped)
    }

    @Test
    fun loadFailureShowsMessage() =
        runTest {
            val client = FakeTicketingClient(offeringsResult = { throw TicketingException("network") })
            val model = TicketPickerModel("b", client)
            model.load()
            assertEquals(TicketPickerPhase.Failed("Check your connection and try again."), model.state.value.phase)
        }

    @Test
    fun submitFreeOrder() =
        runTest {
            val client = FakeTicketingClient(offeringsResult = { listOf(free) }, checkoutResult = { CheckoutStart.Fulfilled("o1") })
            val model = TicketPickerModel("b", client)
            model.load()
            model.increment("free")
            assertEquals(CheckoutStart.Fulfilled("o1"), model.submit())
            assertEquals(listOf(listOf(TicketLine("free", 1))), client.submitted)
            assertEquals(TicketPickerPhase.Ready, model.state.value.phase)
        }

    @Test
    fun submitWithNothingPickedDoesNothing() =
        runTest {
            val client = FakeTicketingClient(offeringsResult = { listOf(paid) })
            val model = TicketPickerModel("b", client)
            model.load()
            assertNull(model.submit())
            assertTrue(client.submitted.isEmpty())
        }

    @Test
    fun refusalRefreshesClampsAndExplains() =
        runTest {
            var stock = paid
            val client =
                FakeTicketingClient(
                    offeringsResult = { listOf(stock) },
                    checkoutResult = { throw TicketingException("insufficient_inventory", remaining = 1) },
                )
            val model = TicketPickerModel("b", client)
            model.load()
            model.increment("paid")
            model.increment("paid")
            stock = paid.copy(maxQuantity = 1, remaining = 1)
            assertNull(model.submit())
            val s = model.state.value
            assertEquals("Only 1 left. We updated your selection.", s.notice)
            assertEquals(1, s.quantity("paid"))
            assertEquals(TicketPickerPhase.Ready, s.phase)
            assertEquals(2, client.offeringsCalls)
        }

    @Test
    fun networkFailureKeepsSelection() =
        runTest {
            val client = FakeTicketingClient(offeringsResult = { listOf(paid) }, checkoutResult = { throw TicketingException("network") })
            val model = TicketPickerModel("b", client)
            model.load()
            model.increment("paid")
            assertNull(model.submit())
            val s = model.state.value
            assertIs<TicketPickerPhase.Failed>(s.phase)
            assertEquals(1, s.quantity("paid"))
            assertNull(s.notice)
        }

    @Test
    fun paidCheckoutReturnsStripeUrl() =
        runTest {
            val client =
                FakeTicketingClient(
                    offeringsResult = { listOf(paid) },
                    checkoutResult = { CheckoutStart.Checkout("o1", "https://checkout.stripe.com/c/pay/cs_1") },
                )
            val model = TicketPickerModel("b", client)
            model.load()
            model.increment("paid")
            assertEquals(CheckoutStart.Checkout("o1", "https://checkout.stripe.com/c/pay/cs_1"), model.submit())
        }
}
