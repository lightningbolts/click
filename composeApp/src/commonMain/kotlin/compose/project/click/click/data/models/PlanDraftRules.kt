package compose.project.click.click.data.models

import compose.project.click.click.data.storage.TokenStorage
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Planner rules shared with iOS `PlanHangoutSheet` (`Features/Chat/PlanViews.swift`). */
object PlanDraftRules {
    const val MIN_LENGTH_MS: Long = 15 * 60 * 1000L
    const val DEFAULT_LENGTH_MS: Long = 2 * 60 * 60 * 1000L
    const val CUSTOM_IDEA_MAX: Int = 40
    private const val TWO_HOURS_MS: Long = 2 * 60 * 60 * 1000L

    val defaultIdeas: List<String> = listOf("☕️ Coffee", "🍜 Dinner", "🍻 Drinks", "🚶 Walk", "🎬 Movie", "🏋️ Workout")

    /** 7 PM today, or tomorrow when that is less than two hours away. */
    fun defaultStart(
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long {
        val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone).date
        val tonight = LocalDateTime(today, LocalTime(19, 0)).toInstant(timeZone).toEpochMilliseconds()
        return if (tonight - nowEpochMs >= TWO_HOURS_MS) {
            tonight
        } else {
            LocalDateTime(today.plus(DatePeriod(days = 1)), LocalTime(19, 0)).toInstant(timeZone).toEpochMilliseconds()
        }
    }

    /** Today, Tomorrow, then the next five days. */
    fun dayChips(
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<LocalDate> {
        val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone).date
        return (0..6).map { today.plus(DatePeriod(days = it)) }
    }

    /** Moves the start to [date], keeping its time of day. */
    fun moveToDay(
        startEpochMs: Long,
        date: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long {
        val time = Instant.fromEpochMilliseconds(startEpochMs).toLocalDateTime(timeZone).time
        return LocalDateTime(date, time).toInstant(timeZone).toEpochMilliseconds()
    }

    /** When the start moves, the end keeps the plan's length. */
    fun shiftEnd(
        oldStartEpochMs: Long,
        newStartEpochMs: Long,
        endEpochMs: Long?,
    ): Long? = endEpochMs?.let { it + (newStartEpochMs - oldStartEpochMs) }

    /** An explicit end must be at least 15 minutes after the start. */
    fun clampEnd(
        startEpochMs: Long,
        endEpochMs: Long,
    ): Long = maxOf(endEpochMs, startEpochMs + MIN_LENGTH_MS)

    fun canSend(
        title: String,
        startEpochMs: Long,
        endEpochMs: Long?,
        nowEpochMs: Long,
    ): Boolean =
        title.isNotBlank() &&
            title.trim().length <= HangoutPlan.TITLE_MAX &&
            startEpochMs > nowEpochMs &&
            (endEpochMs == null || endEpochMs >= startEpochMs + MIN_LENGTH_MS)
}

/** Custom plan ideas persisted on this device (iOS `SettingsStore.planIdeas`). */
object PlanIdeasStore {
    private val serializer = ListSerializer(String.serializer())

    suspend fun load(storage: TokenStorage): List<String> =
        storage.getPlanCustomIdeas()?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    suspend fun save(
        storage: TokenStorage,
        ideas: List<String>,
    ) {
        storage.savePlanCustomIdeas(Json.encodeToString(serializer, ideas.distinct()))
    }

    fun normalize(raw: String): String? = raw.trim().take(PlanDraftRules.CUSTOM_IDEA_MAX).takeIf { it.isNotEmpty() }
}
