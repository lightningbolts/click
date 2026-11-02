package compose.project.click.click.data.places

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Behaviour follows iOS `Tests/ClickTests/PlaceDetailModelTests.swift`. */
class PlaceDetailControllerTest {
    private val energyQ = PulseQuestionDto(key = "energy", prompt = "How's the vibe?")
    private val talkableQ = PulseQuestionDto(key = "talkable", prompt = "Easy to talk?", options = listOf(PulseQuestionOptionDto(1, "Yes")))
    private val wouldReturnQ = PulseQuestionDto(key = "would_return", prompt = "Come back at this time?", phase = "leaving")

    private fun detail(
        canPulse: Boolean = true,
        reason: String? = null,
        checkIn: PlaceCheckInDto? = null,
        manager: Boolean = false,
    ) = PlaceDetail(
        summary = PlaceSummaryDto(id = "p1", slug = "cafe", latitude = 1.0, longitude = 2.0),
        checkIn = checkIn,
        isManager = manager,
        pulseEligibility =
            PulseEligibilityDto(
                canPulse = canPulse,
                reason = reason,
                questions = listOf(energyQ, talkableQ),
                leavingQuestions = listOf(wouldReturnQ),
            ),
    )

    private class FakeRepo(
        var detailResult: Result<PlaceDetail>,
    ) : PlacesRepository {
        val detailSources = mutableListOf<String?>()
        val checkInBodies = mutableListOf<JsonObject>()
        val pulseBodies = mutableListOf<JsonObject>()
        val patchBodies = mutableListOf<JsonObject>()
        var checkInResult: Result<PlaceCheckInDto> = Result.success(PlaceCheckInDto(checkedIn = true, checkInId = "ci1"))
        var checkOutResult: Result<Boolean> = Result.success(true)
        var updateResult: Result<PulseUpdateResult> = Result.success(PulseUpdateResult(PulseSummaryDto(reportCount = 9)))

        /** Runs inside detail(), to model a check-out landing while a fetch is in flight. */
        var duringDetail: (suspend () -> Unit)? = null
        var pulseResult: Result<PulseSubmitResult> =
            Result.success(PulseSubmitResult("pl1", null, PulseSummaryDto(state = "live", label = "packed", reportCount = 1)))

        override suspend fun nearby(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
        ) = Result.success(emptyList<PlaceSummaryDto>())

        override suspend fun detail(
            idOrSlug: String,
            source: String?,
        ): Result<PlaceDetail> {
            detailSources += source
            val result = detailResult
            duringDetail?.let {
                duringDetail = null
                it()
            }
            return result
        }

        override suspend fun checkIn(
            placeId: String,
            body: JsonObject,
        ): Result<PlaceCheckInDto> {
            checkInBodies += body
            return checkInResult
        }

        override suspend fun checkOut(placeId: String) = checkOutResult

        override suspend fun submitPulse(
            placeId: String,
            body: JsonObject,
        ): Result<PulseSubmitResult> {
            pulseBodies += body
            return pulseResult
        }

        override suspend fun updatePulse(
            placeId: String,
            pulseId: String,
            body: JsonObject,
        ): Result<PulseUpdateResult> {
            patchBodies += body
            return updateResult
        }

        override suspend fun myPlaces() = Result.success(emptyList<MyPlaceVisit>())
    }

    private val fix = PlaceLocationOutcome.Fix(1.0, 2.0, 15.0)

    @Test
    fun sourceIsSentOnlyOnFirstFetchAndFailureShowsError() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            val c = PlaceDetailController("cafe", null, repo, source = "history") { fix }
            c.load()
            c.load()
            assertEquals(listOf("history", null), repo.detailSources)
            assertIs<PlaceLoadState.Loaded>(c.state.value.load)

