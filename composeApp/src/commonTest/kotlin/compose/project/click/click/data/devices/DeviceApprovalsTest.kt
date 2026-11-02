package compose.project.click.click.data.devices // pragma: allowlist secret

import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalChallengeDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalDecisionBody // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalHttpException // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalsEnvelope // pragma: allowlist secret
import compose.project.click.click.data.repository.DeviceApprovalGateway // pragma: allowlist secret
import compose.project.click.click.data.repository.deviceApprovalProof // pragma: allowlist secret
import compose.project.click.click.data.repository.runDeviceApprovalDecision // pragma: allowlist secret
import compose.project.click.click.platform.DeviceOwnerAuthCancelled // pragma: allowlist secret
import compose.project.click.click.platform.DeviceOwnerAuthUnavailable // pragma: allowlist secret
import compose.project.click.click.ui.screens.approvalFailureMessage // pragma: allowlist secret
import compose.project.click.click.ui.screens.isApprovalClosed // pragma: allowlist secret
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceApprovalsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val challenge =
        DeviceApprovalChallengeDto(
            challengeId = "11111111-1111-1111-1111-111111111111",
            envelope = "env",
            chatId = "device-approval:req",
            epoch = 1,
            senderDeviceId = "click-server",
        )

    private class FakeGateway(
        private val challenge: Result<DeviceApprovalChallengeDto>,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : DeviceApprovalGateway {
        var challengeCalls = 0
        val decisions = mutableListOf<Pair<String, DeviceApprovalDecisionBody>>()

        override suspend fun challenge(
            requestId: String,
            approvingDeviceId: String,
        ): Result<DeviceApprovalChallengeDto> {
            challengeCalls++
            gate?.await()
            return challenge
        }

        override suspend fun decide(
            requestId: String,
            body: DeviceApprovalDecisionBody,
        ): Result<Unit> {
            decisions += requestId to body
            return Result.success(Unit)
        }
    }

    @Test
    fun parsesServerListAndDropsExpiredOrDecidedIncoming() {
        val raw =
            """
            {"incoming":[
              {"id":"a","status":"pending","device_label":"iPhone","created_at":"2026-11-01T10:00:00Z","expired":false,"email_sent":false},
              {"id":"b","status":"pending","device_label":"Chrome on Mac","expired":true},
              {"id":"c","status":"approved"},
              {"id":"","status":"pending"},
              {"id":"d","status":"weird"}
            ],
            "own":{"id":"o","status":"denied","device_label":"Android phone","email_sent":true},
            "approvers":[{"label":"iPhone","last_seen_at":null}]}
            """.trimIndent()
        val approvals = DeviceApprovals.from(json.decodeFromString(DeviceApprovalsEnvelope.serializer(), raw))
        assertEquals(listOf("a"), approvals.incoming.map { it.id })
        assertEquals("iPhone", approvals.incoming.single().deviceLabel)
        assertNotNull(approvals.incoming.single().createdAt)
        val own = assertNotNull(approvals.own)
        assertEquals(DeviceApproval.Status.DENIED, own.status)
        assertTrue(own.emailSent)
    }

    @Test
    fun copyMatchesIos() {
        assertEquals("An iPhone", DeviceApprovalCopy.withArticle("iPhone"))
        assertEquals("A web browser", DeviceApprovalCopy.withArticle("Web browser"))
        assertEquals("Chrome on Mac", DeviceApprovalCopy.withArticle("Chrome on Mac"))
        assertEquals("An Android phone", DeviceApprovalCopy.withArticle("Android phone"))
        assertEquals("A new device", DeviceApprovalCopy.withArticle(null))
        assertEquals(
            "Your past messages will appear in Chrome on your Mac in a moment.",
            DeviceApprovalCopy.approvedDetail("Chrome on Mac"),
        )
        assertEquals("Your past messages will appear on your iPhone in a moment.", DeviceApprovalCopy.approvedDetail("iPhone"))
        assertTrue(DeviceApprovalCopy.deniedDetail("Web browser").startsWith("That browser can't"))
    }

    @Test
    fun waitingBannerActions() {
        val pending = DeviceApproval("o", DeviceApproval.Status.PENDING, null, null, expired = false, emailSent = false)
        assertEquals(DeviceApprovalCopy.WaitingAction.EMAIL_LINK, DeviceApprovalCopy.waitingAction(pending, emailSent = false))
        assertNull(DeviceApprovalCopy.waitingAction(pending, emailSent = true))
        assertEquals(
            DeviceApprovalCopy.WaitingAction.ASK_AGAIN,
            DeviceApprovalCopy.waitingAction(pending.copy(expired = true), emailSent = true),
        )
        assertEquals(
            DeviceApprovalCopy.WaitingAction.ASK_AGAIN,
            DeviceApprovalCopy.waitingAction(pending.copy(status = DeviceApproval.Status.DENIED), emailSent = false),
        )
        assertEquals(
            "Approve this phone in Click on a device you already use.",
            DeviceApprovalCopy.waitingSubtitle(pending, emailSent = false, thisDevice = "Android phone"),
        )
    }

    @Test
    fun proofUnwrapsWithThisDeviceAsRecipientAndBase64EncodesTheKey() {
        var seen: MessageCryptoV2.EpochKeyWrapMetadata? = null
        val proof =
            deviceApprovalProof(challenge, myDeviceId = "android-1") { metadata, envelope ->
                seen = metadata
                assertEquals("env", envelope)
                ByteArray(32) { 7 }
            }
        assertEquals(
            MessageCryptoV2.EpochKeyWrapMetadata("device-approval:req", 1, "click-server", "android-1"),
            seen,
        )
        assertEquals("BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=", proof)
    }

    @Test
    fun approveSendsDecisionWithChallengeAndProof() =
        runTest {
            val gateway = FakeGateway(Result.success(challenge))
            var authorized = false
            runDeviceApprovalDecision("req", approve = true, myDeviceId = "android-1", gateway = gateway, unwrap = { _, _ ->
                ByteArray(32) { 7 }
            }, authorize = { authorized = true })
            assertTrue(authorized)
            val (id, body) = gateway.decisions.single()
            assertEquals("req", id)
            assertEquals("approve", body.decision)
            assertEquals("android-1", body.approvingDeviceId)
            assertEquals(challenge.challengeId, body.challengeId)
            assertEquals("BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=", body.proof)
        }

    @Test
    fun cancelledAuthorizationSendsNothing() =
        runTest {
            val gateway = FakeGateway(Result.success(challenge), gate = CompletableDeferred())
            assertFailsWith<DeviceOwnerAuthCancelled> {
                runDeviceApprovalDecision("req", approve = true, myDeviceId = "d", gateway = gateway, unwrap = { _, _ ->
                    ByteArray(32)
                }, authorize = { throw DeviceOwnerAuthCancelled() })
            }
            assertTrue(gateway.decisions.isEmpty())
        }

    @Test
    fun denyNeedsNoAuthorizationButStillProvesTheDevice() =
        runTest {
            val gateway = FakeGateway(Result.success(challenge))
            runDeviceApprovalDecision("req", approve = false, myDeviceId = "d", gateway = gateway, unwrap = { _, _ ->
                ByteArray(32)
            }, authorize = null)
            assertEquals(
                "deny",
                gateway.decisions
                    .single()
                    .second.decision,
            )
            assertEquals(1, gateway.challengeCalls)
        }

    @Test
    fun challengeFailurePropagatesAndSendsNoDecision() =
        runTest {
            val gateway = FakeGateway(Result.failure(IllegalStateException("403")))
            assertFailsWith<IllegalStateException> {
                runDeviceApprovalDecision("req", approve = true, myDeviceId = "d", gateway = gateway, unwrap = { _, _ ->
                    ByteArray(32)
                }, authorize = {})
            }
            assertTrue(gateway.decisions.isEmpty())
        }

    @Test
    fun failureMessages() {
        assertNull(approvalFailureMessage(DeviceOwnerAuthCancelled()))
        assertTrue(approvalFailureMessage(DeviceOwnerAuthUnavailable())!!.startsWith("Device authentication is required"))
        assertTrue(approvalFailureMessage(IllegalStateException())!!.startsWith("Couldn't reach Click"))
    }

    @Test
    fun alreadyDecidedAndExpiredGetTheirOwnMessageAndCloseTheRequest() {
        val decided = DeviceApprovalHttpException(409, "decided")
        val expired = DeviceApprovalHttpException(410, "expired")
        assertTrue(approvalFailureMessage(decided)!!.contains("already decided"))
        assertTrue(approvalFailureMessage(expired)!!.contains("expired"))
        assertTrue(isApprovalClosed(decided))
        assertTrue(isApprovalClosed(expired))
        assertTrue(!isApprovalClosed(DeviceApprovalHttpException(500, "boom")))
        assertTrue(approvalFailureMessage(DeviceApprovalHttpException(500, "boom"))!!.startsWith("Couldn't reach Click"))
    }

    @Test
    fun staleRefreshCannotOverwriteNewerState() {
        DeviceApprovalStore.reset()
        val pending = DeviceApproval("a", DeviceApproval.Status.PENDING, "iPhone", null, expired = false, emailSent = false)
        val withA = DeviceApprovals(incoming = listOf(pending))

        // A refresh started before a decision must not bring the decided request back.
        val token = DeviceApprovalStore.beginRefresh()
        DeviceApprovalStore.decided("a")
        assertTrue(!DeviceApprovalStore.publish(token, withA))
        assertTrue(
            DeviceApprovalStore.state.value.incoming
                .isEmpty(),
        )

        // Nor survive a sign-out reset.
        val token2 = DeviceApprovalStore.beginRefresh()
        DeviceApprovalStore.reset()
        assertTrue(!DeviceApprovalStore.publish(token2, withA))
        assertTrue(
            DeviceApprovalStore.state.value.incoming
                .isEmpty(),
        )

        // An account that no longer matches is dropped too.
        val token3 = DeviceApprovalStore.beginRefresh()
        assertTrue(!DeviceApprovalStore.publish(token3, withA) { false })

        // The newest refresh wins.
        val older = DeviceApprovalStore.beginRefresh()
        val newer = DeviceApprovalStore.beginRefresh()
        assertTrue(!DeviceApprovalStore.publish(older, withA))
        assertTrue(DeviceApprovalStore.publish(newer, withA))
        assertEquals(
            listOf("a"),
            DeviceApprovalStore.state.value.incoming
                .map { it.id },
        )
        DeviceApprovalStore.reset()
    }
}
