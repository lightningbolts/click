package compose.project.click.click.data

import compose.project.click.click.chat.DEVICE_APPROVAL_EPOCH
import compose.project.click.click.chat.DEVICE_APPROVAL_SENDER_DEVICE_ID
import compose.project.click.click.chat.DeviceApprovalError
import compose.project.click.click.chat.deviceApprovalWrapMetadata
import compose.project.click.click.chat.encodeDeviceApprovalProof
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.AskForHistoryBody
import compose.project.click.click.data.api.AskForHistoryResponseDto
import compose.project.click.click.data.api.DeviceApprovalChallengeDto
import compose.project.click.click.data.api.DeviceApprovalDecisionBody
import compose.project.click.click.data.api.DeviceHistoryRequestDto
import compose.project.click.click.data.api.DeviceHistoryRequestsDto
import compose.project.click.click.data.api.MyDeviceDto
import compose.project.click.click.data.api.MyDevicesDto
import compose.project.click.click.data.api.parseClickWebCodedError
import compose.project.click.click.data.api.ClickWebRegisterChatDeviceBody
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REQUEST_ID = "4f1c2a3b-5d6e-4f70-8a9b-0c1d2e3f4a5b"

/** Records what the approval flow sends; answers like the click-web routes. */
private class FakeDeviceApi(
    private val challenge: Result<DeviceApprovalChallengeDto> =
        Result.success(
            DeviceApprovalChallengeDto(
                challengeId = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
                envelope = "e2e2.wrapped",
                chatId = "approval:$REQUEST_ID",
                epoch = 1,
                senderDeviceId = "click-device-approval",
            ),
        ),
    private val decision: Result<DeviceHistoryRequestDto?> = Result.success(DeviceHistoryRequestDto(REQUEST_ID, "approved")),
) : DeviceApi {
    val challenged = mutableListOf<Pair<String, String>>()
    val decided = mutableListOf<Pair<String, DeviceApprovalDecisionBody>>()

    override suspend fun historyRequests(deviceId: String) = Result.success(DeviceHistoryRequestsDto())

    override suspend fun askForHistory(
        deviceId: String,
        reopen: Boolean,
    ) = Result.success(AskForHistoryResponseDto())

    override suspend fun historyRequest(requestId: String) = Result.success<DeviceHistoryRequestDto?>(null)

    override suspend fun challenge(
        requestId: String,
        approvingDeviceId: String,
    ): Result<DeviceApprovalChallengeDto> {
        challenged += requestId to approvingDeviceId
        return challenge
    }

    override suspend fun decide(
        requestId: String,
        body: DeviceApprovalDecisionBody,
    ): Result<DeviceHistoryRequestDto?> {
        decided += requestId to body
        return decision
    }

    override suspend fun emailLink(requestId: String) = Result.success(true)

    override suspend fun myDevices() = Result.success(emptyList<MyDeviceDto>())

    override suspend fun removeDevice(deviceId: String) = Result.success(Unit)
}

class DeviceApprovalFlowTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val nonce = ByteArray(32) { it.toByte() }

    @Test
    fun proofIsPaddedStandardBase64OfThe32ByteNonce() {
        assertEquals("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", encodeDeviceApprovalProof(nonce))
        assertEquals(
            "+/7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v7+/v4=",
            encodeDeviceApprovalProof(ByteArray(32) { if (it == 0) 0xFB.toByte() else 0xFE.toByte() }),
        )
        assertFailsWith<IllegalArgumentException> { encodeDeviceApprovalProof(ByteArray(16)) }
    }

    @Test
    fun challengeUnwrapsWithTheApprovalMetadata() {
        val metadata =
            deviceApprovalWrapMetadata(REQUEST_ID, "android-device-1", DeviceApprovalChallengeDto("c", "e", null, null, null))
        assertEquals(MessageCryptoV2.EpochKeyWrapMetadata("approval:$REQUEST_ID", 1, "click-device-approval", "android-device-1"), metadata)
        assertEquals(DEVICE_APPROVAL_EPOCH, metadata.epoch)
        assertEquals(DEVICE_APPROVAL_SENDER_DEVICE_ID, metadata.senderDeviceId)
        assertFailsWith<IllegalArgumentException> {
            deviceApprovalWrapMetadata(REQUEST_ID, "d", DeviceApprovalChallengeDto("c", "e", "approval:other", 1, null))
        }
    }

    @Test
    fun approveSendsTheUnwrappedNonceAsProof() =
        runTest {
            val api = FakeDeviceApi()
            var unwrappedWith: MessageCryptoV2.EpochKeyWrapMetadata? = null
            val result =
                decideDeviceApproval(api, REQUEST_ID, approve = true, myDeviceId = "android-device-1") { metadata, envelope ->
                    unwrappedWith = metadata
                    assertEquals("e2e2.wrapped", envelope)
                    nonce
                }
            assertTrue(result.isSuccess)
            assertEquals(listOf(REQUEST_ID to "android-device-1"), api.challenged)
            assertEquals("approval:$REQUEST_ID", unwrappedWith?.chatId)
            assertEquals("android-device-1", unwrappedWith?.recipientDeviceId)
            val (id, body) = api.decided.single()
            assertEquals(REQUEST_ID, id)
            assertEquals(
                DeviceApprovalDecisionBody(
                    decision = "approve",
                    approvingDeviceId = "android-device-1",
                    challengeId = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
                    proof = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
                ),
                body,
            )
            assertEquals(
                """{"decision":"approve","approving_device_id":"android-device-1","challenge_id":"9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","proof":"AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="}""",
                json.encodeToString(DeviceApprovalDecisionBody.serializer(), body),
            )
        }

    @Test
    fun denyAndChallengeFailures() =
        runTest {
            val deny = FakeDeviceApi()
            decideDeviceApproval(deny, REQUEST_ID, approve = false, myDeviceId = "d") { _, _ -> nonce }
            assertEquals("deny", deny.decided.single().second.decision)

            val notAllowed = FakeDeviceApi(challenge = Result.failure(parseClickWebCodedError(403, """{"error":"x","code":"APPROVER_NOT_ALLOWED"}""")))
            val failed = decideDeviceApproval(notAllowed, REQUEST_ID, approve = true, myDeviceId = "d") { _, _ -> nonce }
            assertEquals(DeviceApprovalError.ApproverNotAllowed, DeviceApprovalError.from(failed.exceptionOrNull()!!))
            assertTrue(notAllowed.decided.isEmpty())

            // A nonce of the wrong size never reaches the server.
            val short = FakeDeviceApi()
            assertTrue(decideDeviceApproval(short, REQUEST_ID, approve = true, myDeviceId = "d") { _, _ -> ByteArray(31) }.isFailure)
            assertTrue(short.decided.isEmpty())
        }

    @Test
    fun errorCodeMap() {
        fun map(
            status: Int,
            body: String,
        ) = DeviceApprovalError.from(parseClickWebCodedError(status, body))
        assertEquals(DeviceApprovalError.EmailProofRequired, map(403, """{"error":"Open the link","code":"EMAIL_PROOF_REQUIRED"}"""))
        assertEquals(DeviceApprovalError.DeviceProofInvalid, map(403, """{"error":"x","code":"DEVICE_PROOF_INVALID"}"""))
        assertEquals(DeviceApprovalError.ApproverNotAllowed, map(403, """{"error":"x","code":"APPROVER_NOT_ALLOWED"}"""))
        assertEquals(DeviceApprovalError.NotFound, map(404, """{"error":"Request not found","code":"NOT_FOUND"}"""))
        assertEquals(DeviceApprovalError.AlreadyDecided, map(409, """{"error":"x","code":"HISTORY_REQUEST_DECIDED"}"""))
        assertEquals(DeviceApprovalError.Expired, map(410, """{"error":"x","code":"HISTORY_REQUEST_EXPIRED"}"""))
        assertEquals(DeviceApprovalError.DeviceGone, map(410, """{"error":"x","code":"HISTORY_REQUEST_DEVICE_GONE"}"""))
        // Status fallbacks for bodies without a code (e.g. an HTML error page).
        assertEquals(DeviceApprovalError.NotFound, map(404, "<html>"))
        assertEquals(DeviceApprovalError.AlreadyDecided, map(409, ""))
        assertEquals(DeviceApprovalError.Expired, map(410, "{}"))
        assertEquals(DeviceApprovalError.Unknown, map(500, """{"error":"Could not save your decision","code":"HISTORY_REQUEST_FAILED"}"""))
        assertEquals(DeviceApprovalError.Unknown, DeviceApprovalError.from(IllegalStateException("offline")))

        assertTrue(DeviceApprovalError.DeviceProofInvalid.retryable)
        assertTrue(DeviceApprovalError.Unknown.retryable)
        assertFalse(DeviceApprovalError.AlreadyDecided.retryable)
        assertFalse(DeviceApprovalError.Expired.retryable)

        val parsed = parseClickWebCodedError(410, """{"error":"This request has expired","code":"HISTORY_REQUEST_EXPIRED"}""")
        assertEquals(410, parsed.statusCode)
        assertEquals("HISTORY_REQUEST_EXPIRED", parsed.code)
        assertEquals("This request has expired", parsed.message)
        assertEquals("Request failed (502)", parseClickWebCodedError(502, null).message)
    }

    @Test
    fun decodesHistoryRequestsList() {
        val dto =
            json.decodeFromString(
                DeviceHistoryRequestsDto.serializer(),
                """
                {"incoming":[{"id":"$REQUEST_ID","status":"pending","device_label":"Chrome on Mac",
                  "created_at":"2026-10-06T11:58:00.123+00:00","expires_at":"2026-10-07T11:58:00+00:00",
                  "decided_at":null,"email_sent":false,"expired":false}],
                 "own":{"id":"r2","status":"denied","device_label":null,"created_at":"2026-10-01T00:00:00+00:00",
                  "expires_at":"2026-10-02T00:00:00+00:00","decided_at":"2026-10-01T00:05:00+00:00","email_sent":true,"expired":false},
                 "approvers":[{"label":"iPhone","last_seen_at":"2026-10-06T11:00:00+00:00"},{"label":null,"last_seen_at":null}]}
                """.trimIndent(),
            )
        val incoming = dto.incoming.single()
        assertEquals("Chrome on Mac", incoming.deviceLabel)
        assertTrue(incoming.isPending)
        assertTrue(dto.own!!.isDenied)
        assertTrue(dto.own!!.emailSent)
        assertEquals(listOf("iPhone", null), dto.approvers.map { it.label })

        val empty = json.decodeFromString(DeviceHistoryRequestsDto.serializer(), """{"incoming":[],"own":null,"approvers":[]}""")
        assertNull(empty.own)
    }

    @Test
    fun decodesChallengeDevicesAndAskResponses() {
        val challenge =
            json.decodeFromString(
                DeviceApprovalChallengeDto.serializer(),
                """{"challenge_id":"c1","envelope":"e2e2.x","chat_id":"approval:$REQUEST_ID","epoch":1,"sender_device_id":"click-device-approval"}""",
            )
        assertEquals("c1", challenge.challengeId)
        assertEquals(1, challenge.epoch)

        val devices =
            json.decodeFromString(
                MyDevicesDto.serializer(),
                """{"devices":[{"device_id":"android-1","label":"Pixel 9","created_at":"2026-09-01T00:00:00+00:00","last_seen_at":null}]}""",
            )
        assertEquals("Pixel 9", devices.devices.single().label)

        val ask = json.decodeFromString(AskForHistoryResponseDto.serializer(), """{"status":"exists","own":{"id":"r","status":"pending"}}""")
        assertEquals("exists", ask.status)
        assertEquals("pending", ask.own?.status)
    }

    @Test
    fun encodesRequestBodiesWithoutUnsetFields() {
        assertEquals("""{"device_id":"d1"}""", json.encodeToString(AskForHistoryBody.serializer(), AskForHistoryBody("d1")))
        assertEquals(
            """{"device_id":"d1","reopen":true}""",
            json.encodeToString(AskForHistoryBody.serializer(), AskForHistoryBody("d1", reopen = true)),
        )
        assertEquals(
            """{"device_id":"d1","identity_public_key":"k","device_label":"Pixel 9"}""",
            json.encodeToString(ClickWebRegisterChatDeviceBody.serializer(), ClickWebRegisterChatDeviceBody("d1", "k", "Pixel 9")),
        )
        assertEquals(
            """{"device_id":"d1","identity_public_key":"k"}""",
            json.encodeToString(ClickWebRegisterChatDeviceBody.serializer(), ClickWebRegisterChatDeviceBody("d1", "k")),
        )
    }

    @Test
    fun thisDeviceIsListedFirst() {
        val devices = listOf(MyDeviceDto("a"), MyDeviceDto("me"), MyDeviceDto("b"))
        assertEquals(listOf("me", "a", "b"), compose.project.click.click.viewmodel.sortDevicesForSettings(devices, "me").map { it.deviceId })
    }
}
