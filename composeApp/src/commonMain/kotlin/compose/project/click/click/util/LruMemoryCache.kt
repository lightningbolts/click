package compose.project.click.click.util

/**
 * Small thread-safe LRU cache for commonMain (no JVM-only dependencies).
 */
class LruMemoryCache<K, V>(private val maxEntries: Int) {
    init {
        require(maxEntries > 0) { "maxEntries must be > 0" }
    }

    private val lock = Any()
    private val map = LinkedHashMap<K, V>(maxEntries, 0.75f, true)

    fun get(key: K): V? = synchronized(lock) { map[key] }

    fun put(key: K, value: V): V? = synchronized(lock) {
        map[key] = value
        if (map.size <= maxEntries) return@synchronized null
        val eldestIterator = map.entries.iterator()
        if (!eldestIterator.hasNext()) return@synchronized null
        val eldest = eldestIterator.next()
        eldestIterator.remove()
        eldest.value
    }

    fun valuesSnapshot(): List<V> = synchronized(lock) { map.values.toList() }

    fun clear() = synchronized(lock) { map.clear() }
}
