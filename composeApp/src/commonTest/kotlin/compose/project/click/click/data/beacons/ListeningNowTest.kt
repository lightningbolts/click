package compose.project.click.click.data.beacons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListeningNowTest {
    private fun people(vararg names: String) = names.mapIndexed { i, n -> ListeningPerson("u$i", n) }

    private fun now(
        count: Int,
        me: Boolean,
        vararg names: String,
    ) = ListeningNow(count, me, people(*names), 360)

    @Test
    fun parsesServerShape() {
        // click-web lib/server/soundtrackPresence.ts loadListening
        val l =
            ListeningNow.parse(
                Json
                    .parseToJsonElement(
                        """{"count":4,"is_listening":true,"connections":[{"user_id":"a","name":"Maya","avatar_url":null}],"heartbeat_seconds":30}""",
                    ).jsonObject,
            )
        assertEquals(4, l.count)
        assertEquals(listOf(ListeningPerson("a", "Maya")), l.connections)
        assertEquals(60, l.heartbeatSeconds, "never faster than once a minute")
        assertEquals(360, ListeningNow.parse(Json.parseToJsonElement("{}").jsonObject).heartbeatSeconds)
    }

    @Test
    fun summariesMatchIos() {
        assertNull(now(0, false).summary)
        assertEquals("You're listening here", now(1, true).summary)
        assertEquals("3 listening now", now(3, false).summary)
        assertEquals("Maya is listening", now(1, false, "Maya").summary)
        assertEquals("Maya is listening", now(2, true, "Maya").summary, "you aren't counted as an other")
        assertEquals("Maya and Sam are listening", now(2, false, "Maya", "Sam").summary)
        assertEquals("Maya and 3 others are listening", now(4, false, "Maya").summary)
        assertEquals("Maya, Sam and 1 other are listening", now(3, false, "Maya", "Sam").summary)
    }
}
