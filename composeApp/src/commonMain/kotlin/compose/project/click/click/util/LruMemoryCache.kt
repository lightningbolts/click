package compose.project.click.click.util

/**
 * Small LRU cache for commonMain (no JVM-only dependencies).
 */
class LruMemoryCache<K, V>(private val maxEntries: Int) {
    init {
        require(maxEntries > 0) { "maxEntries must be > 0" }
    }

    private val values = mutableMapOf<K, V>()
    private val accessOrder = ArrayDeque<K>()

    fun get(key: K): V? {
        val value = values[key] ?: return null
        accessOrder.remove(key)
        accessOrder.addLast(key)
        return value
    }

    fun put(key: K, value: V): V? {
        val alreadyPresent = values.containsKey(key)
        values[key] = value
        if (alreadyPresent) {
            accessOrder.remove(key)
        }
        accessOrder.addLast(key)

        if (values.size <= maxEntries) return null
        val oldestKey = accessOrder.removeFirstOrNull() ?: return null
        return values.remove(oldestKey)
    }

    fun valuesSnapshot(): List<V> = accessOrder.mapNotNull { key -> values[key] }

    fun clear() {
        values.clear()
        accessOrder.clear()
    }

    fun remove(key: K): V? {
        accessOrder.remove(key)
        return values.remove(key)
    }
}
