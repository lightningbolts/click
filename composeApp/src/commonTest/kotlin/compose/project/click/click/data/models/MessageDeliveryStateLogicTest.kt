package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deterministic checks for [Message.withDbDerivedDeliveryState] (no I/O).
 */
class MessageDeliveryStateLogicTest {

    private fun base(
        deliveryState: MessageDeliveryState = MessageDeliveryState.SENT,
        readAt: Long? = null,
        deliveredAt: Long? = null,
    ) = Message(
        id = "m1",
        user_id = "u1",
        content = "x",
        timeCreated = 10L,
        isRead = false,
        readAt = readAt,
        deliveredAt = deliveredAt,
        deliveryState = deliveryState,
    )

    @Test
    fun pending_is_never_overwritten_by_timestamps() {
        val m = base(deliveryState = MessageDeliveryState.PENDING, readAt = 99L, deliveredAt = 88L)
            .withDbDerivedDeliveryState()
        assertEquals(MessageDeliveryState.PENDING, m.deliveryState)
    }

    @Test
    fun error_is_preserved() {
        val m = base(deliveryState = MessageDeliveryState.ERROR, readAt = 99L, deliveredAt = 88L)
            .withDbDerivedDeliveryState()
        assertEquals(MessageDeliveryState.ERROR, m.deliveryState)
    }

    @Test
    fun read_at_promotes_to_read() {
        val m = base(readAt = 42L).withDbDerivedDeliveryState()
        assertEquals(MessageDeliveryState.READ, m.deliveryState)
    }

    @Test
    fun delivered_at_promotes_to_delivered_when_not_read() {
        val m = base(deliveredAt = 7L).withDbDerivedDeliveryState()
        assertEquals(MessageDeliveryState.DELIVERED, m.deliveryState)
    }

    @Test
    fun read_wins_over_delivered() {
        val m = base(readAt = 2L, deliveredAt = 1L).withDbDerivedDeliveryState()
        assertEquals(MessageDeliveryState.READ, m.deliveryState)
    }

    @Test
    fun null_timestamps_stay_sent() {
        val m = base().withDbDerivedDeliveryState()
        assertEquals(MessageDeliveryState.SENT, m.deliveryState)
    }
}
