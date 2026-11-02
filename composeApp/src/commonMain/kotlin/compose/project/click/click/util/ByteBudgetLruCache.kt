package compose.project.click.click.util

/**
 * An LRU cache bounded by the total size of its values (in bytes), not by entry count. Used to cap
 * how many developed Click Drop originals stay in memory: an evicted original is still on disk and
 * reloads when its tile or story comes back on screen.
 *
 * A single value larger than [maxBytes] is still kept (alone), so the photo on screen always shows.
 * Not thread-safe; callers touch it from one dispatcher.
 */
class ByteBudgetLruCache<K, V>(
    val maxBytes: Long,
    private val sizeOf: (V) -> Long,
) {
    init {
        require(maxBytes > 0) { "maxBytes must be > 0" }
    }

    // LinkedHashMap keeps insertion order; re-inserting on access moves a key to the newest end.
    private val entries = LinkedHashMap<K, V>()

    var totalBytes: Long = 0
        private set

    val size: Int get() = entries.size

    operator fun contains(key: K): Boolean = entries.containsKey(key)

    /** The value for [key], marking it most recently used. */
    fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    /** Stores [value] as most recently used and returns the keys evicted to stay within [maxBytes]. */
    fun put(
        key: K,
        value: V,
    ): List<K> {
        entries.remove(key)?.let { totalBytes -= sizeOf(it).coerceAtLeast(0) }
        entries[key] = value
        totalBytes += sizeOf(value).coerceAtLeast(0)
        return trim()
    }

    fun remove(key: K): V? {
        val value = entries.remove(key) ?: return null
        totalBytes -= sizeOf(value).coerceAtLeast(0)
        return value
    }

    fun clear() {
        entries.clear()
        totalBytes = 0
    }

    /** Oldest first. */
    fun keys(): List<K> = entries.keys.toList()

    fun snapshot(): Map<K, V> = LinkedHashMap(entries)

    private fun trim(): List<K> {
        val evicted = mutableListOf<K>()
        while (totalBytes > maxBytes && entries.size > 1) {
            val oldest = entries.keys.first()
            remove(oldest)
            evicted += oldest
        }
        return evicted
    }
}
