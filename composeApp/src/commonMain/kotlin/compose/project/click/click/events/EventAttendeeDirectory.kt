package compose.project.click.click.events

/**
 * Pure sort / relationship helpers for the event people directory.
 * Enrichment comes from GET /api/beacons/{id}/attendees/directory.
 */

enum class AttendeeRelationship {
    Self,
    Connection,
    Mutual,
    Stranger,
    ;

    companion object {
        fun fromApi(raw: String?): AttendeeRelationship = when (raw?.trim()?.lowercase()) {
            "self" -> Self
            "connection" -> Connection
            "mutual" -> Mutual
            else -> Stranger
        }
    }
}

enum class EventAttendeeSortMode {
    Alphabetical,
    InterestOverlap,
    MutualConnections,
}

data class MutualViaPeer(
    val userId: String,
    val name: String,
)

data class DirectoryAttendee(
    val userId: String,
    val name: String,
    val avatarUrl: String? = null,
    val signedUpAt: String? = null,
    val distanceMeters: Double? = null,
    val sharedInterests: List<String> = emptyList(),
    val sharedInterestCount: Int = 0,
    val relationship: AttendeeRelationship = AttendeeRelationship.Stranger,
    val mutualVia: List<MutualViaPeer> = emptyList(),
    val mutualConnectionCount: Int = 0,
)

fun sortEventAttendees(
    attendees: List<DirectoryAttendee>,
    mode: EventAttendeeSortMode,
): List<DirectoryAttendee> = when (mode) {
    EventAttendeeSortMode.Alphabetical ->
        attendees.sortedWith(
            compareBy<DirectoryAttendee, String>(String.CASE_INSENSITIVE_ORDER) {
                it.name.trim().ifBlank { "Attendee" }
            }.thenBy { it.userId },
        )
    EventAttendeeSortMode.InterestOverlap ->
        attendees.sortedWith(
            compareByDescending<DirectoryAttendee> { it.sharedInterestCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.userId },
        )
    EventAttendeeSortMode.MutualConnections ->
        attendees.sortedWith(
            compareByDescending<DirectoryAttendee> { it.mutualConnectionCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.userId },
        )
}

fun mutualsAtEvent(attendees: List<DirectoryAttendee>): List<DirectoryAttendee> =
    attendees.filter { it.relationship == AttendeeRelationship.Mutual }

/** Everyone list when Mutuals section is shown — avoid listing FoF twice. */
fun everyoneExcludingMutualsSection(attendees: List<DirectoryAttendee>): List<DirectoryAttendee> =
    attendees.filter { it.relationship != AttendeeRelationship.Mutual }

fun mutualCountLabel(count: Int): String = when {
    count <= 0 -> ""
    count == 1 -> "1 mutual"
    else -> "$count mutuals"
}

fun relationshipSubtitle(attendee: DirectoryAttendee): String? = when (attendee.relationship) {
    AttendeeRelationship.Self -> "You"
    AttendeeRelationship.Connection -> {
        val count = attendee.mutualConnectionCount
        if (count > 0) mutualCountLabel(count) else "Connection"
    }
    AttendeeRelationship.Mutual -> {
        val countLabel = mutualCountLabel(attendee.mutualConnectionCount)
        val via = attendee.mutualVia.map { it.name.trim() }.filter { it.isNotEmpty() }
        when {
            countLabel.isNotEmpty() && via.isNotEmpty() ->
                "$countLabel · via ${via.take(2).joinToString(", ")}"
            countLabel.isNotEmpty() -> countLabel
            via.isNotEmpty() -> "Mutual · via ${via.take(2).joinToString(", ")}"
            else -> "Mutual"
        }
    }
    AttendeeRelationship.Stranger -> null
}

/**
 * Primary metric line under the attendee name for the active directory sort chip.
 * Keeps one density-matched line instead of always advertising shared interests.
 */
fun directorySortMetricSubtitle(
    attendee: DirectoryAttendee,
    mode: EventAttendeeSortMode,
): String? = when (mode) {
    EventAttendeeSortMode.Alphabetical -> relationshipSubtitle(attendee)
    EventAttendeeSortMode.InterestOverlap -> {
        val count = attendee.sharedInterestCount
        when {
            count <= 0 -> relationshipSubtitle(attendee)
            count == 1 -> "1 shared interest"
            else -> "$count shared interests"
        }
    }
    EventAttendeeSortMode.MutualConnections -> {
        val count = attendee.mutualConnectionCount
        when {
            count > 0 -> {
                val via = attendee.mutualVia.map { it.name.trim() }.filter { it.isNotEmpty() }
                val countLabel = mutualCountLabel(count)
                if (attendee.relationship == AttendeeRelationship.Mutual && via.isNotEmpty()) {
                    "$countLabel · via ${via.take(2).joinToString(", ")}"
                } else {
                    countLabel
                }
            }
            else -> relationshipSubtitle(attendee)
        }
    }
}

/** Profile / connect CTAs: only direct connections get connection actions from the directory. */
fun allowsDirectoryConnectActions(relationship: AttendeeRelationship): Boolean =
    relationship == AttendeeRelationship.Connection
