package compose.project.click.click.collaboration

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/*
 * Home's "View all" archive of shared drops (flag `shared_drops`): every drop you can see, newest
 * first, in a photo grid under day headers. Mirrors iOS `Click/Features/Home/SharedDropsArchiveView.swift`.
 */

/** One header of the archive grid and its drops, newest first. */
data class SharedDropArchiveSection(
    val title: String,
    val drops: List<SharedDrop>,
)

private val MONTH_NAMES =
    listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

/** Today, Yesterday, This week, This month, then a header per month ("August 2026"). */
fun sharedDropArchiveSectionTitle(
    createdAt: Instant,
    now: Instant,
    zone: TimeZone,
): String {
    val date = createdAt.toLocalDateTime(zone).date
    val today = now.toLocalDateTime(zone).date
    return when {
        date == today -> "Today"
        date == today.minus(DatePeriod(days = 1)) -> "Yesterday"
        now - createdAt < 7.days -> "This week"
        date.year == today.year && date.monthNumber == today.monthNumber -> "This month"
        else -> "${MONTH_NAMES[date.monthNumber - 1]} ${date.year}"
    }
}

/** Groups [drops] (newest first, as the server sends them) under consecutive headers. */
fun sharedDropArchiveSections(
    drops: List<SharedDrop>,
    now: Instant,
    zone: TimeZone,
): List<SharedDropArchiveSection> {
    val out = mutableListOf<SharedDropArchiveSection>()
    for (drop in drops) {
        val title = sharedDropArchiveSectionTitle(parseInstantOrNull(drop.createdAt) ?: now, now, zone)
        val last = out.lastOrNull()
        if (last?.title == title) {
            out[out.lastIndex] = last.copy(drops = last.drops + drop)
        } else {
            out += SharedDropArchiveSection(title, listOf(drop))
        }
    }
    return out
}

/**
 * The archive played as one story, in grid order (iOS `StoryPlaylist.archive`): drops still
 * developing are left out, as they are on the strip.
 */
fun archiveStoryGroup(drops: List<SharedDrop>): SharedDropGroup = SharedDropGroup(userId = ARCHIVE_STORY_ID, drops = drops)

const val ARCHIVE_STORY_ID = "shared-drops-archive"
