package compose.project.click.click

import compose.project.click.click.data.chat.PendingSend
import compose.project.click.click.data.chat.PendingSendStore
import compose.project.click.click.data.chat.ftsQuery
import compose.project.click.click.data.chat.storableText
import compose.project.click.click.data.chat.toStored
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageDeliveryState
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import compose.project.click.click.revealBeforeTags
import compose.project.click.click.ui.chat.liftedStackTop
import compose.project.click.click.ui.components.commonGroundInterests
import compose.project.click.click.ui.components.encounterTimelineTitle
import compose.project.click.click.ui.components.parseAuraHex
import compose.project.click.click.util.SoundtrackResolver
import compose.project.click.click.viewmodel.cliqueIneligibleReason
import compose.project.click.click.viewmodel.pendingSendRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParityRemainderTest {
    private fun pending(
        tempId: String,
        failed: Boolean = false,
        at: Long = 10,
    ) = PendingSend(
        tempId = tempId,
        userId = "me",
        threadKey = "conn",
        apiChatId = "chat",
        content = "hello $tempId",
        localSentAtMs = at,
        clientMessageId = "cid-$tempId",
        failed = failed,
    )

    @Test
    fun outboxRestoresMissingRowsWithState() {
        val me = User(id = "me", name = "Me")
        val shown = listOf(MessageWithUser(Message(id = "temp-a", user_id = "me", content = "x", timeCreated = 1), me, isSent = true))
        val rows = pendingSendRows(shown, listOf(pending("temp-a"), pending("temp-b"), pending("temp-c", failed = true)), me)
        assertEquals(listOf("temp-b", "temp-c"), rows.map { it.message.id })
        assertEquals(MessageDeliveryState.PENDING, rows[0].message.deliveryState)
        assertEquals(MessageDeliveryState.ERROR, rows[1].message.deliveryState)
        assertTrue(rows.all { it.isSent })
    }

    @Test
    fun outboxUpsertReplacesAndBounds() {
        val one = PendingSendStore.upserted(emptyList(), pending("temp-a"))
        val replaced = PendingSendStore.upserted(one, pending("temp-a", failed = true))
        assertEquals(1, replaced.size)
        assertTrue(replaced.single().failed)
        // The client message id survives a retry (same record).
        assertEquals("cid-temp-a", replaced.single().clientMessageId)
        val many = (1..5).fold(emptyList<PendingSend>()) { acc, i -> PendingSendStore.upserted(acc, pending("t$i"), max = 3) }
        assertEquals(listOf("t3", "t4", "t5"), many.map { it.tempId })
    }

    @Test
    fun cliqueReasonsNameWhoIsMissing() {
        assertNull(cliqueIneligibleReason(emptyList()))
        assertEquals("Hasn't Clicked with Lena", cliqueIneligibleReason(listOf("Lena")))
        assertEquals("Hasn't Clicked with Lena and 2 more", cliqueIneligibleReason(listOf("Sam", "Lena", "Priya")))
    }

    @Test
    fun encounterTitles() {
        assertEquals("First Clicked at Blue Bottle", encounterTimelineTitle("Blue Bottle", isFirst = true, tags = emptyList()))
        assertEquals("Reconnected at Pier 39", encounterTimelineTitle("Pier 39", isFirst = false, tags = listOf("Coffee")))
        assertEquals("Extended Hangout", encounterTimelineTitle(null, isFirst = false, tags = listOf("extended hangout")))
        assertEquals("Reconnected", encounterTimelineTitle(" ", isFirst = false, tags = emptyList()))
    }

    @Test
    fun commonGroundSharedFirstThenOthers() {
        val (shared, others) = commonGroundInterests(listOf("hiking", "Jazz"), listOf("Jazz", "Cooking", "Hiking", "jazz"))
        assertEquals(listOf("Jazz", "Hiking"), shared)
        assertEquals(listOf("Cooking"), others)
    }

    @Test
    fun revealOrderFollowsIosWhenTheConnectionExists() {
        assertTrue(revealBeforeTags(isNewConnection = true, requiresSelection = false, createdConnections = 1))
        assertFalse(revealBeforeTags(isNewConnection = true, requiresSelection = true, createdConnections = 0))
        assertFalse(revealBeforeTags(isNewConnection = false, requiresSelection = false, createdConnections = 1))
    }

    @Test
    fun liftedStackStaysOnScreen() {
        // Fits: the capsule sits right above the bubble.
        assertEquals(
            400f,
            liftedStackTop(
                bubbleTop = 500f,
                capsuleHeight = 100f,
                bubbleHeight = 80f,
                panelHeight = 300f,
                screenHeight = 2000f,
                margin = 50f,
            ),
        )
        // Near the bottom: pushed up so the panel fits.
        assertEquals(
            1470f,
            liftedStackTop(
                bubbleTop = 1900f,
                capsuleHeight = 100f,
                bubbleHeight = 80f,
                panelHeight = 300f,
                screenHeight = 2000f,
                margin = 50f,
            ),
        )
        // Near the top: never above the margin.
        assertEquals(
            50f,
            liftedStackTop(
                bubbleTop = 20f,
                capsuleHeight = 100f,
                bubbleHeight = 80f,
                panelHeight = 300f,
                screenHeight = 2000f,
                margin = 50f,
            ),
        )
    }

    @Test
    fun localStoreIndexesReadableTextOnly() {
        fun msg(
            content: String,
            type: String = "text",
            id: String = "m1",
        ) = Message(id = id, user_id = "u", content = content, timeCreated = 5, messageType = type)
        assertEquals("see you at 7", storableText(msg("see you at 7")))
        assertNull(storableText(msg("e2e2:abc")))
        assertNull(storableText(msg("ccx:v1:{}", type = "file")))
        assertNull(storableText(msg(" ", type = "image")))
        assertEquals("sunset", storableText(msg("sunset", type = "image")))
        assertNull(msg("hi", id = "temp-1").toStored("chat", "conn"))
        assertEquals("conn", msg("hi").toStored("chat", "conn")?.threadKey)
        assertEquals("\"cof\"* \"near\"*", ftsQuery("Cof  near!"))
        assertNull(ftsQuery("  ?! "))
    }

    @Test
    fun auraHexParsing() {
        assertEquals(0xFF112233L.toInt(), parseAuraHex("#112233")?.let { (it.value shr 32).toInt() })
        assertTrue(parseAuraHex("80FFFFFF") != null)
        assertNull(parseAuraHex("#12"))
        assertNull(parseAuraHex("zzzzzz"))
    }

    @Test
    fun soundtrackResolverHelpers() {
        assertEquals("1440818839", SoundtrackResolver.appleCatalogId("https://music.apple.com/us/album/x/1440818664?i=1440818839"))
        assertEquals("1440818664", SoundtrackResolver.appleCatalogId("https://music.apple.com/us/album/x/id1440818664"))
        assertEquals("dQw4w9WgXcQ", SoundtrackResolver.youtubeVideoId("https://youtu.be/dQw4w9WgXcQ?t=1"))
        assertEquals("dQw4w9WgXcQ", SoundtrackResolver.youtubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=x"))
        assertNull(SoundtrackResolver.youtubeVideoId("https://open.spotify.com/track/1"))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", SoundtrackResolver.linkThumbnail("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("Blinding Lights", SoundtrackResolver.spotifySongTitle("Blinding Lights - song and lyrics by The Weeknd | Spotify"))
        assertEquals(
            "The Weeknd Blinding Lights",
            SoundtrackResolver.spotifyTerm("Blinding Lights - song and lyrics by The Weeknd | Spotify"),
        )
        assertEquals("Song", SoundtrackResolver.cleaned("Song (Official Video)"))
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/a/600x600bb.jpg",
            SoundtrackResolver.artwork("https://is1-ssl.mzstatic.com/image/a/100x100bb.jpg"),
        )
        assertTrue(SoundtrackResolver.isTrustedPreview("https://audio-ssl.itunes.apple.com/p.m4a"))
        assertFalse(SoundtrackResolver.isTrustedPreview("https://evil.example/p.m4a"))
        assertFalse(SoundtrackResolver.isTrustedPreview("http://audio-ssl.itunes.apple.com/p.m4a"))
    }
}
