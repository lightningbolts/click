package compose.project.click.click.data.places

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One foreground location read for a single tap (no background location, ever: spec §0.2). */
sealed class PlaceLocationOutcome {
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double?,
    ) : PlaceLocationOutcome()

    data object Denied : PlaceLocationOutcome()

    data object Unavailable : PlaceLocationOutcome()
}

sealed class PlaceLoadState {
    data object Idle : PlaceLoadState()

    data object Loading : PlaceLoadState()

    data object Loaded : PlaceLoadState()

    data class Failed(
        val error: PlaceError,
    ) : PlaceLoadState()
}

sealed class PlaceCheckInPhase {
    data object Idle : PlaceCheckInPhase()

    data object Locating : PlaceCheckInPhase()

    data object Submitting : PlaceCheckInPhase()

    data class Failed(
        val error: PlaceError,
    ) : PlaceCheckInPhase()

    val busy: Boolean get() = this == Locating || this == Submitting
}

sealed class PlacePulsePhase {
    data object Idle : PlacePulsePhase()

    data object Submitting : PlacePulsePhase()

    data class Submitted(
        val pulseId: String,
        val editableUntilMs: Long?,
    ) : PlacePulsePhase()

    data class Failed(
        val error: PlaceError,
    ) : PlacePulsePhase()
}

/** Everything the Place page renders; derived values are pure so they can be unit tested. */
data class PlaceDetailUiState(
    val detail: PlaceDetail? = null,
    val load: PlaceLoadState = PlaceLoadState.Idle,
    val checkIn: PlaceCheckInPhase = PlaceCheckInPhase.Idle,
    val pulse: PlacePulsePhase = PlacePulsePhase.Idle,
    /** From a check-in QR code: sent with the check-in. */
    val pendingAnchorToken: String? = null,
    /** "Check in at …?" for a QR check-in (never checks in without the tap). */
    val showAnchorConfirmation: Boolean = false,
    /** "Come back at this time?" after Leave. */
    val showWouldReturn: Boolean = false,
    /** "Let my Clicks see I'm here": this check-in only, off by default. */
    val shareWithConnections: Boolean = false,
    val answeredFollowUps: Set<String> = emptySet(),
) {
    val isCheckedIn: Boolean get() = detail?.checkIn?.isActive == true
    val isManager: Boolean get() = detail?.isManager == true
    val pulseSubmitted: Boolean get() = pulse is PlacePulsePhase.Submitted

    /** Shown when the user can Pulse, or to explain the cooldown; hidden for managers. */
    val showsPulseCard: Boolean
        get() {
            val eligibility = detail?.pulseEligibility ?: return false
            if (isManager) return false
            return eligibility.canPulse || eligibility.reason == "cooldown" || pulseSubmitted
        }

    /** Chips are disabled during the cooldown and while a Pulse is in flight. */
    val energyChipsEnabled: Boolean
        get() {
            val eligibility = detail?.pulseEligibility ?: return false
            return eligibility.canPulse && pulse != PlacePulsePhase.Submitting && !pulseSubmitted
        }

    val energyPrompt: String
        get() =
            detail
                ?.pulseEligibility
                ?.questions
                ?.firstOrNull { it.key == "energy" }
                ?.prompt ?: "How's the energy?"

    val wouldReturnQuestion: PulseQuestionDto?
        get() = detail?.pulseEligibility?.leavingQuestions?.firstOrNull { it.key == "would_return" }

    /** Follow-ups still to offer after an energy Pulse, while it is editable. */
    fun remainingFollowUps(nowMs: Long): List<PulseQuestionDto> {
        val submitted = pulse as? PlacePulsePhase.Submitted ?: return emptyList()
        if (submitted.editableUntilMs != null && submitted.editableUntilMs <= nowMs) return emptyList()
        return detail
            ?.pulseEligibility
            ?.questions
            .orEmpty()
            .filter { it.key != "energy" && it.key !in answeredFollowUps }
    }
}

private fun Throwable.placeError(): PlaceError = (this as? PlaceApiException)?.error ?: PlaceError.Network

/**
 * The Place page logic (§6.4, §6.7, §6.8): detail, check-in, Pulse. Mirrors iOS
 * `Click/Features/Places/PlaceDetailModel.swift`. Location is read once per tap through [locate];
 * the server owns the geofence and stores no coordinates.
 */