            val failing = FakeRepo(Result.failure(PlaceApiException(PlaceError.NotFound)))
            val f = PlaceDetailController("gone", null, failing) { fix }
            f.load()
            assertEquals(PlaceLoadState.Failed(PlaceError.NotFound), f.state.value.load)
        }

    @Test
    fun refreshFailureKeepsDetail() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            repo.detailResult = Result.failure(PlaceApiException(PlaceError.Network))
            c.load()
            assertEquals(
                "p1",
                c.state.value.detail
                    ?.id,
            )
            assertIs<PlaceLoadState.Loaded>(c.state.value.load)
        }

    @Test
    fun gpsCheckInNeedsLocation() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            val c = PlaceDetailController("cafe", null, repo) { PlaceLocationOutcome.Denied }
            c.load()
            c.checkIn()
            assertEquals(PlaceCheckInPhase.Failed(PlaceError.NoLocation), c.state.value.checkIn)
            assertTrue(repo.checkInBodies.isEmpty())
        }

    @Test
    fun qrCheckInWorksWithoutLocationAndAsksFirst() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            val c = PlaceDetailController("cafe", "tok", repo) { PlaceLocationOutcome.Unavailable }
            assertTrue(c.state.value.showAnchorConfirmation)
            c.load()
            assertEquals("qr", repo.detailSources.first())
            c.confirmAnchorCheckIn()
            val body = repo.checkInBodies.single()
            assertEquals("tok", body["anchor_token"]!!.jsonPrimitive.content)
            assertFalse("latitude" in body)
            assertFalse(c.state.value.showAnchorConfirmation)
            assertNull(c.state.value.pendingAnchorToken)
            assertEquals(PlaceCheckInPhase.Idle, c.state.value.checkIn)
        }

    @Test
    fun dismissingQrDropsTheToken() {
        val c = PlaceDetailController("cafe", "tok", FakeRepo(Result.success(detail()))) { fix }
        c.dismissAnchorConfirmation()
        assertFalse(c.state.value.showAnchorConfirmation)
        assertNull(c.state.value.pendingAnchorToken)
    }

    @Test
    fun checkInSendsShareFlagAndMapsRejection() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            repo.checkInResult = Result.failure(PlaceApiException(PlaceError.OutOfBounds(400.0)))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            c.setShareWithConnections(true)
            c.checkIn()
            assertEquals(
                "true",
                repo.checkInBodies
                    .single()["share_with_connections"]!!
                    .jsonPrimitive.content,
            )
            assertEquals(PlaceCheckInPhase.Failed(PlaceError.OutOfBounds(400.0)), c.state.value.checkIn)
        }

    @Test
    fun checkOutAsksWouldReturn() =
        runTest {
            val repo = FakeRepo(Result.success(detail(checkIn = PlaceCheckInDto(active = true, checkInId = "ci1"))))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            assertTrue(c.state.value.isCheckedIn)
            repo.detailResult = Result.success(detail())
            c.checkOut()
            assertFalse(c.state.value.isCheckedIn)
            assertTrue(c.state.value.showWouldReturn)
            c.answerWouldReturn(1)
            assertFalse(c.state.value.showWouldReturn)
            assertEquals(
                1,
                repo.pulseBodies
                    .single()["would_return"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
        }

    @Test
    fun detailStartedBeforeCheckOutIsIgnored() =
        runTest {
            val checkedIn = detail(checkIn = PlaceCheckInDto(active = true, checkInId = "ci1"))
            val repo = FakeRepo(Result.success(checkedIn))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            // A refresh that still says "checked in" is in flight when Leave succeeds.
            repo.duringDetail = {
                repo.detailResult = Result.success(detail())
                c.checkOut()
            }
            c.load()
            assertFalse(c.state.value.isCheckedIn)
        }

    @Test
    fun failedWouldReturnBringsThePromptBack() =
        runTest {
            val repo = FakeRepo(Result.success(detail(checkIn = PlaceCheckInDto(active = true, checkInId = "ci1"))))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            c.checkOut()
            repo.pulseResult = Result.failure(PlaceApiException(PlaceError.Network))
            c.answerWouldReturn(1)
            assertTrue(c.state.value.showWouldReturn)
        }

    @Test
    fun followUpNetworkFailureOffersItAgainButRejectionDoesNot() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            c.submitEnergy(3)
            repo.updateResult = Result.failure(PlaceApiException(PlaceError.Network))
            c.answerFollowUp(talkableQ, 1)
            assertEquals(
                listOf("talkable"),
                c.state.value
                    .remainingFollowUps(0)
                    .map { it.key },
            )
            repo.updateResult = Result.failure(PlaceApiException(PlaceError.NotEditable))
            c.answerFollowUp(talkableQ, 1)
            assertTrue(
                c.state.value
                    .remainingFollowUps(0)
                    .isEmpty(),
            )
        }

    @Test
    fun onlySubmittableFollowUpsAreOffered() {
        val odd = PulseQuestionDto(key = "noise", prompt = "Loud?")
        val d = detail().let { it.copy(pulseEligibility = it.pulseEligibility!!.copy(questions = listOf(energyQ, talkableQ, odd))) }
        val s = PlaceDetailUiState(detail = d, pulse = PlacePulsePhase.Submitted("pl1", null))
        assertEquals(listOf("talkable"), s.remainingFollowUps(0).map { it.key })
    }

    @Test
    fun missingPulseSummaryKeepsTheShownPulse() =
        runTest {
            val shown = PulseSummaryDto(state = "live", label = "chill", reportCount = 4)
            val d = detail().let { it.copy(summary = it.summary.copy(pulse = shown)) }
            val repo = FakeRepo(Result.success(d))
            repo.pulseResult = Result.success(PulseSubmitResult("pl1", null, summary = null))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            c.submitEnergy(2)
            assertTrue(c.state.value.pulseSubmitted)
            assertEquals(
                shown,
                c.state.value.detail
                    ?.summary
                    ?.pulse,
            )
        }

    @Test
    fun pulseCardVisibility() {
        assertTrue(PlaceDetailUiState(detail = detail()).showsPulseCard)
        assertTrue(PlaceDetailUiState(detail = detail(canPulse = false, reason = "cooldown")).showsPulseCard)
        assertFalse(PlaceDetailUiState(detail = detail(canPulse = false, reason = "cooldown")).energyChipsEnabled)
        assertFalse(PlaceDetailUiState(detail = detail(canPulse = false, reason = "not_present")).showsPulseCard)
        assertFalse(PlaceDetailUiState(detail = detail(manager = true)).showsPulseCard)
        assertEquals("How's the vibe?", PlaceDetailUiState(detail = detail()).energyPrompt)
        assertEquals("How's the energy?", PlaceDetailUiState().energyPrompt)
    }

    @Test
    fun energyThenFollowUps() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            c.submitEnergy(4)
            assertEquals(
                4,
                repo.pulseBodies
                    .single()["energy"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
            val s = c.state.value
            assertTrue(s.pulseSubmitted)
            assertFalse(s.energyChipsEnabled)
            assertEquals(
                EnergyLabel.PACKED,
                s.detail
                    ?.summary
                    ?.pulse
                    ?.energy,
            )
            assertEquals(listOf("talkable"), s.remainingFollowUps(0).map { it.key })

            c.answerFollowUp(talkableQ, 1)
            assertEquals(setOf("talkable"), repo.patchBodies.single().keys)
            assertTrue(
                c.state.value
                    .remainingFollowUps(0)
                    .isEmpty(),
            )
            assertEquals(
                9,
                c.state.value.detail
                    ?.summary
                    ?.pulse
                    ?.reportCount,
            )
        }

    @Test
    fun followUpsStopWhenWindowCloses() {
        val s = PlaceDetailUiState(detail = detail(), pulse = PlacePulsePhase.Submitted("pl1", editableUntilMs = 1_000))
        assertEquals(1, s.remainingFollowUps(999).size)
        assertTrue(s.remainingFollowUps(1_000).isEmpty())
    }

    @Test
    fun cooldownRejectionReloads() =
        runTest {
            val repo = FakeRepo(Result.success(detail()))
            repo.pulseResult = Result.failure(PlaceApiException(PlaceError.Cooldown(null)))
            val c = PlaceDetailController("cafe", null, repo) { fix }
            c.load()
            c.submitEnergy(2)
            assertEquals(PlacePulsePhase.Failed(PlaceError.Cooldown(null)), c.state.value.pulse)
            assertEquals(2, repo.detailSources.size)
        }
}
