package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.collaboration.ClickDropDevelopResult
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.ClickDropFlags
import compose.project.click.click.collaboration.ClickDropKind
import compose.project.click.click.collaboration.ClickDropRef
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropAudience
import compose.project.click.click.collaboration.SharedDropGroup
import compose.project.click.click.collaboration.clickDropPreviewJpeg
import compose.project.click.click.collaboration.encodedImageSize
import compose.project.click.click.core.DiskFirstCache
import compose.project.click.click.core.FeatureFlagGate
import compose.project.click.click.core.UserDiskCache
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.api.DropsApi
import compose.project.click.click.data.api.ReactionTarget
import compose.project.click.click.data.api.ReactionsApi
import compose.project.click.click.data.api.ReactionsState
import compose.project.click.click.data.api.SharedDropPostException
import compose.project.click.click.data.api.SharedDropsApi
import compose.project.click.click.data.models.ChatDropReply
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.SupabaseChatRepository
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random

/** A drop being shared, kept so a failed post retries with the same `client_drop_id`. */
data class PendingSharedDrop(
    val clientDropId: String,
    val jpeg: ByteArray,
    val audience: SharedDropAudience,
    val caption: String?,
)

data class SharedDropsUiState(
    val drops: List<SharedDrop> = emptyList(),
    val loaded: Boolean = false,
    val posting: Boolean = false,
    val postError: String? = null,
    val pendingPost: PendingSharedDrop? = null,
    val developing: Set<String> = emptySet(),
    val notice: String? = null,
    val archive: List<SharedDrop> = emptyList(),
    val archiveNextBefore: String? = null,
    val archiveEnd: Boolean = false,
    val archiveLoading: Boolean = false,
)

/**
 * The Home shared-drops strip, story viewer and archive (sheet 07 G2-3..G2-9; iOS
 * `SharedDropsStore.swift`). Gated on `shared_drops`. The strip and developed originals paint from
 * disk first, then refresh.
 */
