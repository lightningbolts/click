package compose.project.click.click.collaboration

import compose.project.click.click.data.api.ClickDropService
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Message
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Mirrors iOS `ClickDropDevelopTests` (develop all, failure, flag, live develop). */
class ClickDropDevelopControllerTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private class FakeService(
        var fail: Boolean = false,
    ) : ClickDropService {
        val developCalls = mutableListOf<List<String>>()

        override suspend fun develop(refs: List<ClickDropRef>): List<ClickDropDevelopResult> {
            developCalls += refs.map { it.id }
            if (fail) error("500")
            return refs.map {
                ClickDropDevelopResult(
                    it,
                    ClickDropDevelopResult.Status.DEVELOPED,
                    Instant.parse("2026-10-01T12:00:01Z"),
                    "https://signed.example/${it.id}",
                )
            }
        }

        override suspend fun developedAt(
            kind: ClickDropKind,
            ids: List<String>,
        ): Map<String, Instant> = ids.filter { it == "seen" }.associateWith { Instant.parse("2026-09-30T00:00:00Z") }
    }

    private fun drop(
        id: String,
        revealIn: Duration,
        gated: Boolean,
    ): Message {
        val gate = if (gated) ""","drop_gated":true""" else ""
        return Message(
            id = id,
            user_id = "peer",
            content = " ",
            timeCreated = 0L,
            messageType = ChatMessageType.IMAGE,
            metadata =
                Json.parseToJsonElement(
                    """{"media_url":"https://signed.example/$id","disposable_roll":true,"reveal_at":"${now + revealIn}"$gate}""",
                ),
        )
    }

    private fun controller(
        service: ClickDropService,
        flagOn: Boolean,
    ) = ClickDropDevelopController(service, isDevelopFlagOn = { flagOn }, clock = { now })

    @Test
    fun legacyDropsKeepDevelopingOnTheirOwnWhenTheFlagIsOff() {
        val item = drop("m1", (-60).seconds, gated = false)
        assertNull(controller(FakeService(), flagOn = false).state(item))
        assertEquals(ClickDropDevelopState.Ready, controller(FakeService(), flagOn = true).state(item))
    }

    @Test
    fun gatedDropsAlwaysUseTapToDevelop() {
        val item = drop("m1", (-60).seconds, gated = true)
        assertEquals(ClickDropDevelopState.Ready, controller(FakeService(), flagOn = false).state(item))
    }

    @Test
    fun developAllDevelopsOnlyReadyDrops() =
        runTest {
            val service = FakeService()
            val drops = controller(service, flagOn = true)
            val ready = drop("m1", (-60).seconds, gated = true)
            val alsoReady = drop("m2", (-30).seconds, gated = true)
            val pending = drop("m3", 1.hours, gated = true)
            val items = listOf(ready, alsoReady, pending)
            assertEquals(listOf("m1", "m2"), drops.readyDrops(items).map { it.id })

            drops.develop(items)

            assertEquals(listOf(listOf("m1", "m2")), service.developCalls)
            assertEquals(ClickDropDevelopState.Developed, drops.state(ready))
            assertEquals(ClickDropDevelopState.Developed, drops.state(alsoReady))
            assertTrue(drops.state(pending)!!.isPending)
            assertTrue(drops.readyDrops(items).isEmpty())
            assertEquals(setOf("m1", "m2"), drops.freshlyDeveloped.value)
            assertEquals("https://signed.example/m1", drops.signedOriginalUrl("m1"))
        }

    @Test
    fun aFailedDevelopLeavesDropsReadyAndSaysSoCalmly() =
        runTest {
            val drops = controller(FakeService(fail = true), flagOn = true)
            val item = drop("m1", (-60).seconds, gated = true)
            drops.develop(listOf(item))
            assertEquals(ClickDropDevelopState.Ready, drops.state(item))
            assertNotNull(drops.error.value)
            assertTrue(drops.developing.value.isEmpty())
        }

    @Test
    fun developedStateSyncsFromTheServer() =
        runTest {
            val drops = controller(FakeService(), flagOn = false)
            val seen = drop("seen", (-1).hours, gated = true)
            drops.refresh(listOf(seen, drop("other", (-1).hours, gated = true)))
            assertEquals(ClickDropDevelopState.Developed, drops.state(seen))
            assertEquals(setOf("seen"), drops.developedAt.value.keys)
        }

    @Test
    fun liveDevelopOnlyTakesDropsThatJustReachedZero() =
        runTest {
            val service = FakeService()
            val drops = controller(service, flagOn = true)
            val justNow = drop("now", (-2).seconds, gated = true)
            val earlier = drop("old", (-1).hours, gated = true)
            assertTrue(drops.developRevealedWhileVisible(listOf(justNow, earlier)))
            assertEquals(listOf(listOf("now")), service.developCalls)
            assertEquals(ClickDropDevelopState.Ready, drops.state(earlier))
        }

    @Test
    fun aPendingOptimisticSendStaysPending() {
        val item =
            drop(
                "temp-1",
                (-60).seconds,
                gated = true,
            ).copy(deliveryState = compose.project.click.click.data.models.MessageDeliveryState.PENDING)
        assertTrue(controller(FakeService(), flagOn = true).state(item)!!.isPending)
    }
}
