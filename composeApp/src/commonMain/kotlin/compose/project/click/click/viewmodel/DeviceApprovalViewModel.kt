package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.chat.DeviceApprovalError // pragma: allowlist secret
import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.DeviceApi // pragma: allowlist secret
import compose.project.click.click.data.DeviceApprovals // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebCodedException // pragma: allowlist secret
import compose.project.click.click.data.decideDeviceApproval // pragma: allowlist secret
import compose.project.click.click.data.thisChatDeviceId // pragma: allowlist secret
import compose.project.click.click.data.unwrapWithThisDevice // pragma: allowlist secret
import compose.project.click.click.notifications.cancelDeviceApprovalNotification // pragma: allowlist secret
import compose.project.click.click.platform.DEVICE_APPROVAL_AUTH_REASON // pragma: allowlist secret
import compose.project.click.click.platform.OwnerAuthResult // pragma: allowlist secret
import compose.project.click.click.platform.authenticateDeviceOwner // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Approve new sign-in?" for one request (iOS `DeviceApprovalSheet`). Approve asks the device
 * owner to confirm it's them every time (Deny never does); the challenge is fetched meanwhile and
 * the decision is posted only after the owner confirms on the same account (sheet 10 §A1).
 */
class DeviceApprovalViewModel(
    private val requestId: String,
    private val api: DeviceApi = DeviceApprovals.api,
    private val myDeviceId: () -> String? = ::thisChatDeviceId,
    private val unwrap: (MessageCryptoV2.EpochKeyWrapMetadata, String) -> ByteArray = ::unwrapWithThisDevice,
    /** The signed-in user now; the sheet belongs to whoever it was when it opened. */
    private val currentUserId: () -> String? = { AppDataManager.currentUser.value?.id },
    private val authenticate: suspend (reason: String) -> OwnerAuthResult = ::authenticateDeviceOwner,
    /** Outlives the sheet: sharing history after approval keeps going when it closes (§A2). */
    private val backgroundScope: CoroutineScope = AppDataManager.scope,
    private val dismissNotification: (requestId: String) -> Unit = ::cancelDeviceApprovalNotification,
    /** After approving: share this device's history with the approved one right away. */
    private val onApproved: suspend () -> Unit = {},
) : ViewModel() {
    sealed interface Phase {
        data object Loading : Phase

        data object Asking : Phase

        data object Approving : Phase

        data object Denying : Phase

        data object Approved : Phase

        data object Denied : Phase

        data class Failed(
            val error: DeviceApprovalError,
        ) : Phase
    }

    data class UiState(
        val phase: Phase = Phase.Loading,
        val deviceLabel: String? = null,
        val createdAt: String? = null,
        /** The request was found and is waiting; false while loading or when loading failed. */
        val loaded: Boolean = false,
    ) {
        val busy: Boolean get() = phase == Phase.Approving || phase == Phase.Denying
        val finished: Boolean get() =
            phase == Phase.Approved ||
                phase == Phase.Denied ||
                (phase as? Phase.Failed)?.error?.retryable == false
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val openedForUserId: String? = currentUserId()

    /** The owner dismissed the prompt, or the account changed while it was up: back to asking. */
    private class OwnerAuthCancelled : Exception()

    /** No screen lock, or the confirmation failed: fail closed. */
    private class OwnerAuthUnavailable : Exception()

    init {
        viewModelScope.launch { load() }
    }

    /** The label comes from the incoming list (`GET …/{id}` has none); a decided request fails closed. */
    internal suspend fun load() {
        DeviceApprovals.incoming(requestId)?.let {
            _state.value = UiState(Phase.Asking, it.deviceLabel, it.createdAt, loaded = true)
            return
        }
        val deviceId = myDeviceId()
        val listed =
            deviceId?.let { id ->
                api
                    .historyRequests(id)
                    .getOrNull()
                    ?.incoming
                    ?.firstOrNull { it.id == requestId }
            }
        if (listed != null) {
            _state.value = UiState(Phase.Asking, listed.deviceLabel, listed.createdAt, loaded = true)
            return
        }
        api.historyRequest(requestId).fold(
            onSuccess = { request ->
                _state.value =
                    when {
                        request == null -> UiState(Phase.Failed(DeviceApprovalError.NotFound))
                        !request.isPending -> UiState(Phase.Failed(DeviceApprovalError.AlreadyDecided))
                        request.expired -> UiState(Phase.Failed(DeviceApprovalError.Expired))
                        else -> UiState(Phase.Asking, request.deviceLabel, request.createdAt, loaded = true)
                    }
            },
            onFailure = { _state.value = UiState(Phase.Failed(DeviceApprovalError.from(it))) },
        )
    }

    fun approve() = decide(approve = true)

    fun deny() = decide(approve = false)

    private fun decide(approve: Boolean) {
        val current = _state.value
        if (current.busy || current.finished || !current.loaded) return
        val userId = openedForUserId ?: return
        val deviceId =
            myDeviceId() ?: run {
                _state.value = current.copy(phase = Phase.Failed(DeviceApprovalError.Unknown))
                return
            }
        _state.value = current.copy(phase = if (approve) Phase.Approving else Phase.Denying)
        val authorize: (suspend () -> Unit)? =
            if (approve) {
                {
                    when (authenticate(DEVICE_APPROVAL_AUTH_REASON)) {
                        // A session switch while the prompt was up must never approve for the old account.
                        OwnerAuthResult.Success -> if (currentUserId() != userId) throw OwnerAuthCancelled()
                        OwnerAuthResult.Cancelled -> throw OwnerAuthCancelled()
                        OwnerAuthResult.Unavailable -> throw OwnerAuthUnavailable()
                    }
                }
            } else {
                null
            }
        viewModelScope.launch {
            val result =
                try {
                    decideDeviceApproval(api, requestId, approve, deviceId, authorize = authorize, unwrap = unwrap)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            result.fold(
                onSuccess = {
                    DeviceApprovals.decided(requestId)
                    dismissNotification(requestId)
                    _state.value = _state.value.copy(phase = if (approve) Phase.Approved else Phase.Denied)
                    if (approve) backgroundScope.launch { runCatching { onApproved() } }
                },
                onFailure = { e ->
                    when (e) {
                        is OwnerAuthCancelled -> _state.value = _state.value.copy(phase = Phase.Asking)
                        is OwnerAuthUnavailable ->
                            _state.value = _state.value.copy(phase = Phase.Failed(DeviceApprovalError.AuthenticationRequired))
                        else -> {
                            val error = DeviceApprovalError.from(e)
                            if (!error.retryable && e is ClickWebCodedException) {
                                DeviceApprovals.decided(requestId)
                                dismissNotification(requestId)
                            }
                            _state.value = _state.value.copy(phase = Phase.Failed(error))
                        }
                    }
                },
            )
        }
    }

    /** "Try again" after a retryable failure. */
    fun retry() {
        val phase = _state.value.phase
        if (phase !is Phase.Failed || !phase.error.retryable) return
        if (_state.value.loaded) {
            _state.value = _state.value.copy(phase = Phase.Asking)
        } else {
            _state.value = UiState()
            viewModelScope.launch { load() }
        }
    }
}
