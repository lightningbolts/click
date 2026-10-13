package compose.project.click.click.core

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer

/**
 * One [UserDiskCache] document a ViewModel paints from on its first frame, then revalidates
 * (sheet 07 G1-5; iOS `CacheStore`). [bound] trims what is stored (the shared drops strip keeps
 * at most 240). A file that no longer decodes is dropped and reads as nothing.
 */
class DiskFirstCache<T>(
    val key: String,
    private val serializer: KSerializer<T>,
    private val bound: (T) -> T = { it },
) {
    /** The cached value for [userId], or null. Blocking, but small: call it while building state. */
    fun seed(userId: String?): T? = UserDiskCache.read(userId, key, serializer)

    fun store(
        userId: String?,
        value: T,
    ) {
        UserDiskCache.write(userId, key, serializer, bound(value))
    }

    /**
     * Fetches fresh data; on success publishes it and writes it through. A failure keeps whatever
     * was painted from disk (the caller shows its own offline or error state).
     */
    suspend fun revalidate(
        userId: String?,
        fetch: suspend () -> Result<T>,
        publish: (T) -> Unit,
    ): Result<T> {
        val result =
            try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        result.onSuccess { fresh ->
            val bounded = bound(fresh)
            publish(bounded)
            UserDiskCache.write(userId, key, serializer, bounded)
        }
        return result
    }
}
