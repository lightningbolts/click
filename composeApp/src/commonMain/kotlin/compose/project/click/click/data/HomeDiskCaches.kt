package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.core.DiskFirstCache
import compose.project.click.click.core.UserDiskCache
import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.api.InboxNudgeDto // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** The Home modules that come from the network, as last seen (sheet 07 G1-5). */
@Serializable
data class HomeFeedSnapshot(
    val nudges: List<InboxNudgeDto> = emptyList(),
    @SerialName("availability_intents") val availabilityIntents: List<AvailabilityIntentRow> = emptyList(),
    @SerialName("availability_overlaps") val availabilityOverlaps: List<String> = emptyList(),
)

/**
 * The disk-first documents Home paints from on its first frame (sheet 07 G1-5; iOS `CacheStore`).
 * Per user, wiped at sign-out with the rest of [UserDiskCache]. RSVPs live in
 * [compose.project.click.click.data.storage.BeaconRsvpPersistence] under the same `events.rsvp_saved` prefix.
 */
object HomeDiskCaches {
    val homeFeed = DiskFirstCache(UserDiskCache.Keys.HOME_FEED, HomeFeedSnapshot.serializer())

    /** Saved events (bookmarks): [UserDiskCache.Keys.RSVP_SAVED] + `.saved`. */
    val savedEvents =
        DiskFirstCache("${UserDiskCache.Keys.RSVP_SAVED}.saved", ListSerializer(EventBookmarkItemDto.serializer())) {
            it.take(AppDataManager.SAVED_EVENT_BOOKMARKS_LIMIT)
        }
}
