package compose.project.click.click.collaboration

import compose.project.click.click.core.FeatureFlagGate
import compose.project.click.click.data.api.ClickDropService
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageDeliveryState
import compose.project.click.click.data.models.clickDropRevealAt
import compose.project.click.click.data.models.isDisposableRoll
import compose.project.click.click.data.models.isGatedDrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * A chat timeline's Click Drop develop flow (spec §2; iOS `ConversationModel` "Click Drop develop"):
 * per-viewer developed state from the server, tap to develop, "Develop all", live develop at zero,
 * and the signed URLs gated originals come back with.
 *
 * Gated drops always use it, whatever this device's flag says (their media is only the preview);
 * legacy drops do only when `drops_develop` is on, and otherwise keep developing by themselves.
 */
class ClickDropDevelopController(
    private val service: ClickDropService,
    private val isDevelopFlagOn: () -> Boolean = { FeatureFlagGate.isEnabled(ClickDropFlags.DROPS_DEVELOP) },
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val _developedAt = MutableStateFlow<Map<String, Instant>>(emptyMap())

    /** This viewer's developed-at time per message ID (on any device). */
    val developedAt: StateFlow<Map<String, Instant>> = _developedAt.asStateFlow()

    private val _developing = MutableStateFlow<Set<String>>(emptySet())
    val developing: StateFlow<Set<String>> = _developing.asStateFlow()

    private val _freshlyDeveloped = MutableStateFlow<Set<String>>(emptySet())

    /** Developed in this session and not yet animated by their bubble. */
    val freshlyDeveloped: StateFlow<Set<String>> = _freshlyDeveloped.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val signedUrls = mutableMapOf<String, String>()

    fun usesDevelopFlow(message: Message): Boolean =
        message.isDisposableRoll() &&
            message.messageType.lowercase() == ChatMessageType.IMAGE &&
            (message.isGatedDrop() || isDevelopFlagOn())

    /** The develop state for a drop in the flow; null for anything else (legacy drops outside the flag). */
    fun state(
        message: Message,
        developed: Map<String, Instant> = _developedAt.value,
        now: Instant = clock(),
    ): ClickDropDevelopState? {
        if (!usesDevelopFlow(message)) return null
        if (message.deliveryState == MessageDeliveryState.PENDING || message.deliveryState == MessageDeliveryState.ERROR) {
            return ClickDropDevelopState.Pending(message.clickDropRevealAt())
        }
        return ClickDropDevelopState.resolve(message.clickDropRevealAt(), developed[message.id], now)
    }

    /** Ready drops on screen, oldest first ("Develop all"). */
    fun readyDrops(
        messages: List<Message>,
        now: Instant = clock(),
    ): List<Message> = messages.filter { state(it, now = now) == ClickDropDevelopState.Ready }

    /** Loads which revealed drops this viewer already developed (on any device). */
    suspend fun refresh(messages: List<Message>) {
        val now = clock()
        val ids =
            messages
                .filter { message ->
                    state(message, now = now) == ClickDropDevelopState.Ready && message.id !in _developedAt.value
                }.map { it.id }
                .filterNot { it.startsWith("temp-") }
        if (ids.isEmpty()) return
        val developed = runCatching { service.developedAt(ClickDropKind.CHAT, ids) }.getOrNull() ?: return
        _developedAt.update { current -> developed + current }
    }

    /** Tap to develop, "Develop all", or live develop at zero. Pending drops stay pending. */
    suspend fun develop(targets: List<Message>) {
        val now = clock()
        val ready = targets.filter { state(it, now = now) == ClickDropDevelopState.Ready && it.id !in _developing.value }
        if (ready.isEmpty()) return
        val ids = ready.map { it.id }.toSet()
        _developing.update { it + ids }
        try {
            val results = service.develop(ready.map { ClickDropRef(ClickDropKind.CHAT, it.id) })
            val developed = results.filter { it.status == ClickDropDevelopResult.Status.DEVELOPED }
            developed.forEach { result -> result.originalUrl?.let { signedUrls[result.ref.id] = it } }
            _developedAt.update { current -> current + developed.associate { it.ref.id to (it.developedAt ?: now) } }
            _freshlyDeveloped.update { it + developed.map { result -> result.ref.id } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _error.value = "Couldn't develop right now. Try again in a moment."
        } finally {
            _developing.update { it - ids }
        }
    }

    /**
     * Live develop: drops that reached zero in the last few seconds while this chat is on screen
     * develop by themselves. False when none did.
     */
    suspend fun developRevealedWhileVisible(messages: List<Message>): Boolean {
        val now = clock()
        val justRevealed =
            readyDrops(messages, now).filter { message ->
                val reveal = message.clickDropRevealAt() ?: return@filter false
                (now - reveal).inWholeSeconds < LIVE_DEVELOP_WINDOW_SECONDS
            }
        if (justRevealed.isEmpty()) return false
        develop(justRevealed)
        return true
    }

    /** The bubble finished its develop animation. */
    fun didShowDevelop(id: String) {
        _freshlyDeveloped.update { it - id }
    }

    fun clearError() {
        _error.value = null
    }

    /** The signed URL a develop call issued for a gated original (expires after ~10 minutes). */
    fun signedOriginalUrl(id: String): String? = signedUrls[id]

    /** Developing again is idempotent and issues a fresh signed URL (no URL yet, or an expired one). */
    suspend fun refreshSignedOriginalUrl(id: String): String? {
        val fresh =
            runCatching { service.develop(listOf(ClickDropRef(ClickDropKind.CHAT, id))) }
                .getOrNull()
                ?.firstOrNull { it.status == ClickDropDevelopResult.Status.DEVELOPED }
                ?.originalUrl
                ?: return null
        signedUrls[id] = fresh
        return fresh
    }

    /** Server ID swap after an optimistic send: carry state over. */
    fun markDevelopedLocally(id: String) {
        _developedAt.update { it + (id to clock()) }
    }

    fun reset() {
        _developedAt.value = emptyMap()
        _developing.value = emptySet()
        _freshlyDeveloped.value = emptySet()
        _error.value = null
        signedUrls.clear()
    }

    companion object {
        const val LIVE_DEVELOP_WINDOW_SECONDS = 5L
    }
}