class PlaceDetailController(
    val idOrSlug: String,
    anchorToken: String?,
    private val repository: PlacesRepository,
    source: String? = null,
    private val appVersion: String? = null,
    private val locate: suspend () -> PlaceLocationOutcome,
) {
    private val source: String? = source ?: if (anchorToken == null) null else "qr"
    private var hasFetched = false

    private val _state =
        MutableStateFlow(
            PlaceDetailUiState(
                pendingAnchorToken = anchorToken,
                showAnchorConfirmation = anchorToken != null,
            ),
        )
    val state: StateFlow<PlaceDetailUiState> = _state.asStateFlow()

    private val placeId: String get() = _state.value.detail?.id ?: idOrSlug

    suspend fun load() {
        if (_state.value.detail == null) _state.update { it.copy(load = PlaceLoadState.Loading) }
        val result = repository.detail(idOrSlug, if (hasFetched) null else source)
        result.fold(
            onSuccess = { fresh ->
                hasFetched = true
                _state.update { it.copy(detail = fresh, load = PlaceLoadState.Loaded) }
            },
            onFailure = { e ->
                if (e is CancellationException) throw e
                // A refresh failure keeps what's on screen.
                if (_state.value.detail == null) _state.update { it.copy(load = PlaceLoadState.Failed(e.placeError())) }
            },
        )
    }

    fun setShareWithConnections(value: Boolean) = _state.update { it.copy(shareWithConnections = value) }

    suspend fun confirmAnchorCheckIn() {
        _state.update { it.copy(showAnchorConfirmation = false) }
        checkIn()
    }

    fun dismissAnchorConfirmation() = _state.update { it.copy(showAnchorConfirmation = false, pendingAnchorToken = null) }

    suspend fun checkIn() {
        if (_state.value.checkIn.busy) return
        _state.update { it.copy(checkIn = PlaceCheckInPhase.Locating) }
        val anchor = _state.value.pendingAnchorToken
        val fix = locate() as? PlaceLocationOutcome.Fix
        // A QR check-in still works without location (§4.4); GPS needs it.
        if (fix == null && anchor == null) {
            _state.update { it.copy(checkIn = PlaceCheckInPhase.Failed(PlaceError.NoLocation)) }
            return
        }
        _state.update { it.copy(checkIn = PlaceCheckInPhase.Submitting) }
        val body =
            PlacesRequests.checkInBody(
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                accuracyMeters = fix?.accuracyMeters,
                anchorToken = anchor,
                shareWithConnections = _state.value.shareWithConnections,
                appVersion = appVersion,
            )
        repository.checkIn(placeId, body).fold(
            onSuccess = { checkIn ->
                _state.update {
                    it.copy(
                        pendingAnchorToken = null,
                        detail = it.detail?.copy(checkIn = checkIn),
                        checkIn = PlaceCheckInPhase.Idle,
                    )
                }
                load()
            },
            onFailure = { e -> _state.update { it.copy(checkIn = PlaceCheckInPhase.Failed(e.placeError())) } },
        )
    }

    suspend fun checkOut() {
        repository.checkOut(placeId).fold(
            onSuccess = { ask ->
                _state.update {
                    it.copy(
                        detail = it.detail?.copy(checkIn = null),
                        pulse = PlacePulsePhase.Idle,
                        answeredFollowUps = emptySet(),
                    )
                }
                _state.update { it.copy(showWouldReturn = ask && it.wouldReturnQuestion != null) }
                load()
            },
            onFailure = { e -> _state.update { it.copy(checkIn = PlaceCheckInPhase.Failed(e.placeError())) } },
        )
    }

    suspend fun submitEnergy(energy: Int) {
        if (!_state.value.energyChipsEnabled) return
        _state.update { it.copy(pulse = PlacePulsePhase.Submitting) }
        repository.submitPulse(placeId, PlacesRequests.pulseBody(energy = energy)).fold(
            onSuccess = { result ->
                _state.update {
                    it.copy(
                        detail = it.detail?.let { d -> d.copy(summary = d.summary.copy(pulse = result.summary)) },
                        answeredFollowUps = emptySet(),
                        pulse = PlacePulsePhase.Submitted(result.pulseId, result.editableUntilMs),
                    )
                }
            },
            onFailure = { e ->
                val error = e.placeError()
                _state.update { it.copy(pulse = PlacePulsePhase.Failed(error)) }
                if (error is PlaceError.Cooldown) load()
            },
        )
    }

    suspend fun answerFollowUp(
        question: PulseQuestionDto,
        value: Int,
    ) {
        val submitted = _state.value.pulse as? PlacePulsePhase.Submitted ?: return
        _state.update { it.copy(answeredFollowUps = it.answeredFollowUps + question.key) }
        val body =
            PlacesRequests.pulsePatchBody(
                talkable = value.takeIf { question.key == "talkable" },
                categoryAnswer = value.takeIf { question.key == "category" },
            )
        // The window closed or the field was already set: just stop offering it.
        repository.updatePulse(placeId, submitted.pulseId, body).onSuccess { summary ->
            _state.update { it.copy(detail = it.detail?.let { d -> d.copy(summary = d.summary.copy(pulse = summary)) }) }
        }
    }

    /** null = dismissed without answering. */
    suspend fun answerWouldReturn(value: Int?) {
        _state.update { it.copy(showWouldReturn = false) }
        if (value == null) return
        repository.submitPulse(placeId, PlacesRequests.pulseBody(wouldReturn = value))
    }
}
