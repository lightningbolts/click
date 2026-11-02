@file:Suppress("ktlint:standard:backing-property-naming")

package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropAudience
import compose.project.click.click.collaboration.SharedDropGroup
import compose.project.click.click.collaboration.clickDropPreviewJpeg
import compose.project.click.click.collaboration.encodedImageSize
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.api.SharedDropPostException
import compose.project.click.click.data.api.SharedDropsApi
import compose.project.click.click.data.api.SharedDropsService
import compose.project.click.click.data.api.sharedDropCreateBody
import compose.project.click.click.data.flags.FeatureFlag
import compose.project.click.click.data.flags.FeatureFlags
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** A drop being shared, kept so a failed post retries with the same `client_drop_id`. */
data class PendingSharedDrop(
    val clientDropId: String,
    val jpeg: ByteArray,
    val audience: SharedDropAudience,
    val caption: String?,
) {
    override fun equals(other: Any?): Boolean = other is PendingSharedDrop && other.clientDropId == clientDropId

    override fun hashCode(): Int = clientDropId.hashCode()
}

data class SharedDropsUiState(
    val drops: List<SharedDrop> = emptyList(),
    val loaded: Boolean = false,
    val posting: Boolean = false,
    val postError: String? = null,
    /** Kept after a failure that Retry can fix; null when it can't (cap reached, bad photo). */
    val pendingPost: PendingSharedDrop? = null,
    val developing: Set<String> = emptySet(),
    val notice: String? = null,
)

/** Where developed photos, teasers and the last strip live on device (fakeable in tests). */
interface SharedDropsDisk {
    fun read(
        key: String,
        extension: String,
    ): ByteArray?

    fun write(
        key: String,
        bytes: ByteArray,
        extension: String,
    )
}

/** The app's private media vault (the same store as chat media). */
object VaultSharedDropsDisk : SharedDropsDisk {
    override fun read(
        key: String,
        extension: String,
    ): ByteArray? = readChatMediaVaultBytes(key, extension)?.takeIf { it.isNotEmpty() }

    override fun write(
        key: String,
        bytes: ByteArray,
        extension: String,
    ) {
        writeChatMediaVaultFile(key, bytes, extension)
    }
}

internal const val SHARED_DROP_DEVELOP_FAILED = "Couldn't develop right now. Try again in a moment."
internal const val SHARED_DROP_SHARE_FAILED = "Couldn't share your drop. Tap Retry — it won't be shared twice."

/** Marks [id] developed (keeping any newer signed URL) everywhere it appears. */
internal fun List<SharedDrop>.withDeveloped(
    id: String,
    developedAtIso: String,
    originalUrl: String?,
): List<SharedDrop> = map { if (it.id == id) it.copy(developedAtIso = developedAtIso, originalUrl = originalUrl ?: it.originalUrl) else it }

/** A newly shared drop goes first; retrying a post never shows it twice. */
internal fun List<SharedDrop>.withShared(drop: SharedDrop): List<SharedDrop> = listOf(drop) + filterNot { it.id == drop.id }

/** The strip as kept on disk: signed URLs expire, so only what the cache can paint is stored. */
internal fun List<SharedDrop>.forDiskCache(): List<SharedDrop> = map { it.copy(previewUrl = null, originalUrl = null) }

