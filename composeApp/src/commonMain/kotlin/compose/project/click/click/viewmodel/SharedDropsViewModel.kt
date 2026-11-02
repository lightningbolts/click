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
import compose.project.click.click.data.api.ReactionTarget
import compose.project.click.click.data.api.ReactionsApi
import compose.project.click.click.data.api.ReactionsService
import compose.project.click.click.data.api.SharedDropPostException
import compose.project.click.click.data.api.SharedDropsApi
import compose.project.click.click.data.api.SharedDropsService
import compose.project.click.click.data.api.sharedDropCreateBody
import compose.project.click.click.data.api.withMine
import compose.project.click.click.data.flags.FeatureFlag
import compose.project.click.click.data.flags.FeatureFlags
import compose.project.click.click.data.models.ChatDropReply
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.realtime.LiveBatch
import compose.project.click.click.data.realtime.LiveUpdates
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    /** "View all": every drop you can see, newest first, a page at a time. */
    val archive: List<SharedDrop> = emptyList(),
    /** Where the next archive page starts; null at the end. */
    val archiveNextBefore: String? = null,
    val archiveLoaded: Boolean = false,
    val archiveLoading: Boolean = false,
    /** The first page failed and nothing is on screen. */
    val archiveError: String? = null,
    /** A later page failed: the grid shows "Couldn't load more. Retry". */
    val archiveMoreFailed: Boolean = false,
) {
    /** The strip's copy first (it's the fresher one), else the archive's (iOS `SharedDropsStore.drop`). */
    fun drop(id: String): SharedDrop? = drops.firstOrNull { it.id == id } ?: archive.firstOrNull { it.id == id }
}

/** Sends a story reply (or a reaction) into the 1-1 chat with the poster; false when it didn't go. */
fun interface SharedDropReplySender {
    suspend fun send(
        connectionId: String,
        userId: String,
        text: String,
        metadata: JsonObject,
    ): Boolean
}

