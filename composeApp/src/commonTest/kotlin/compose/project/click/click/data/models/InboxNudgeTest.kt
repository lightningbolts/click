package compose.project.click.click.data.models

import compose.project.click.click.data.api.InboxNudgeDto
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboxNudgeTest {
    private fun dto(
        type: String,
        connectionId: String? = "c1",
        beaconId: String? = null,
        payload: Map<String, String> = emptyMap(),
    ) = InboxNudgeDto(
        id = "n1",
        nudgeType = type,
        connectionId = connectionId,
        beaconId = beaconId,
        headline = "Headline",
        body = "Body",
        payload = buildJsonObject { payload.forEach { (k, v) -> put(k, v) } },
    )

    @Test
    fun unknownKindsAreSkipped() {
        assertNull(dto("something_new").typed())
        assertEquals(NudgeKind.WAVE, dto("wave").typed()?.kind)
    }

    @Test
    fun hangoutConfirmConfirmsInsteadOfOpeningChat() {
        val nudge = dto("hangout_confirm", payload = mapOf("confirmation_id" to "h1", "peer_user_id" to "u2")).typed()!!
        assertEquals(NudgeAction.ConfirmHangout("h1"), nudge.primaryAction())
        assertTrue(nudge.resolvedByHangoutEndpoints)
        assertEquals("Confirm", nudge.primaryActionTitle)
        assertEquals("Not us", nudge.secondaryActionTitle)
        // Without the confirmation id there is nothing safe to do.
        assertNull(dto("hangout_confirm").typed()!!.primaryAction())
    }

    @Test
    fun groupRevivalOpensTheGroupChatFromPayload() {
        val nudge = dto("group_revival", connectionId = null, payload = mapOf("chat_id" to "g1", "group_name" to "Climbers")).typed()!!
        assertEquals(NudgeAction.OpenGroupChat("g1"), nudge.primaryAction())
        assertEquals("Climbers", nudge.groupName)
        assertEquals("Plan", nudge.primaryActionTitle)
    }

    @Test
    fun actionsPerKind() {
        assertEquals(NudgeAction.WaveBack("c1"), dto("wave").typed()!!.primaryAction())
        assertEquals(
            NudgeAction.OpenProfile("u2", "c1"),
            dto("memory_prompt", payload = mapOf("peer_user_id" to "u2")).typed()!!.primaryAction(),
        )
        assertEquals(NudgeAction.OpenChat("c1"), dto("anniversary").typed()!!.primaryAction())
        assertEquals(NudgeAction.OpenChat("c1"), dto("reconnect_lull").typed()!!.primaryAction())
        assertEquals(
            NudgeAction.OpenEvent("b1"),
            dto("shared_upcoming_event", beaconId = "b1").typed()!!.primaryAction(),
        )
        assertFalse(dto("wave").typed()!!.resolvedByHangoutEndpoints)
    }

    @Test
    fun nonStringPayloadValuesAreIgnored() {
        val nudge =
            InboxNudgeDto(
                id = "n1",
                nudgeType = "hangout_confirm",
                headline = "",
                body = "",
                payload = buildJsonObject { put("confirmation_id", JsonPrimitive(42)) },
            ).typed()!!
        assertNull(nudge.confirmationId)
    }

    @Test
    fun homePriorityMatchesIos() {
        val ordered = NudgeKind.entries.sortedBy { it.homePriority() }
        assertEquals(
            listOf(
                NudgeKind.HANGOUT_CONFIRM,
                NudgeKind.SHARED_UPCOMING_EVENT,
                NudgeKind.WAVE,
                NudgeKind.ANNIVERSARY,
                NudgeKind.RECONNECT_LULL,
                NudgeKind.MEMORY_PROMPT,
                NudgeKind.GROUP_REVIVAL,
            ),
            ordered,
        )
    }
}
