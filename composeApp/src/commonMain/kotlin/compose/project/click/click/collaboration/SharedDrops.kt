package compose.project.click.click.collaboration

import compose.project.click.click.data.api.ReactionsState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Shared Click Drops (spec F3, flag `shared_drops`): one photo to all your connections or only core
 * ones, developing `develop_hours` after it's posted, like a story. Not end-to-end encrypted like
 * chat drops; the server checks the audience on every read. Mirrors iOS `SharedDrops.swift`.
 */

enum class SharedDropAudience(
    val wire: String,
) {
    ALL("all"),
    CORE("core"),
}

@Serializable
data class SharedDropUser(
    val id: String = "",
    val name: String = "Someone",
    @SerialName("avatar_url") val avatarUrl: String? = null,
) {
    val firstName: String get() = name.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() } ?: name
}

/** One drop as `GET /api/me/shared-drops` (and the archive) sends it. */
@Serializable
data class SharedDrop(
    val id: String,
    val user: SharedDropUser = SharedDropUser(),
    @SerialName("is_mine") val isMine: Boolean = false,
    /** Only on your own drops. */
    val audience: String? = null,
    /** The connection to reply in (others' drops). */
    @SerialName("connection_id") val connectionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reveal_at") val revealAtIso: String? = null,
    @SerialName("developed_at") val developedAtIso: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    /** Signed, short-lived; only once this viewer has developed it. */
    @SerialName("original_url") val originalUrl: String? = null,
    /** Inline once developed, so the viewer opens filled. */
    val reactions: ReactionsState? = null,
    /** Locket-style; others get it only once the drop develops. */
    val caption: String? = null,
) {
    val userId: String get() = user.id
    val revealAt: Instant? get() = parseInstantOrNull(revealAtIso)
    val developedAt: Instant? get() = parseInstantOrNull(developedAtIso)

    fun state(now: Instant = Clock.System.now()): ClickDropDevelopState = ClickDropDevelopState.resolve(revealAt, developedAt, now)

    companion object {
        /** Captions are capped at this many characters, as people count them. */
        const val CAPTION_LIMIT = 100
    }
}

/** A caption fits when it has at most [SharedDrop.CAPTION_LIMIT] grapheme clusters. */
fun sharedDropCaptionFits(caption: String): Boolean = graphemeCount(caption.trim()) <= SharedDrop.CAPTION_LIMIT

/**
 * One person's drops on the Home strip: a single tile that opens the viewer on their drops in
 * order, then carries on to the next person.
 */
data class SharedDropGroup(
    val userId: String,
    /** Oldest first: the order the story plays them. */
    val drops: List<SharedDrop>,
) {
    val isMine: Boolean get() = drops.firstOrNull()?.isMine ?: false
    val newest: SharedDrop get() = drops.last()

    fun viewable(now: Instant = Clock.System.now()): List<SharedDrop> = drops.filter { !it.state(now).isPending }

    /** Where the story starts: the first drop still to develop (unseen), else the first one. */
    fun start(now: Instant = Clock.System.now()): SharedDrop? {
        val viewable = viewable(now)
        return viewable.firstOrNull { it.state(now) == ClickDropDevelopState.Ready } ?: viewable.firstOrNull()
    }

    /** The tile's face: the drop the story opens on (the newest, a countdown, while none can open). */
    fun cover(now: Instant = Clock.System.now()): SharedDrop = start(now) ?: newest

    /** Has a developed drop you haven't watched yet (the tile's ready ring). */
    fun hasUnwatched(now: Instant = Clock.System.now()): Boolean = drops.any { it.state(now) == ClickDropDevelopState.Ready }

    companion object {
        /**
         * Yours first, then people with drops you haven't watched, then everyone else, each by their
         * newest drop. [list] is the strip, newest first, as the server sends it.
         */
        fun group(
            list: List<SharedDrop>,
            now: Instant = Clock.System.now(),
        ): List<SharedDropGroup> {
            val order = mutableListOf<String>()
            val byUser = mutableMapOf<String, MutableList<SharedDrop>>()
            for (drop in list) {
                if (drop.userId !in byUser) order += drop.userId
                byUser.getOrPut(drop.userId) { mutableListOf() } += drop
            }
            val groups = order.map { SharedDropGroup(it, byUser.getValue(it).reversed()) }
            val others = groups.filterNot { it.isMine }
            return groups.filter { it.isMine } + others.filter { it.hasUnwatched(now) } + others.filterNot { it.hasUnwatched(now) }
        }
    }
}

/** A page of the drop archive and where the next one starts (null at the end). */
@Serializable
data class SharedDropArchivePage(
    val drops: List<SharedDrop> = emptyList(),
    @SerialName("next_before") val nextBefore: String? = null,
)
