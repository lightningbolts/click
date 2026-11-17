package compose.project.click.click.viewmodel

import compose.project.click.click.collaboration.ClickDropDevelopBook
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.ClickDropRevealRef
import compose.project.click.click.data.api.DropDevelopResultDto
import compose.project.click.click.data.models.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private class FakeDevelopService : ChatDropDevelopService {
    var developResult: (List<String>) -> Result<List<DropDevelopResultDto>> = { ids ->
        Result.success(
            ids.map { DropDevelopResultDto("chat", it, "developed", developedAt = "2026-10-01T12:00:00Z", url = "https://o/$it") },
        )
    }
    var viewsResult: Result<Map<String, String>> = Result.success(emptyMap())
    val developCalls = mutableListOf<List<String>>()
    val viewCalls = mutableListOf<List<String>>()

    override suspend fun develop(ids: List<String>): Result<List<DropDevelopResultDto>> {
        developCalls += ids
        return developResult(ids)
    }

    override suspend fun developedAt(ids: List<String>): Result<Map<String, String>> {
        viewCalls += ids
        return viewsResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDropDevelopControllerTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val ready = ClickDropRevealRef("a", now - 3600.seconds)
    private val ready2 = ClickDropRevealRef("b", now - 60.seconds)
    private val pending = ClickDropRevealRef("p", now + 60.seconds)

    private class Harness(
        val controller: ChatDropDevelopController,
        val developed: MutableList<Pair<String, String?>>,
        val errors: MutableList<String>,
    )

    private fun TestScope.harness(
        service: FakeDevelopService,
        clock: () -> Instant = { now },
    ): Harness {
        val developed = mutableListOf<Pair<String, String?>>()
        val errors = mutableListOf<String>()
        val controller =
            ChatDropDevelopController(
                scope = this,
                service = service,
                onDeveloped = { id, url -> developed += id to url },
                onError = { errors += it },
                now = clock,
            )
        return Harness(controller, developed, errors)
    }

    @Test
    fun developAllSendsOnlyReadyDropsAndHandsOverTheirOriginals() =
        runTest {
            val service = FakeDevelopService()
            val h = harness(service)
            h.controller.develop(listOf(ready, pending, ready2))
            assertEquals(setOf("a", "b"), h.controller.book.value.developing)
            advanceUntilIdle()
            assertEquals(listOf(listOf("a", "b")), service.developCalls)
            assertEquals(listOf<Pair<String, String?>>("a" to "https://o/a", "b" to "https://o/b"), h.developed)
            val book = h.controller.book.value
            assertEquals(ClickDropDevelopState.Developed, book.state(ready, now))
            assertEquals(setOf("a", "b"), book.fresh)
            assertTrue(book.developing.isEmpty())
        }

    @Test
    fun developingTwiceDoesNotResendInFlightOrDevelopedDrops() =
        runTest {
            val service = FakeDevelopService()
            val h = harness(service)
            h.controller.develop(listOf(ready))
            h.controller.develop(listOf(ready))
            advanceUntilIdle()
            h.controller.develop(listOf(ready))
            advanceUntilIdle()
            assertEquals(1, service.developCalls.size)
        }

    @Test
    fun aDropTheServerStillCallsPendingStaysReady() =
        runTest {
            val service = FakeDevelopService()
            service.developResult = { ids -> Result.success(ids.map { DropDevelopResultDto("chat", it, "pending") }) }
            val h = harness(service)
            h.controller.develop(listOf(ready))
            advanceUntilIdle()
            assertEquals(
                ClickDropDevelopState.Ready,
                h.controller.book.value
                    .state(ready, now),
            )
            assertTrue(h.developed.isEmpty())
        }

    @Test
    fun aFailedDevelopSaysSoAndCanBeRetried() =
        runTest {
            val service = FakeDevelopService()
            service.developResult = { Result.failure(Exception("offline")) }
            val h = harness(service)
            h.controller.develop(listOf(ready))
            advanceUntilIdle()
            assertEquals(listOf(CLICK_DROP_DEVELOP_FAILED), h.errors)
            assertTrue(
                h.controller.book.value.developing
                    .isEmpty(),
            )
            assertEquals(
                listOf(ready),
                h.controller.book.value
                    .ready(listOf(ready), now),
            )
        }

    @Test
    fun liveDevelopOnlyFiresRightAtReveal() =
        runTest {
            val service = FakeDevelopService()
            val h = harness(service)
            h.controller.developIfJustRevealed(ready)
            h.controller.developIfJustRevealed(pending)
            advanceUntilIdle()
            assertTrue(service.developCalls.isEmpty())
            val atReveal = harness(service, clock = { now + 61.seconds })
            atReveal.controller.developIfJustRevealed(pending)
            advanceUntilIdle()
            assertEquals(listOf(listOf("p")), service.developCalls)
        }

    @Test
    fun checkAsksTheServerOnceForEveryRevealedDrop() =
        runTest {
            val service = FakeDevelopService()
            service.viewsResult = Result.success(mapOf("b" to "2026-09-30T10:00:00Z"))
            val h = harness(service)
            h.controller.check(listOf(ready, ready2, pending))
            h.controller.check(listOf(ready, ready2, pending))
            advanceUntilIdle()
            h.controller.check(listOf(ready, ready2, pending))
            advanceUntilIdle()
            // No local shortcut: even a drop whose original is on this device asks the server.
            assertEquals(listOf(listOf("a", "b")), service.viewCalls)
            val book = h.controller.book.value
            assertEquals(ClickDropDevelopState.Ready, book.state(ready, now))
            assertEquals(ClickDropDevelopState.Developed, book.state(ready2, now))
            assertTrue(book.fresh.isEmpty())
            assertTrue(h.developed.isEmpty())
        }

    @Test
    fun aFailedBatchOnlyPutsItsOwnDropsBackToReady() =
        runTest {
            val service = FakeDevelopService()
            val drops = (1..60).map { ClickDropRevealRef("d$it", now - 60.seconds) }
            val ok = service.developResult
            service.developResult = { ids -> if (service.developCalls.size == 1) ok(ids) else Result.failure(Exception("429")) }
            val h = harness(service)
            h.controller.develop(drops)
            advanceUntilIdle()
            assertEquals(listOf(50, 10), service.developCalls.map { it.size })
            val book = h.controller.book.value
            assertEquals(50, book.developedAt.size)
            assertEquals(drops.drop(50), book.ready(drops, now))
            assertTrue(book.developing.isEmpty())
            assertEquals(50, h.developed.size)
            assertEquals(listOf(CLICK_DROP_DEVELOP_FAILED), h.errors)
        }

    @Test
    fun aFailedCheckIsAskedAgainNextTime() =
        runTest {
            val service = FakeDevelopService()
            service.viewsResult = Result.failure(Exception("offline"))
            val h = harness(service)
            h.controller.check(listOf(ready))
            advanceUntilIdle()
            h.controller.check(listOf(ready))
            advanceUntilIdle()
            assertEquals(2, service.viewCalls.size)
        }

    @Test
    fun shownAndClearResetTheBook() =
        runTest {
            val h = harness(FakeDevelopService())
            h.controller.develop(listOf(ready))
            advanceUntilIdle()
            h.controller.shown("a")
            assertTrue(
                h.controller.book.value.fresh
                    .isEmpty(),
            )
            h.controller.clear()
            assertTrue(
                h.controller.book.value.developedAt
                    .isEmpty(),
            )
        }

    @Test
    fun onlyPersistedGatedDropsJoinTheDevelopFlow() {
        val id = "11111111-1111-4111-8111-111111111111"
        val metadata =
            buildJsonObject {
                put("disposable_roll", JsonPrimitive(true))
                put("drop_gated", JsonPrimitive(true))
                put("reveal_at", JsonPrimitive("2026-10-01T12:00:00Z"))
            }
        val gated = Message(id = id, user_id = "u", content = "", timeCreated = 0L, messageType = "image", metadata = metadata)
        assertEquals(ClickDropRevealRef(id, now), gated.clickDropRevealRefOrNull())
        assertNull(gated.copy(id = "temp-img-1").clickDropRevealRefOrNull())
        assertNull(gated.copy(metadata = null).clickDropRevealRefOrNull())
    }

    private fun legacyDrop(
        id: String = "22222222-2222-4222-8222-222222222222",
        revealAt: String = "2026-10-01T11:00:00Z",
    ): Message =
        Message(
            id = id,
            user_id = "u",
            content = "",
            timeCreated = 0L,
            messageType = "image",
            metadata =
                buildJsonObject {
                    put("disposable_roll", JsonPrimitive(true))
                    put("collaboration_ttl", JsonPrimitive(revealAt))
                },
        )

    @Test
    fun legacyDropsJoinTheDevelopFlowOnlyWhenDropsDevelopIsOn() {
        val legacy = legacyDrop()
        assertNull(legacy.clickDropRevealRefOrNull(developFlowEnabled = false))
        assertFalse(legacy.usesClickDropDevelopFlow(developFlowEnabled = false))
        assertEquals(ClickDropRevealRef(legacy.id, now - 3600.seconds), legacy.clickDropRevealRefOrNull(developFlowEnabled = true))
        // Only photos, and never an optimistic copy.
        assertNull(legacy.copy(messageType = "video").clickDropRevealRefOrNull(developFlowEnabled = true))
        assertNull(legacy.copy(id = "temp-img-2").clickDropRevealRefOrNull(developFlowEnabled = true))
        assertNull(legacy.copy(metadata = null).clickDropRevealRefOrNull(developFlowEnabled = true))
    }

    @Test
    fun gatedDropsStayInTheDevelopFlowWithTheFlagOff() {
        val gated =
            legacyDrop().copy(
                metadata =
                    buildJsonObject {
                        put("disposable_roll", JsonPrimitive(true))
                        put("drop_gated", JsonPrimitive(true))
                        put("reveal_at", JsonPrimitive("2026-10-01T11:00:00Z"))
                    },
            )
        assertTrue(gated.usesClickDropDevelopFlow(developFlowEnabled = false))
        // Gated drops wait for their original instead, so they are never "legacy awaiting develop".
        assertFalse(gated.isLegacyDropAwaitingDevelop(ClickDropDevelopBook(), developFlowEnabled = true))
    }

    @Test
    fun aLegacyDropStaysLockedUntilTheServerSaysItIsDeveloped() =
        runTest {
            val legacy = legacyDrop()
            assertFalse(legacy.isLegacyDropAwaitingDevelop(ClickDropDevelopBook(), developFlowEnabled = false))
            assertTrue(legacy.isLegacyDropAwaitingDevelop(ClickDropDevelopBook(), developFlowEnabled = true))
            // Develops through the same server call as gated drops; there is no signed original.
            val service = FakeDevelopService()
            service.developResult = { ids -> Result.success(ids.map { DropDevelopResultDto("chat", it, "developed", url = null) }) }
            val h = harness(service)
            val ref = assertNotNull(legacy.clickDropRevealRefOrNull(developFlowEnabled = true))
            h.controller.develop(listOf(ref))
            advanceUntilIdle()
            assertEquals(listOf(listOf(legacy.id)), service.developCalls)
            assertEquals(listOf<Pair<String, String?>>(legacy.id to null), h.developed)
            assertEquals(setOf(legacy.id), h.controller.book.value.fresh)
            assertFalse(legacy.isLegacyDropAwaitingDevelop(h.controller.book.value, developFlowEnabled = true))
        }
}