class SharedDropsViewModel(
    private val api: SharedDropsApi = SharedDropsApi(),
    private val drops: DropsApi = DropsApi(),
    private val reactionsApi: ReactionsApi = ReactionsApi(),
    private val chatRepository: ChatRepository = SupabaseChatRepository(tokenStorage = createTokenStorage()),
) : ViewModel() {
    /** The strip as last seen, per user, for the first frame (sheet 07 G1-5, G2-9). */
    private val stripCache =
        DiskFirstCache(UserDiskCache.Keys.SHARED_DROPS_STRIP, ListSerializer(SharedDrop.serializer())) {
            it.take(STRIP_CACHE_MAX)
        }

    // Seeded synchronously from disk so the strip paints on the first frame; [refresh] revalidates.
    private val _state = MutableStateFlow(SharedDropsUiState(drops = cachedStrip()))
    val state: StateFlow<SharedDropsUiState> = _state.asStateFlow()

    private val _originals = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Developed originals by drop ID (disk cache first, then the signed URL). */
    val originals: StateFlow<Map<String, ByteArray>> = _originals.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Pixelated teasers by drop ID. */
    val previews: StateFlow<Map<String, ByteArray>> = _previews.asStateFlow()

    private val loadingImages = mutableSetOf<String>()

    val isEnabled: Boolean get() = FeatureFlagGate.isEnabled(ClickDropFlags.SHARED_DROPS)

    fun groups(): List<SharedDropGroup> = SharedDropGroup.group(_state.value.drops)

    private fun userId(): String? = AppDataManager.currentUser.value?.id

    /** The last strip from disk. Signed URLs expire, so a copy read back keeps only what the cache can paint. */
    private fun cachedStrip(): List<SharedDrop> {
        if (!isEnabled) return emptyList()
        return stripCache
            .seed(userId())
            ?.map { it.copy(originalUrl = null, previewUrl = null) }
            .orEmpty()
    }

    private fun persistStrip() {
        stripCache.store(userId(), _state.value.drops)
    }

    private fun originalKey(id: String) = "shared-drop-$id-original"

    private fun previewKey(id: String) = "shared-drop-$id-preview"

    /** Paints the last strip from disk, then fetches the live one. */
    fun refresh() {
        if (!isEnabled) return
        val uid = userId() ?: return
        // The user can resolve after this ViewModel was built (cold start): paint from disk now.
        if (!_state.value.loaded && _state.value.drops.isEmpty()) {
            cachedStrip().takeIf { it.isNotEmpty() }?.let { cached -> _state.update { it.copy(drops = cached) } }
        }
        viewModelScope.launch {
            stripCache
                .revalidate(
                    userId = uid,
                    fetch = { api.strip() },
                    publish = { list -> _state.update { it.copy(drops = list, loaded = true) } },
                ).onSuccess { list -> list.forEach { warmImages(it) } }
                .onFailure { _state.update { s -> s.copy(loaded = true) } }
        }
    }

    /** Loads the teaser (and the original once developed) for [drop], disk first. */
    fun warmImages(drop: SharedDrop) {
        val id = drop.id
        if (id !in _previews.value) {
            readChatMediaVaultBytes(previewKey(id), "jpg")?.let { b -> _previews.update { it + (id to b) } }
                ?: drop.previewUrl?.let { url ->
                    load("p:$id") {
                        api.download(url).getOrNull()?.also { b ->
                            writeChatMediaVaultFile(previewKey(id), b, "jpg")
                            _previews.update {
                                it +
                                    (id to b)
                            }
                        }
                    }
                }
        }
        if (drop.state() == ClickDropDevelopState.Developed && id !in _originals.value) {
            readChatMediaVaultBytes(originalKey(id), "jpg")?.let { b -> _originals.update { it + (id to b) } }
                ?: drop.originalUrl?.let { url -> load("o:$id") { downloadOriginal(id, url) } }
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
        id: String,
        url: String,
    ) {
        val bytes = api.download(url).getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        writeChatMediaVaultFile(originalKey(id), bytes, "jpg")
        _originals.update { it + (id to bytes) }
    }

    /** Tap to develop (or the story opening on a ready drop): marks it developed and fetches the original. */
    fun develop(drop: SharedDrop) {
        if (drop.state() != ClickDropDevelopState.Ready || drop.id in _state.value.developing) return
        _state.update { it.copy(developing = it.developing + drop.id) }
        viewModelScope.launch {
            try {
                val result =
                    runCatching { drops.develop(listOf(ClickDropRef(ClickDropKind.SHARED, drop.id))) }
                        .getOrNull()
                        ?.firstOrNull { it.status == ClickDropDevelopResult.Status.DEVELOPED }
                if (result == null) {
                    _state.update { it.copy(notice = "Couldn't develop right now. Try again in a moment.") }
                    return@launch
                }
                val developedIso = (result.developedAt ?: Clock.System.now()).toString()
                replaceDrop(drop.id) { it.copy(developedAtIso = developedIso, originalUrl = result.originalUrl ?: it.originalUrl) }
                result.originalUrl?.let { downloadOriginal(drop.id, it) }
                if (drop.reactions == null) loadReactions(drop.id)
            } finally {
                _state.update { it.copy(developing = it.developing - drop.id) }
            }
        }
    }

    private fun replaceDrop(
        id: String,
        transform: (SharedDrop) -> SharedDrop,
    ) {
        _state.update { s ->
            s.copy(
                drops = s.drops.map { if (it.id == id) transform(it) else it },
                archive = s.archive.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    /** Shares one drop. Failures keep it for Retry with the same `client_drop_id`. */
    fun share(
        jpeg: ByteArray,
        audience: SharedDropAudience,
        caption: String?,
    ) = post(PendingSharedDrop(newClientDropId(), jpeg, audience, caption?.trim()?.takeIf { it.isNotEmpty() }))

    fun retryShare() {
        _state.value.pendingPost?.let(::post)
    }

    fun dismissPostError() {
        _state.update { it.copy(postError = null, pendingPost = null) }
    }

    private fun post(pending: PendingSharedDrop) {
        if (_state.value.posting) return
        _state.update { it.copy(posting = true, postError = null, pendingPost = pending) }
        viewModelScope.launch {
            val preview = clickDropPreviewJpeg(pending.jpeg)
            val size = encodedImageSize(pending.jpeg)
            val result =
                if (preview == null) {
                    Result.failure(SharedDropPostException.InvalidPhoto())
                } else {
                    api.share(pending.jpeg, preview, size?.first, size?.second, pending.audience, pending.caption, pending.clientDropId)
                }
            result
                .onSuccess { drop ->
                    writeChatMediaVaultFile(previewKey(drop.id), preview!!, "jpg")
                    // You already have your own original.
                    writeChatMediaVaultFile(originalKey(drop.id), pending.jpeg, "jpg")
                    _previews.update { it + (drop.id to preview) }
                    _state.update { s ->
                        s.copy(
                            posting = false,
                            pendingPost = null,
                            drops =
                                listOf(drop) + s.drops.filterNot { it.id == drop.id },
                        )
                    }
                    persistStrip()
                }.onFailure { error ->
                    val message = (error as? SharedDropPostException)?.message ?: "Couldn't share your drop. Try again."
                    // Past the cap a retry can't succeed: drop the pending photo.
                    _state.update {
                        it.copy(
                            posting = false,
                            postError = message,
                            pendingPost = if (error is SharedDropPostException) null else pending,
                        )
                    }
                }
        }
    }

    fun delete(drop: SharedDrop) {
        viewModelScope.launch {
            api
                .delete(drop.id)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            drops = s.drops.filterNot { it.id == drop.id },
                            archive =
                                s.archive.filterNot {
                                    it.id ==
                                        drop.id
                                },
                        )
                    }
                    persistStrip()
                }.onFailure { _state.update { it.copy(notice = "Couldn't delete it. Try again.") } }
        }
    }

    fun report(
        drop: SharedDrop,
        reason: String,
    ) {
        viewModelScope.launch {
            drops.report(ClickDropKind.SHARED, drop.id, reason.ifBlank { "Reported from the story viewer" })
            _state.update { it.copy(notice = "Thanks. We'll take a look.") }
        }
    }

    fun loadReactions(id: String) {
        viewModelScope.launch {
            reactionsApi.reactions(ReactionTarget.SHARED_DROP, id).onSuccess { r -> replaceDrop(id) { it.copy(reactions = r) } }
        }
    }

    /**
     * Reacts (or, with null, takes it back). The pick shows at once; a failure puts the old one back.
     * A new reaction also answers in the 1-1 chat, carrying the drop (`metadata.drop_reply`).
     */
    fun react(
        drop: SharedDrop,
        emoji: String?,
    ) {
        val previous = drop.reactions
        replaceDrop(drop.id) { it.copy(reactions = (previous ?: ReactionsState()).copy(mine = emoji)) }
        viewModelScope.launch {
            reactionsApi
                .react(ReactionTarget.SHARED_DROP, drop.id, emoji)
                .onSuccess { saved ->
                    replaceDrop(drop.id) { it.copy(reactions = saved) }
                    if (emoji != null) sendReply(drop, emoji, isReaction = true)
                }.onFailure { replaceDrop(drop.id) { it.copy(reactions = previous) } }
        }
    }

    /** "Reply to {name}": into the 1-1 chat with the poster. */
    fun sendReply(
        drop: SharedDrop,
        text: String,
        isReaction: Boolean = false,
    ) {
        val body = text.trim()
        val connectionId = drop.connectionId ?: return
        val uid = userId() ?: return
        if (body.isEmpty()) return
        viewModelScope.launch {
            val sent =
                chatRepository.sendMessageForConnection(
                    connectionId = connectionId,
                    userId = uid,
                    content = body,
                    messageType = ChatMessageType.TEXT,
                    metadata = buildJsonObject { put(ChatDropReply.METADATA_KEY, ChatDropReply(drop.id, isReaction).wire()) },
                )
            val first = drop.user.firstName
            _state.update {
                it.copy(
                    notice =
                        if (sent ==
                            null
                        ) {
                            "Couldn't send. Try again."
                        } else if (isReaction) {
                            "Reaction sent to $first"
                        } else {
                            "Sent to $first"
                        },
                )
            }
        }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** The archive behind "View all", a page at a time. */
    fun loadMoreArchive(reset: Boolean = false) {
        val s = _state.value
        if (s.archiveLoading || (!reset && s.archiveEnd)) return
        _state.update { it.copy(archiveLoading = true) }
        viewModelScope.launch {
            api
                .archive(if (reset) null else s.archiveNextBefore)
                .onSuccess { page ->
                    _state.update {
                        val merged =
                            if (reset) {
                                page.drops
                            } else {
                                it.archive +
                                    page.drops.filter { d -> it.archive.none { a -> a.id == d.id } }
                            }
                        it.copy(
                            archive = merged,
                            archiveNextBefore = page.nextBefore,
                            archiveEnd = page.nextBefore == null,
                            archiveLoading = false,
                        )
                    }
                    page.drops.forEach { warmImages(it) }
                }.onFailure { _state.update { it.copy(archiveLoading = false) } }
        }
    }

    companion object {
        /** The cached strip is bounded (sheet 07 G1-5). */
        const val STRIP_CACHE_MAX = 240
    }

    private fun newClientDropId(): String {
        val bytes = Random.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