/** A random RFC 4122 v4 UUID (the server requires `client_drop_id` to be one). */
internal fun newSharedDropClientId(random: Random = Random.Default): String {
    val bytes = random.nextBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

/** How long until the next pending drop reveals (clamped), so the strip re-resolves right on time. */
internal fun sharedDropsNextTickMs(
    drops: List<SharedDrop>,
    now: Instant,
): Long {
    val nextReveal = drops.mapNotNull { d -> d.revealAt?.takeIf { it > now } }.minOrNull()
    return nextReveal?.let { (it - now).inWholeMilliseconds.coerceIn(500, 60_000) + 250 } ?: 60_000
}

/**
 * The Home shared-drops strip and story viewer (flag `shared_drops`). Mirrors iOS
 * `Click/Features/Home/SharedDropsStore.swift`: the strip and developed originals paint from disk
 * first, then refresh; a failed share keeps its `client_drop_id` so Retry never shares twice.
 */
class SharedDropsViewModel(
    private val service: SharedDropsService = SharedDropsApi(),
    private val disk: SharedDropsDisk = VaultSharedDropsDisk,
    private val currentUserId: () -> String? = { AppDataManager.currentUser.value?.id },
    private val isEnabled: () -> Boolean = { FeatureFlags.isEnabled(FeatureFlag.SHARED_DROPS) },
    private val makePreview: suspend (ByteArray) -> ByteArray? = ::clickDropPreviewJpeg,
    private val imageSize: (ByteArray) -> Pair<Int, Int>? = ::encodedImageSize,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(SharedDropsUiState())
    val state: StateFlow<SharedDropsUiState> = _state.asStateFlow()

    private val _originals = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Developed originals by drop ID (disk first, then the signed URL). */
    val originals: StateFlow<Map<String, ByteArray>> = _originals.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Pixelated teasers by drop ID. */
    val previews: StateFlow<Map<String, ByteArray>> = _previews.asStateFlow()

    private val loadingImages = mutableSetOf<String>()
    private var loadedForUser: String? = null

    fun groups(at: Instant = now()): List<SharedDropGroup> = SharedDropGroup.group(_state.value.drops, at)

    private fun stripKey(uid: String) = "shared-drops-strip-$uid"

    private fun originalKey(
        uid: String,
        id: String,
    ) = "shared-drop-$uid-$id-original"

    private fun previewKey(
        uid: String,
        id: String,
    ) = "shared-drop-$uid-$id-preview"

    /** Paints the last strip from disk, then fetches the live one. */
    fun refresh() {
        if (!isEnabled()) return
        val uid = currentUserId()?.takeIf { it.isNotBlank() } ?: return
        if (loadedForUser != uid) {
            // Another account's drops never show, even for a frame.
            loadedForUser = uid
            _state.value = SharedDropsUiState()
            _originals.value = emptyMap()
            _previews.value = emptyMap()
            disk
                .read(stripKey(uid), "json")
                ?.let { bytes ->
                    runCatching { json.decodeFromString(ListSerializer(SharedDrop.serializer()), bytes.decodeToString()) }.getOrNull()
                }?.let { cached ->
                    _state.update { it.copy(drops = cached) }
                    cached.forEach { warmImages(it) }
                }
        }
        viewModelScope.launch {
            service
                .strip()
                .onSuccess { list ->
                    if (currentUserId() != uid) return@onSuccess
                    commit(uid, list)
                    _state.update { it.copy(loaded = true) }
                    list.forEach { warmImages(it) }
                }.onFailure { _state.update { s -> s.copy(loaded = true) } }
        }
    }

    private fun commit(
        uid: String,
        list: List<SharedDrop>,
    ) {
        _state.update { it.copy(drops = list) }
        val encoded = json.encodeToString(ListSerializer(SharedDrop.serializer()), list.forDiskCache())
        disk.write(stripKey(uid), encoded.encodeToByteArray(), "json")
    }

    /** Loads the teaser (and the original once developed) for [drop], disk first. */
    fun warmImages(drop: SharedDrop) {
        val uid = currentUserId() ?: return
        val id = drop.id
        if (id !in _previews.value) {
            val cached = disk.read(previewKey(uid, id), "jpg")
            if (cached != null) {
                _previews.update { it + (id to cached) }
            } else {
                drop.previewUrl?.let { url ->
                    load("p:$id") {
                        service.download(url).getOrNull()?.takeIf { it.isNotEmpty() }?.let { bytes ->
                            disk.write(previewKey(uid, id), bytes, "jpg")
                            _previews.update { it + (id to bytes) }
                        }
                    }
                }
            }
        }
        if (drop.state(now()) == ClickDropDevelopState.Developed && id !in _originals.value) {
            val cached = disk.read(originalKey(uid, id), "jpg")
            if (cached != null) {
                _originals.update { it + (id to cached) }
            } else {
                load("o:$id") {
                    // A copy from disk has no URL; developing again is idempotent and signs a fresh one.
                    val url = drop.originalUrl ?: service.develop(id).getOrNull()?.url
                    url?.let { downloadOriginal(uid, id, it) }
                }
            }
        }
    }

    private fun load(
        key: String,
        block: suspend () -> Unit,
    ) {
        if (!loadingImages.add(key)) return
        viewModelScope.launch {
            try {
                block()
            } finally {
                loadingImages.remove(key)
            }
        }
    }

    private suspend fun downloadOriginal(
        uid: String,
        id: String,
        url: String,
    ) {
        val bytes = service.download(url).getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        disk.write(originalKey(uid, id), bytes, "jpg")
        _originals.update { it + (id to bytes) }
    }

    /** The story opening on a ready drop: marks it developed for this viewer and fetches the original. */
    fun develop(drop: SharedDrop) {
        val uid = currentUserId() ?: return
        if (drop.state(now()) != ClickDropDevelopState.Ready || drop.id in _state.value.developing) return
        _state.update { it.copy(developing = it.developing + drop.id) }
        viewModelScope.launch {
            try {
                service
                    .develop(drop.id)
                    .onSuccess { result ->
                        if (result?.status != "developed") return@onSuccess
                        val developedIso = result.developedAt ?: now().toString()
                        commit(uid, _state.value.drops.withDeveloped(drop.id, developedIso, result.url))
                        result.url?.let { downloadOriginal(uid, drop.id, it) }
                    }.onFailure { error ->
                        if (error !is CancellationException) _state.update { it.copy(notice = SHARED_DROP_DEVELOP_FAILED) }
                    }
            } finally {
                _state.update { it.copy(developing = it.developing - drop.id) }
            }
        }
    }

    /** Shares one drop. A failure Retry can fix keeps it with the same `client_drop_id`. */
    fun share(
        jpeg: ByteArray,
        audience: SharedDropAudience,
        caption: String?,
    ) = post(PendingSharedDrop(newSharedDropClientId(), jpeg, audience, caption?.trim()?.takeIf { it.isNotEmpty() }))

    fun retryShare() {
        _state.value.pendingPost?.let(::post)
    }

    fun dismissPostError() {
        _state.update { it.copy(postError = null, pendingPost = null) }
    }

    private fun post(pending: PendingSharedDrop) {
        val uid = currentUserId() ?: return
        if (_state.value.posting) return
        _state.update { it.copy(posting = true, postError = null, pendingPost = pending) }
        viewModelScope.launch {
            val preview = makePreview(pending.jpeg)
            val size = imageSize(pending.jpeg)
            val result =
                if (preview == null) {
                    Result.failure(SharedDropPostException.InvalidPhoto())
                } else {
                    service.share(
                        sharedDropCreateBody(
                            original = pending.jpeg,
                            preview = preview,
                            width = size?.first,
                            height = size?.second,
                            audience = pending.audience,
                            caption = pending.caption,
                            clientDropId = pending.clientDropId,
                        ),
                    )
                }
            result
                .onSuccess { drop ->
                    preview?.let { disk.write(previewKey(uid, drop.id), it, "jpg") }
                    // You already have your own original: no download when it develops.
                    disk.write(originalKey(uid, drop.id), pending.jpeg, "jpg")
                    preview?.let { p -> _previews.update { it + (drop.id to p) } }
                    _originals.update { it + (drop.id to pending.jpeg) }
                    commit(uid, _state.value.drops.withShared(drop))
                    _state.update { it.copy(posting = false, pendingPost = null) }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    // Past the cap, or with a photo the server refuses, Retry can't succeed.
                    val refusal = error as? SharedDropPostException
                    _state.update {
                        it.copy(
                            posting = false,
                            postError = refusal?.message ?: SHARED_DROP_SHARE_FAILED,
                            pendingPost = if (refusal != null) null else pending,
                        )
                    }
                }
        }
    }

    /** The poster takes a drop down (removed for everyone it went to). */
    fun delete(drop: SharedDrop) {
        val uid = currentUserId() ?: return
        viewModelScope.launch {
            service
                .delete(drop.id)
                .onSuccess {
                    commit(uid, _state.value.drops.filterNot { it.id == drop.id })
                    _originals.update { it - drop.id }
                    _previews.update { it - drop.id }
                }.onFailure { _state.update { it.copy(notice = "Couldn't delete it. Try again.") } }
        }
    }

    /** A quiet report to the Click team; nobody else sees it. */
    fun report(drop: SharedDrop) {
        viewModelScope.launch {
            service
                .report(drop.id, "Reported from the story viewer")
                .onSuccess { _state.update { it.copy(notice = "Thanks. We'll take a look.") } }
                .onFailure { _state.update { it.copy(notice = "Couldn't send the report. Try again.") } }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }
}
