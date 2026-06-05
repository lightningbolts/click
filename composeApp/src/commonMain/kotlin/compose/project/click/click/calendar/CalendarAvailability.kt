package compose.project.click.click.calendar

/**
 * Identifies gaps longer than [minGapMinutes] where both [userCal] and [friendCal] are free
 * inside their shared window intersection.
 *
 * Pure Kotlin — safe to run on [kotlinx.coroutines.Dispatchers.Default].
 */
fun calculateAvailabilityOverlaps(
    userCal: CalendarFreeBusy,
    friendCal: CalendarFreeBusy,
    minGapMinutes: Int = 45,
): List<AvailabilityOverlapGap> {
    val minGapMs = minGapMinutes.coerceAtLeast(1) * 60_000L
    val windowStart = maxOf(userCal.windowStartEpochMs, friendCal.windowStartEpochMs)
    val windowEnd = minOf(userCal.windowEndEpochMs, friendCal.windowEndEpochMs)
    if (windowEnd <= windowStart) return emptyList()

    val userFree = invertBusyToFree(userCal.busyBlocks, windowStart, windowEnd)
    val friendFree = invertBusyToFree(friendCal.busyBlocks, windowStart, windowEnd)
    return intersectFreeBlocks(userFree, friendFree)
        .filter { it.durationMs > minGapMs }
        .sortedBy { it.startEpochMs }
}

internal fun mergeBusyBlocks(blocks: List<BusyBlock>): List<BusyBlock> {
    if (blocks.isEmpty()) return emptyList()
    val sorted = blocks.sortedBy { it.startEpochMs }
    val merged = ArrayList<BusyBlock>(sorted.size)
    var current = sorted.first()
    for (i in 1 until sorted.size) {
        val next = sorted[i]
        if (next.startEpochMs <= current.endEpochMs) {
            current = BusyBlock(
                startEpochMs = current.startEpochMs,
                endEpochMs = maxOf(current.endEpochMs, next.endEpochMs),
            )
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

internal fun invertBusyToFree(
    busyBlocks: List<BusyBlock>,
    windowStart: Long,
    windowEnd: Long,
): List<AvailabilityOverlapGap> {
    if (windowEnd <= windowStart) return emptyList()
    val mergedBusy = mergeBusyBlocks(
        busyBlocks.mapNotNull { block ->
            val start = maxOf(block.startEpochMs, windowStart)
            val end = minOf(block.endEpochMs, windowEnd)
            if (end > start) BusyBlock(start, end) else null
        },
    )
    if (mergedBusy.isEmpty()) {
        return listOf(AvailabilityOverlapGap(windowStart, windowEnd))
    }
    val free = ArrayList<AvailabilityOverlapGap>()
    var cursor = windowStart
    for (busy in mergedBusy) {
        if (busy.startEpochMs > cursor) {
            free.add(AvailabilityOverlapGap(cursor, busy.startEpochMs))
        }
        cursor = maxOf(cursor, busy.endEpochMs)
    }
    if (cursor < windowEnd) {
        free.add(AvailabilityOverlapGap(cursor, windowEnd))
    }
    return free
}

internal fun intersectFreeBlocks(
    a: List<AvailabilityOverlapGap>,
    b: List<AvailabilityOverlapGap>,
): List<AvailabilityOverlapGap> {
    if (a.isEmpty() || b.isEmpty()) return emptyList()
    val result = ArrayList<AvailabilityOverlapGap>()
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        val left = a[i]
        val right = b[j]
        val start = maxOf(left.startEpochMs, right.startEpochMs)
        val end = minOf(left.endEpochMs, right.endEpochMs)
        if (end > start) {
            result.add(AvailabilityOverlapGap(start, end))
        }
        if (left.endEpochMs < right.endEpochMs) {
            i++
        } else {
            j++
        }
    }
    return result
}
