package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.HangoutPendingExistsException // pragma: allowlist secret
import compose.project.click.click.data.api.LogHangoutBody // pragma: allowlist secret
import compose.project.click.click.data.api.PendingHangoutDto // pragma: allowlist secret
import compose.project.click.click.data.models.UpcomingPlan // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Relationship state for one peer profile (iOS `PeerProfileModel`): hangouts waiting on a
 * confirmation, upcoming plans in your chat, and the Log hangout / Confirm / Not us / Wave actions.
 * Encounters themselves come from the profile load; [encountersChanged] tells the sheet to refetch
 * after a confirmation records a new one.
 */
class PeerFriendshipViewModel(
    private val connectionId: String,
    private val peerFirstName: String,
    private val api: ApiClient = ApiClient(),
    private val chatRepository: ChatRepository = SupabaseChatRepository(createTokenStorage()),
) : ViewModel() {
    private val _pendingHangouts = MutableStateFlow<List<PendingHangoutDto>>(emptyList())
    val pendingHangouts: StateFlow<List<PendingHangoutDto>> = _pendingHangouts.asStateFlow()

    private val _upcomingPlans = MutableStateFlow<List<UpcomingPlan>>(emptyList())
    val upcomingPlans: StateFlow<List<UpcomingPlan>> = _upcomingPlans.asStateFlow()

    /** One-shot message for a toast; clear with [clearNotice]. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Increments when encounters changed server-side (a hangout was confirmed). */
    private val _encountersChanged = MutableStateFlow(0)
    val encountersChanged: StateFlow<Int> = _encountersChanged.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun refresh(chatId: String?) {
        viewModelScope.launch {
            api.getPendingHangouts().onSuccess { all ->
                _pendingHangouts.value = all.filter { it.connectionId == connectionId }
            }
        }
        val chat = chatId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            _upcomingPlans.value =
                chatRepository.fetchUpcomingPlans(chat, Clock.System.now().toEpochMilliseconds()).take(3)
        }
    }

    fun confirm(hangoutId: String) {
        val before = _pendingHangouts.value
        _pendingHangouts.value = before.filterNot { it.id == hangoutId }
        viewModelScope.launch {
            api
                .confirmHangout(hangoutId)
                .onSuccess { result ->
                    _notice.value =
                        when {
                            result.alreadyLogged -> "Already logged. You two are on the map."
                            result.status == "confirmed" -> "Hangout confirmed"
                            else -> "Confirmed. Waiting on $peerFirstName."
                        }
                    if (result.status == "confirmed") _encountersChanged.value += 1
                }.onFailure {
                    _pendingHangouts.value = before
                    _notice.value = "Couldn't confirm that hangout"
                }
        }
    }

    fun decline(hangoutId: String) {
        val before = _pendingHangouts.value
        _pendingHangouts.value = before.filterNot { it.id == hangoutId }
        viewModelScope.launch {
            api.declineHangout(hangoutId).onFailure {
                _pendingHangouts.value = before
                _notice.value = "Couldn't update that hangout"
            }
        }
    }

    /** Logs a hangout (you're pre-confirmed; [peerFirstName] is asked). Returns true on success. */
    fun logHangout(
        occurredAtEpochMs: Long,
        placeName: String?,
        latitude: Double?,
        longitude: Double?,
        onDone: (Boolean) -> Unit = {},
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result =
                api.logHangout(
                    LogHangoutBody(
                        connectionId = connectionId,
                        occurredAt = Instant.fromEpochMilliseconds(occurredAtEpochMs).toString(),
                        lat = latitude?.takeIf { longitude != null },
                        lon = longitude?.takeIf { latitude != null },
                        locationName = placeName?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
                    ),
                )
            _busy.value = false
            result
                .onSuccess { hangout ->
                    _pendingHangouts.value = listOf(hangout) + _pendingHangouts.value.filterNot { it.id == hangout.id }
                    _notice.value = "Logged. $peerFirstName will be asked to confirm."
                    onDone(true)
                }.onFailure { error ->
                    _notice.value =
                        if (error is HangoutPendingExistsException) {
                            "A hangout with $peerFirstName is already waiting to be confirmed."
                        } else {
                            "Couldn't log that hangout"
                        }
                    onDone(false)
                }
        }
    }

    fun wave() {
        viewModelScope.launch {
            api
                .wave(connectionId)
                .onSuccess { _notice.value = if (it.alreadyWavedToday) "You already waved today" else "You waved at $peerFirstName 👋" }
                .onFailure { _notice.value = "Couldn't wave right now" }
        }
    }
}
