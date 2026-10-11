package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.chat.DeviceApprovalError // pragma: allowlist secret
import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.DeviceApi // pragma: allowlist secret
import compose.project.click.click.data.DeviceApprovals // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebCodedException // pragma: allowlist secret
import compose.project.click.click.data.decideDeviceApproval // pragma: allowlist secret
import compose.project.click.click.data.thisChatDeviceId // pragma: allowlist secret
import compose.project.click.click.data.unwrapWithThisDevice // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "Approve new sign-in?" for one request (iOS `DeviceApprovalSheet`). */
class DeviceApprovalViewModel(
    private val requestId: String,
    private val api: DeviceApi = DeviceApprovals.api,
    private val myDeviceId: () -> String? = ::thisChatDeviceId,
    private val unwrap: (MessageCryptoV2.EpochKeyWrapMetadata, String) -> ByteArray = ::unwrapWithThisDevice,
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
        val deviceId =
            myDeviceId() ?: run {
                _state.value = current.copy(phase = Phase.Failed(DeviceApprovalError.Unknown))
                return
            }
        _state.value = current.copy(phase = if (approve) Phase.Approving else Phase.Denying)
        viewModelScope.launch {
            val result =
                try {
                    decideDeviceApproval(api, requestId, approve, deviceId, unwrap)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            result.fold(
                onSuccess = {
                    DeviceApprovals.decided(requestId)
                    _state.value = _state.value.copy(phase = if (approve) Phase.Approved else Phase.Denied)
                    if (approve) runCatching { onApproved() }
                },
                onFailure = { e ->
                    val error = DeviceApprovalError.from(e)
                    if (!error.retryable && e is ClickWebCodedException) DeviceApprovals.decided(requestId)
                    _state.value = _state.value.copy(phase = Phase.Failed(error))
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
