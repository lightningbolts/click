package compose.project.click.click.events // pragma: allowlist secret

data class NudgeCopy(
    val title: String,
    val body: String,
)

fun teaserHeadline(
    count: Int,
    label: String,
    sharedTag: String? = null,
): String {
    val n = count.coerceAtLeast(1)
    val people = if (n == 1) "person" else "people"
    val verb = if (n == 1) "is" else "are"
    return when (label) {
        "interest" -> {
            val tag = sharedTag?.trim()?.takeIf { it.isNotEmpty() }
            if (tag != null) {
                "$n $people going who share your interest in $tag"
            } else {
                "$n $people going who share an interest"
            }
        }
        "org" -> "$n $people going from a group you are in"
        else -> "$n $people you know $verb going"
    }
}

fun reconnectNudgeCopy(
    peerFirstName: String,
    daysSinceEncounter: Int,
): NudgeCopy {
    val name = peerFirstName.trim().ifEmpty { "a connection" }
    val days = daysSinceEncounter.coerceAtLeast(1)
    val whenLabel = if (days == 1) "yesterday" else "$days days ago"
    return NudgeCopy(
        title = "Check in with $name",
        body = "You and $name haven't talked since you Clicked $whenLabel.",
    )
}

fun sharedEventNudgeCopy(
    peerFirstName: String,
    eventTitle: String,
): NudgeCopy {
    val name = peerFirstName.trim().ifEmpty { "a connection" }
    val event = eventTitle.trim().ifEmpty { "an upcoming event" }
    return NudgeCopy(
        title = "$name is going too",
        body = "You and $name are both going to $event.",
    )
}
