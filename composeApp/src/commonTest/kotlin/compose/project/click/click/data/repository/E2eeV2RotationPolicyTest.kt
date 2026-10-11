package compose.project.click.click.data.repository

import compose.project.click.click.data.api.ClickWebChatDeviceDto
import compose.project.click.click.data.api.ClickWebRequestException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class E2eeV2RotationPolicyTest {
    private fun device(
        rowId: String,
        deviceId: String,
        userId: String,
    ) = ClickWebChatDeviceDto(
        id = rowId,
        deviceId = deviceId,
        identityPublicKey = "pk-$deviceId",
        keyAlgorithm = "X25519",
        cryptoVersion = 2,
        userId = userId,
    )

    @Test
    fun sharedPhoneRowsCollapseToOneRecipient() {
        // One phone signed into two member accounts: two rows, one logical device id.
        val devices =
            listOf(
                device("row-a", "phone-1", "user-a"),
                device("row-b", "phone-1", "user-b"),
                device("row-c", "android-2", "user-c"),
            )
        val recipients = E2eeV2LifecyclePolicy.distinctEpochRecipients(devices)
        assertEquals(listOf("phone-1", "android-2"), recipients.map { it.deviceId })
    }

    /** Fake epoch API: the state is the current epoch number. */
    private class FakeEpochApi(
        var serverEpoch: Int,
        val rotateError: Exception? = null,
    ) {
        var refetches = 0

        suspend fun rotate(): Int {
            rotateError?.let { throw it }
            serverEpoch += 1
            return serverEpoch
        }

        suspend fun refetch(): Int {
            refetches += 1
            return serverEpoch
        }
    }

    @Test
    fun serverErrorOnSendContinuesOnCurrentEpoch() =
        runTest {
            val api = FakeEpochApi(serverEpoch = 4, rotateError = ClickWebRequestException(500, "boom"))
            val skipped = mutableListOf<Throwable>()
            val state =
                E2eeV2LifecyclePolicy.rotateOrContinue(
                    strict = false,
                    current = 4,
                    rotate = api::rotate,
                    refetch = api::refetch,
                    onSkipped = { skipped += it },
                )
            assertEquals(4, state)
            assertEquals(1, skipped.size)
            assertEquals(0, api.refetches)
        }

    @Test
    fun conflictRefetchesState() =
        runTest {
            // A peer rotated to 5 first; our write of 5 is rejected with 409.
            val api = FakeEpochApi(serverEpoch = 5, rotateError = ClickWebRequestException(409, "epoch must advance"))
            val state =
                E2eeV2LifecyclePolicy.rotateOrContinue(
                    strict = true,
                    current = 4,
                    rotate = api::rotate,
                    refetch = api::refetch,
                )
            assertEquals(5, state)
            assertEquals(1, api.refetches)
        }

    @Test
    fun membershipChangeRethrowsNonConflictFailure() =
        runTest {
            val error = ClickWebRequestException(500, "boom")
            val api = FakeEpochApi(serverEpoch = 4, rotateError = error)
            val thrown =
                assertFailsWith<ClickWebRequestException> {
                    E2eeV2LifecyclePolicy.rotateOrContinue(
                        strict = true,
                        current = 4,
                        rotate = api::rotate,
                        refetch = api::refetch,
                    )
                }
            assertSame(error, thrown)
        }

    @Test
    fun successfulRotationReturnsNewState() =
        runTest {
            val api = FakeEpochApi(serverEpoch = 4)
            val state =
                E2eeV2LifecyclePolicy.rotateOrContinue(
                    strict = false,
                    current = 4,
                    rotate = api::rotate,
                    refetch = api::refetch,
                )
            assertEquals(5, state)
        }
}
