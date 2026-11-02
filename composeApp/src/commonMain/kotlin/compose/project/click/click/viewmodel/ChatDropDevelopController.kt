package compose.project.click.click.viewmodel

import compose.project.click.click.collaboration.ClickDropDevelopBook
import compose.project.click.click.collaboration.ClickDropRevealRef
import compose.project.click.click.collaboration.parseInstantOrNull
import compose.project.click.click.data.api.DropDevelopResultDto
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.disposableRollRevealAtIso
import compose.project.click.click.data.models.isGatedDrop
import compose.project.click.click.util.isPersistedApiUuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** The server calls behind chat Click Drop develop (fakeable in tests). */
interface ChatDropDevelopService {
    /** `POST /api/drops/develop` for chat drops. */
    suspend fun develop(ids: List<String>): Result<List<DropDevelopResultDto>>

    /** `GET /api/drops/views?kind=chat`: this viewer's developed-at (ISO) per id. */
    suspend fun developedAt(ids: List<String>): Result<Map<String, String>>
}

/** A gated chat drop that went through the server (not an optimistic copy); null for anything else. */
fun Message.clickDropRevealRefOrNull(): ClickDropRevealRef? {
    if (!isGatedDrop() || !isPersistedApiUuid(id)) return null
    return ClickDropRevealRef(id, parseInstantOrNull(disposableRollRevealAtIso()))
}

internal const val CLICK_DROP_DEVELOP_FAILED = "Couldn't develop right now. Try again in a moment."

/**
 * Tap to develop, "Develop all" and live develop for gated chat Click Drops, mirroring iOS
 * `ConversationModel` (`develop`, `developDropsRevealedWhileVisible`, `refreshDropStates`,
 * `didShowDevelop`). Only the server develops a drop; this keeps its answers in [book] and hands each
 * developed drop's signed original URL to [onDeveloped].
 */
class ChatDropDevelopController(
    private val scope: CoroutineScope,
    private val service: ChatDropDevelopService,
    /** True when this device already holds the drop's original (developed here before). */
    private val hasLocalOriginal: (String) -> Boolean,
    private val onDeveloped: (id: String, signedUrl: String?) -> Unit,
    private val onError: (String) -> Unit,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val _book = MutableStateFlow(ClickDropDevelopBook())
    val book: StateFlow<ClickDropDevelopBook> = _book.asStateFlow()

    private val checksInFlight = mutableSetOf<String>()

    /**
     * Learns which revealed drops this viewer already developed: from the vault first, then one
     * `GET /api/drops/views` for the rest. Each drop is asked about once.
     */
    fun check(drops: List<ClickDropRevealRef>) {
        val at = now()
        val unknown = _book.value.needingCheck(drops, at).filter { it.id !in checksInFlight }
        if (unknown.isEmpty()) return
        val (local, remote) = unknown.partition { hasLocalOriginal(it.id) }
        if (local.isNotEmpty()) {
            _book.update { book -> local.fold(book) { b, drop -> b.markDevelopedLocally(drop.id, drop.revealAt ?: at) } }
        }
        if (remote.isEmpty()) return
        val ids = remote.map { it.id }
        checksInFlight += ids
        scope.launch {
            try {
                service
                    .developedAt(ids)
                    .onSuccess { developed ->
                        val parsed = developed.mapNotNull { (id, iso) -> parseInstantOrNull(iso)?.let { id to it } }.toMap()
                        _book.update { it.mergeViews(ids, parsed) }
                    }
                // A failed check stays unchecked, so the next look asks again.
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                checksInFlight -= ids.toSet()
            }
        }
    }

    /** Tap to develop, or "Develop all". Pending and already-developed drops are left alone. */
    fun develop(drops: List<ClickDropRevealRef>) {
        val ready = _book.value.ready(drops, now())
        if (ready.isEmpty()) return
        val ids = ready.map { it.id }
        _book.update { it.startDeveloping(ids) }
        scope.launch {
            try {
                service
                    .develop(ids)
                    .onSuccess { results ->
                        _book.update { it.applyDevelopResults(ids, results, now()) }
                        results
                            .filter { it.id in ids && it.status == ClickDropDevelopBook.DEVELOP_STATUS_DEVELOPED }
                            .forEach { onDeveloped(it.id, it.url) }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _book.update { it.failDeveloping(ids) }
                        onError(CLICK_DROP_DEVELOP_FAILED)
                    }
            } catch (e: CancellationException) {
                _book.update { it.failDeveloping(ids) }
                throw e
            }
        }
    }

    /** Live develop: a drop that reaches zero while it's on screen develops by itself. */
    fun developIfJustRevealed(drop: ClickDropRevealRef) {
        val due = _book.value.justRevealed(listOf(drop), now())
        if (due.isNotEmpty()) develop(due)
    }

    /** The bubble finished its develop animation. */
    fun shown(id: String) {
        _book.update { it.shown(id) }
    }

    /** Sign-out or account switch: nothing carries over. */
    fun clear() {
        checksInFlight.clear()
        _book.value = ClickDropDevelopBook()
    }
}
