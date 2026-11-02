package compose.project.click.click.collaboration

import androidx.compose.animation.core.CubicBezierEasing
import compose.project.click.click.data.api.DropDevelopResultDto
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A chat Click Drop in the develop flow: its message id and reveal time (null when unknown). */
data class ClickDropRevealRef(
    val id: String,
    val revealAt: Instant?,
)

/**
 * This viewer's develop progress for chat Click Drops. Mirrors the develop half of iOS
 * `Click/Features/Chat/ConversationModel.swift` (`dropDevelopedAt`, `developingDropIDs`,
 * `freshlyDevelopedDropIDs`). The server decides when a drop is developed (`POST /api/drops/develop`,
 * `GET /api/drops/views`); this only records its answers so the bubbles can render the state.
 */
data class ClickDropDevelopBook(
    /** When this viewer developed each drop (on any device). */
    val developedAt: Map<String, Instant> = emptyMap(),
    /** Drops with a develop request in flight. */
    val developing: Set<String> = emptySet(),
    /** Developed on this screen just now: the bubble plays the bloom once, then calls [shown]. */
    val fresh: Set<String> = emptySet(),
    /** Revealed drops whose developed-at has been asked for already (asked once per drop). */
    val checked: Set<String> = emptySet(),
) {
    fun state(
        drop: ClickDropRevealRef,
        now: Instant,
    ): ClickDropDevelopState = ClickDropDevelopState.resolve(drop.revealAt, developedAt[drop.id], now)

    fun isDeveloped(id: String): Boolean = id in developedAt

    /** Ready drops (revealed, not developed, not in flight), in the order given ("Develop all"). */
    fun ready(
        drops: List<ClickDropRevealRef>,
        now: Instant,
    ): List<ClickDropRevealRef> = drops.filter { state(it, now) == ClickDropDevelopState.Ready && it.id !in developing }

    /**
     * Live develop: ready drops that reached zero within [window] while on screen. One opened later
     * stays ready and waits for a tap (iOS `developDropsRevealedWhileVisible`).
     */
    fun justRevealed(
        drops: List<ClickDropRevealRef>,
        now: Instant,
        window: Duration = LIVE_DEVELOP_WINDOW,
    ): List<ClickDropRevealRef> = ready(drops, now).filter { drop -> drop.revealAt?.let { now - it < window } == true }

    /** Revealed drops whose developed-at this viewer hasn't asked the server for yet. */
    fun needingCheck(
        drops: List<ClickDropRevealRef>,
        now: Instant,
    ): List<ClickDropRevealRef> = drops.filter { state(it, now) == ClickDropDevelopState.Ready && it.id !in checked }

    fun startDeveloping(ids: Collection<String>): ClickDropDevelopBook = copy(developing = developing + ids)

    /**
     * Applies `POST /api/drops/develop` answers for [requested]. Developed drops become fresh (they
     * bloom); pending and not-found drops stay as they were. The first developed-at wins.
     */
    fun applyDevelopResults(
        requested: Collection<String>,
        results: List<DropDevelopResultDto>,
        now: Instant,
    ): ClickDropDevelopBook {
        val developed =
            results
                .filter { it.status == DEVELOP_STATUS_DEVELOPED && it.id in requested }
                .associate { it.id to (parseInstantOrNull(it.developedAt) ?: now) }
        return copy(
            developedAt = developed + developedAt,
            developing = developing - requested.toSet(),
            fresh = fresh + developed.keys.filter { it !in developedAt },
        )
    }

    fun failDeveloping(requested: Collection<String>): ClickDropDevelopBook = copy(developing = developing - requested.toSet())

    /** Merges `GET /api/drops/views` (developed elsewhere, or earlier): no bloom for these. */
    fun mergeViews(
        asked: Collection<String>,
        developed: Map<String, Instant>,
    ): ClickDropDevelopBook = copy(developedAt = developedAt + developed.filterKeys { it !in developedAt }, checked = checked + asked)

    /** Developed before, on this device (the original is in the vault): no network and no bloom. */
    fun markDevelopedLocally(
        id: String,
        at: Instant,
    ): ClickDropDevelopBook = if (id in developedAt) this else copy(developedAt = developedAt + (id to at), checked = checked + id)

    /** A failed check may be retried on the next look. */
    fun uncheck(ids: Collection<String>): ClickDropDevelopBook = copy(checked = checked - ids.toSet())

    fun shown(id: String): ClickDropDevelopBook = if (id in fresh) copy(fresh = fresh - id) else this

    companion object {
        const val DEVELOP_STATUS_DEVELOPED = "developed"

        /** iOS `developDropsRevealedWhileVisible`: revealed less than 5 s ago. */
        val LIVE_DEVELOP_WINDOW: Duration = 5.seconds
    }
}

