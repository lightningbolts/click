package compose.project.click.click.encounter

import compose.project.click.click.data.models.PendingHandshake
import compose.project.click.click.data.models.ProximityHandshakeLocationSnapshot
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.sensors.HardwareVibeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent offline queue for tri-factor proximity encounters (heard tokens + sensor context).
 *
 * Mirrors the architectural seam of [compose.project.click.click.data.chat.PendingMessageQueue],
 * but persists via [TokenStorage] so encounters survive process death and sync when online.
 */
class PendingEncounterQueue(
    private val tokenStorage: TokenStorage,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<List<PendingHandshake>>(emptyList())

    val state: StateFlow<List<PendingHandshake>> = _state.asStateFlow()

    val size: Int get() = _state.value.size

    fun observe(): Flow<List<PendingHandshake>> = _state

    fun observeCount(): Flow<Int> =
        _state.map { it.size }

    suspend fun hydrate() {
        mutex.withLock {
            _state.value = readPersisted()
        }
    }

    suspend fun enqueue(
        myToken: String,
        heardTokens: List<String>,
        latitude: Double? = null,
        longitude: Double? = null,
        altitudeMeters: Double? = null,
        hardwareVibe: HardwareVibeSnapshot? = null,
        noiseLevel: String? = null,
        exactNoiseLevelDb: Double? = null,
        heightCategory: String? = null,
        exactBarometricElevationM: Double? = null,
        contextTags: List<String> = emptyList(),
        id: String? = null,
        capturedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): PendingHandshake {
        val now = capturedAtEpochMs
        val loc = if (latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            !(latitude == 0.0 && longitude == 0.0)
        ) {
            ProximityHandshakeLocationSnapshot(
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters?.takeIf { it.isFinite() },
                capturedAtEpochMs = now,
            )
        } else {
            null
        }
        val draft = PendingHandshake(
            id = id ?: compose.project.click.click.data.models.newPendingHandshakeId(),
            myToken = myToken.trim(),
            heardTokens = heardTokens.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            capturedAtEpochMs = now,
            location = loc,
            hardwareVibe = hardwareVibe,
            noiseLevel = noiseLevel?.trim()?.takeIf { it.isNotEmpty() },
            exactNoiseLevelDb = exactNoiseLevelDb?.takeIf { it.isFinite() },
            heightCategory = heightCategory?.trim()?.takeIf { it.isNotEmpty() },
            exactBarometricElevationM = exactBarometricElevationM?.takeIf { it.isFinite() },
            contextTags = contextTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        )

        mutex.withLock {
            val q = _state.value.toMutableList()
            val dup = q.lastOrNull {
                it.myToken == draft.myToken &&
                    it.heardTokens == draft.heardTokens &&
                    (now - it.capturedAtEpochMs) < 60_000L
            }
            if (dup != null) return dup

            q += draft
            while (q.size > MAX_QUEUE_SIZE) q.removeAt(0)
            persistLocked(q)
            return draft
        }
    }

    suspend fun peek(): PendingHandshake? =
        mutex.withLock { _state.value.firstOrNull() }

    suspend fun dequeueHead(): PendingHandshake? {
        mutex.withLock {
            val q = _state.value
            if (q.isEmpty()) return null
            val rest = q.drop(1)
            persistLocked(rest)
            return q.first()
        }
    }

    suspend fun replaceAll(items: List<PendingHandshake>) {
        mutex.withLock {
            persistLocked(items)
        }
    }

    suspend fun clear() {
        mutex.withLock {
            persistLocked(emptyList())
        }
    }

    fun snapshot(): List<PendingHandshake> = _state.value

    private suspend fun readPersisted(): List<PendingHandshake> {
        val raw = tokenStorage.getPendingProximityHandshakeQueue()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<PendingHandshake>>(raw)
        }.getOrElse {
            println("PendingEncounterQueue: Failed to decode queue: ${it.message}")
            emptyList()
        }
    }

    private suspend fun persistLocked(items: List<PendingHandshake>) {
        _state.value = items
        tokenStorage.savePendingProximityHandshakeQueue(
            if (items.isEmpty()) null else json.encodeToString(items),
        )
    }

    private companion object {
        const val MAX_QUEUE_SIZE = 32
    }
}