/** The app's chat pipeline: E2EE like any message, with `metadata.drop_reply` beside the crypto keys. */
object ChatSharedDropReplySender : SharedDropReplySender {
    override suspend fun send(
        connectionId: String,
        userId: String,
        text: String,
        metadata: JsonObject,
    ): Boolean =
        try {
            AppDataManager.chatRepository.sendMessageForConnection(
                connectionId = connectionId,
                userId = userId,
                content = text,
                messageType = ChatMessageType.TEXT,
                metadata = metadata,
            ) != null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
}

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
internal const val SHARED_DROP_ARCHIVE_FAILED = "Couldn't load your drops."

/** The toast after a story reply or reaction lands in the chat (iOS `SharedDropStorySource.send`). */
internal fun sharedDropReplyNotice(
    firstName: String,
    isReaction: Boolean,
    sent: Boolean,
): String =
    when {
        !sent -> "Couldn't send. Try again."
        isReaction -> "Reaction sent to $firstName"
        else -> "Sent to $firstName"
    }

/** The reply field shows on others' developed drops that have a chat to reply in (iOS `SharedDropFooter`). */
internal fun SharedDrop.canReply(now: Instant): Boolean = !isMine && !connectionId.isNullOrBlank() && !state(now).isPending

/** Replaces drop [id] wherever it appears. */
internal fun List<SharedDrop>.replacing(
    id: String,
    transform: (SharedDrop) -> SharedDrop,
): List<SharedDrop> = map { if (it.id == id) transform(it) else it }

/** Appends an archive page, never showing a drop twice. */
internal fun List<SharedDrop>.appendingPage(page: List<SharedDrop>): List<SharedDrop> {
    val seen = mapTo(HashSet()) { it.id }
    return this + page.filter { seen.add(it.id) }
}

/** Disk keys for a shared drop's photos (per account), shared with the chat's drop-reply header. */
internal fun sharedDropOriginalKey(
    uid: String,
    id: String,
) = "shared-drop-$uid-$id-original"

internal fun sharedDropPreviewKey(
    uid: String,
    id: String,
) = "shared-drop-$uid-$id-preview"

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
 * first, then refresh; a failed share keeps its `client_drop_id` so Retry never shares twice. Also
 * the "View all" archive, reactions (iOS `ReactionBar.swift`) and story replies
 * (iOS `SharedDropStorySource.send`), and a refresh on the Live updates `drops` hint.
 */
class SharedDropsViewModel(
    private val service: SharedDropsService = SharedDropsApi(),
    private val disk: SharedDropsDisk = VaultSharedDropsDisk,
    private val currentUserId: () -> String? = { AppDataManager.currentUser.value?.id },
    private val isEnabled: () -> Boolean = { FeatureFlags.isEnabled(FeatureFlag.SHARED_DROPS) },
    private val makePreview: suspend (ByteArray) -> ByteArray? = ::clickDropPreviewJpeg,
    private val imageSize: (ByteArray) -> Pair<Int, Int>? = ::encodedImageSize,
    private val now: () -> Instant = { Clock.System.now() },
    private val reactions: ReactionsService = ReactionsApi(),
    private val replySender: SharedDropReplySender = ChatSharedDropReplySender,
    liveHints: Flow<LiveBatch> = LiveUpdates.batches,
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
    private var archiveGeneration = 0

    init {
        // A drop shared, deleted or developed elsewhere (or a new connection's drops) refreshes the strip.
        viewModelScope.launch {
            liveHints.collect { batch ->
                if (!batch.refetchesSharedDrops || loadedForUser == null) return@collect
                refresh()
                if (_state.value.archiveLoaded) loadArchive()
            }
        }
    }

    fun groups(at: Instant = now()): List<SharedDropGroup> = SharedDropGroup.group(_state.value.drops, at)

    private fun stripKey(uid: String) = "shared-drops-strip-$uid"

    private fun originalKey(
        uid: String,
        id: String,
    ) = sharedDropOriginalKey(uid, id)

    private fun previewKey(
        uid: String,
        id: String,
    ) = sharedDropPreviewKey(uid, id)

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
                        _state.update { it.copy(archive = it.archive.withDeveloped(drop.id, developedIso, result.url)) }
                        commit(uid, _state.value.drops.withDeveloped(drop.id, developedIso, result.url))
                        // Reactions open with the photo (the strip inlines them for drops already developed).
                        loadReactions(drop.id)
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
                    _state.update { s -> s.copy(archive = s.archive.filterNot { it.id == drop.id }) }
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

    /** Fetches reactions for a developed drop the strip didn't inline them for. */
    fun loadReactions(id: String) {
        viewModelScope.launch {
            reactions.reactions(ReactionTarget.SHARED_DROP, id).onSuccess { r -> replaceDrop(id) { it.copy(reactions = r) } }
        }
    }

    /**
     * Reacts (null takes it back). The pick shows at once and a failure puts the old one back. A new
     * reaction also answers in the 1-1 chat, carrying the drop (iOS `ReactionBar.onReacted`).
     */
    fun react(
        drop: SharedDrop,
        emoji: String?,
    ) {
        if (drop.isMine) return
        val previous = _state.value.drop(drop.id)?.reactions ?: drop.reactions
        replaceDrop(drop.id) { it.copy(reactions = previous.withMine(emoji)) }
        viewModelScope.launch {
            reactions
                .react(ReactionTarget.SHARED_DROP, drop.id, emoji)
                .onSuccess { saved ->
                    replaceDrop(drop.id) { it.copy(reactions = saved) }
                    if (emoji != null) sendReply(drop, emoji, isReaction = true)
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    replaceDrop(drop.id) { it.copy(reactions = previous) }
                }
        }
    }

    /** "Reply to {name}…": into the 1-1 chat with the poster, through the usual E2EE send. */
    fun sendReply(
        drop: SharedDrop,
        text: String,
        isReaction: Boolean = false,
    ) {
        val body = text.trim()
        val connectionId = drop.connectionId?.takeIf { it.isNotBlank() } ?: return
        val uid = currentUserId() ?: return
        if (body.isEmpty() || drop.isMine) return
        viewModelScope.launch {
            val sent = replySender.send(connectionId, uid, body, ChatDropReply.metadataFor(drop.id, isReaction))
            _state.update { it.copy(notice = sharedDropReplyNotice(drop.user.firstName, isReaction, sent)) }
        }
    }

    private fun replaceDrop(
        id: String,
        transform: (SharedDrop) -> SharedDrop,
    ) {
        val uid = currentUserId()
        _state.update { it.copy(archive = it.archive.replacing(id, transform)) }
        if (_state.value.drops.any { it.id == id }) {
            val next = _state.value.drops.replacing(id, transform)
            if (uid != null) commit(uid, next) else _state.update { it.copy(drops = next) }
        }
    }

    /** The first page of "View all" (iOS `SharedDropsStore.loadArchive`); a newer load wins over an older one. */
    fun loadArchive() {
        if (!isEnabled()) return
        val uid = currentUserId()?.takeIf { it.isNotBlank() } ?: return
        val generation = ++archiveGeneration
        _state.update { it.copy(archiveLoading = true, archiveError = null) }
        viewModelScope.launch {
            service
                .archive(null)
                .onSuccess { page ->
                    if (generation != archiveGeneration || currentUserId() != uid) return@onSuccess
                    _state.update {
                        it.copy(
                            archive = page.drops,
                            archiveNextBefore = page.nextBefore,
                            archiveLoaded = true,
                            archiveLoading = false,
                            archiveMoreFailed = false,
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    if (generation != archiveGeneration) return@onFailure
                    _state.update {
                        it.copy(archiveLoading = false, archiveError = if (it.archiveLoaded) null else SHARED_DROP_ARCHIVE_FAILED)
                    }
                }
        }
    }

    /** The next archive page, when there is one and none is loading. */
    fun loadMoreArchive() {
        val s = _state.value
        val cursor = s.archiveNextBefore ?: return
        if (s.archiveLoading || !s.archiveLoaded) return
        val generation = archiveGeneration
        _state.update { it.copy(archiveLoading = true) }
        viewModelScope.launch {
            service
                .archive(cursor)
                .onSuccess { page ->
                    if (generation != archiveGeneration) return@onSuccess
                    _state.update {
                        it.copy(
                            archive = it.archive.appendingPage(page.drops),
                            archiveNextBefore = page.nextBefore,
                            archiveLoading = false,
                            archiveMoreFailed = false,
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    if (generation == archiveGeneration) _state.update { it.copy(archiveLoading = false, archiveMoreFailed = true) }
                }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }
}
