package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventAttendeeDirectoryTest {
    private fun attendee(
        id: String,
        name: String,
        interest: Int = 0,
        distance: Double? = null,
        mutualCount: Int = 0,
        relationship: AttendeeRelationship = AttendeeRelationship.Stranger,
        via: List<MutualViaPeer> = emptyList(),
    ) = DirectoryAttendee(
        userId = id,
        name = name,
        sharedInterestCount = interest,
        distanceMeters = distance,
        mutualConnectionCount = mutualCount,
        relationship = relationship,
        mutualVia = via,
    )

    @Test
    fun sortAlphabetical() {
        val sorted = sortEventAttendees(
            listOf(attendee("2", "Zoe"), attendee("1", "amy"), attendee("3", "Bob")),
            EventAttendeeSortMode.Alphabetical,
        )
        assertEquals(listOf("amy", "Bob", "Zoe"), sorted.map { it.name })
    }

    @Test
    fun sortInterestOverlap() {
        val sorted = sortEventAttendees(
            listOf(
                attendee("a", "A", interest = 1),
                attendee("b", "B", interest = 3),
                attendee("c", "C", interest = 2),
            ),
            EventAttendeeSortMode.InterestOverlap,
        )
        assertEquals(listOf("B", "C", "A"), sorted.map { it.name })
    }

    @Test
    fun sortRsvpDistance_nullsLast() {
        val sorted = sortEventAttendees(
            listOf(
                attendee("a", "Far", distance = 900.0),
                attendee("b", "Unknown", distance = null),
                attendee("c", "Near", distance = 50.0),
            ),
            EventAttendeeSortMode.RsvpDistance,
        )
        assertEquals(listOf("Near", "Far", "Unknown"), sorted.map { it.name })
    }

    @Test
    fun sortMutualConnections() {
        val sorted = sortEventAttendees(
            listOf(
                attendee("a", "A", mutualCount = 1),
                attendee("b", "B", mutualCount = 4),
            ),
            EventAttendeeSortMode.MutualConnections,
        )
        assertEquals(listOf("B", "A"), sorted.map { it.name })
    }

    @Test
    fun mutualSubtitleAndConnectGate() {
        val mutual = attendee(
            "m",
            "Morgan",
            relationship = AttendeeRelationship.Mutual,
            via = listOf(MutualViaPeer("s", "Sam"), MutualViaPeer("j", "Jordan")),
        )
        assertEquals("Mutual · via Sam, Jordan", relationshipSubtitle(mutual))
        assertFalse(allowsDirectoryConnectActions(AttendeeRelationship.Mutual))
        assertFalse(allowsDirectoryConnectActions(AttendeeRelationship.Stranger))
        assertTrue(allowsDirectoryConnectActions(AttendeeRelationship.Connection))
    }
}