/** "Develop all (3)" when more than one drop waits (iOS `ChatView` overlay); null otherwise. */
fun clickDropDevelopAllLabel(readyCount: Int): String? = if (readyCount > 1) "Develop all ($readyCount)" else null

/**
 * How long until the next pending drop in [drops] reveals (plus a little grace), so a screen can
 * re-check right on time; [idleMs] when none is pending.
 */
fun clickDropNextRevealDelayMs(
    drops: List<ClickDropRevealRef>,
    now: Instant,
    idleMs: Long = 60_000L,
    graceMs: Long = 500L,
): Long {
    val next = drops.mapNotNull { it.revealAt }.filter { it > now }.minOrNull() ?: return idleMs
    return ((next - now).inWholeMilliseconds + graceMs).coerceIn(graceMs, idleMs)
}

/**
 * Timing of the develop bloom (iOS `ClickDropDevelopingImage`): finer layers (24, 48, 96 blocks,
 * then the photo) each grow from the centre over [BLOOM_MS] with an ease-out, starting [STAGGER_MS]
 * after the one before, over the coarse 12-block base.
 */
object ClickDropBloom {
    /** Blocks per longest side of each pixel layer, coarse to fine. */
    val LAYER_BLOCKS: List<Int> = listOf(12, 24, 48, 96)
    const val STAGGER_MS: Int = 140
    const val BLOOM_MS: Int = 500

    /** The stages that bloom over the base: three finer pixel layers and the photo. */
    val STAGE_COUNT: Int = LAYER_BLOCKS.size

    /** Start to the photo fully in place (0.92 s, as on iOS). */
    val DURATION_MS: Int = STAGGER_MS * (STAGE_COUNT - 1) + BLOOM_MS

    /** Reduce Motion: a quick fade instead. */
    const val REDUCED_FADE_MS: Int = 300

    /** The soft edge starts at this fraction of the mask's radius (iOS gradient stop 0.75). */
    const val SOFT_EDGE_START: Float = 0.75f

    /** The mask grows to this much of the frame's diagonal so its soft edge clears the corners. */
    const val DIAMETER_OF_DIAGONAL: Float = 1.4f

    // SwiftUI `.easeOut`.
    private val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    /** 0..1 scale of stage [index]'s circular mask [elapsedMs] after the develop starts. */
    fun stageScale(
        index: Int,
        elapsedMs: Long,
    ): Float {
        val local = elapsedMs - index.toLong() * STAGGER_MS
        if (local <= 0) return 0f
        if (local >= BLOOM_MS) return 1f
        return easeOut.transform(local.toFloat() / BLOOM_MS).coerceIn(0f, 1f)
    }

    /** Mask radius in px for stage [index] in a [width] x [height] frame. */
    fun stageRadius(
        index: Int,
        elapsedMs: Long,
        width: Float,
        height: Float,
    ): Float {
        val diagonal = kotlin.math.sqrt(width * width + height * height)
        return diagonal * DIAMETER_OF_DIAGONAL / 2f * stageScale(index, elapsedMs)
    }

    fun isFinished(elapsedMs: Long): Boolean = elapsedMs >= DURATION_MS
}
