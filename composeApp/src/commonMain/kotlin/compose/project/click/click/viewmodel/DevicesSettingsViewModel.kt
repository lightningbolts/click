package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.DeviceApi // pragma: allowlist secret
import compose.project.click.click.data.DeviceApprovals // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebCodedException // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceHistoryRequestDto // pragma: allowlist secret
import compose.project.click.click.data.api.MyDeviceDto // pragma: allowlist secret
import compose.project.click.click.data.thisChatDeviceId // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings › Devices (iOS `DevicesSettingsView`, sheet 10 §A5): the devices holding their own key to
 * this account's chats, with Remove for the others, and new sign-ins waiting for this device to
 * share its chat history. Removing a device is about chats, not sign-in.
 */
class DevicesSettingsViewModel(
    private val api: DeviceApi = DeviceApprovals.api,
    private val myDeviceId: () -> String? = ::thisChatDeviceId,
    private val isOnline: StateFlow<Boolean> = AppDataManager.networkConnectivityMonitor.isOnline,
    /** Hands a request to this page's review sheet so the global prompt doesn't also open. */
    private val deferApproval: (requestId: String) -> Unit = DeviceApprovals::defer,
) : ViewModel() {
    data class UiState(
        /** Null until the first successful load. */
        val devices: List<MyDeviceDto>? = null,
        val loading: Boolean = false,
        /** The last load failed (shown only while there's no list). */
        val loadFailed: Boolean = false,
        /** Other devices of this account waiting for this one to approve them. */
        val incoming: List<DeviceHistoryRequestDto> = emptyList(),
        val thisDeviceId: String? = null,
        val online: Boolean = true,
        /** "Couldn't remove Pixel 9. …" after a removal the server refused. */
        val error: String? = null,
        /** The request open in the review sheet. */
        val reviewing: String? = null,
    ) {
        /** This device first, then the server's order (most recently seen first). */
        val orderedDevices: List<MyDeviceDto> get() = sortDevicesForSettings(devices.orEmpty(), thisDeviceId)
    }

    private val _state = MutableStateFlow(UiState(online = isOnline.value))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            isOnline.collect { online ->
                _state.update { it.copy(online = online) }
                // Back online with nothing to show yet: try again by itself.
                if (online && _state.value.devices == null && _state.value.loadFailed) load()
            }
        }
    }

    /** On appear, pull-to-refresh, back online, and after the review sheet closes. */
    fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val deviceId = myDeviceId()
                _state.update { it.copy(loading = true, thisDeviceId = deviceId) }
                // Requests are extra: without them, the devices still show.
                val approvals = deviceId?.let { id -> async { api.historyRequests(id).getOrNull() } }
                val devices = api.myDevices()
                val incoming = approvals?.await()?.incoming
                _state.update { s ->
                    devices.fold(
                        onSuccess = { list ->
                            s.copy(devices = list.toList(), loading = false, loadFailed = false, incoming = incoming?.toList() ?: s.incoming)
                        },
                        onFailure = { s.copy(loading = false, loadFailed = true, incoming = incoming?.toList() ?: s.incoming) },
                    )
                }
            }
    }

    /**
     * Gone from the list at once; back where it was, with the reason, if the server says no.
     * Never this device, whose key the app is using.
     */
    fun remove(device: MyDeviceDto) {
        val current = _state.value
        val list = current.devices ?: return
        val index = list.indexOfFirst { it.deviceId == device.deviceId }
        if (device.deviceId == current.thisDeviceId || index < 0) return
        _state.value = current.copy(devices = list.filterNot { it.deviceId == device.deviceId }, error = null)
        viewModelScope.launch {
            api.removeDevice(device.deviceId).onFailure { e ->
                _state.update { s ->
                    val now = s.devices.orEmpty()
                    val restored =
                        if (now.any { it.deviceId == device.deviceId }) {
                            now
                        } else {
                            now.toMutableList().apply { add(index.coerceAtMost(size), device) }
                        }
                    s.copy(devices = restored, error = removeDeviceErrorMessage(device.label, e))
                }
            }
        }
    }

    /** "Review": opens the approval sheet here instead of the global prompt. */
    fun review(requestId: String) {
        deferApproval(requestId)
        _state.update { it.copy(reviewing = requestId) }
    }

    /** The sheet decided: the row goes at once. */
    fun reviewDecided(requestId: String) {
        _state.update { s -> s.copy(incoming = s.incoming.filterNot { it.id == requestId }) }
    }

    /** The sheet closed: reload, as the decision (or another device's) may have changed things. */
    fun reviewDismissed() {
        _state.update { it.copy(reviewing = null) }
        load()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

/** This device first, then the server's order (most recently seen first). */
internal fun sortDevicesForSettings(
    devices: List<MyDeviceDto>,
    thisDeviceId: String?,
): List<MyDeviceDto> = devices.filter { it.deviceId == thisDeviceId } + devices.filterNot { it.deviceId == thisDeviceId }

/** "Couldn't remove Pixel 9. {reason}" (iOS: the label, else "that device"). */
internal fun removeDeviceErrorMessage(
    label: String?,
    error: Throwable,
): String {
    val reason =
        error.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() && error is ClickWebCodedException }
            ?: "Check your connection and try again."
    return "Couldn't remove ${label ?: "that device"}. $reason"
}
