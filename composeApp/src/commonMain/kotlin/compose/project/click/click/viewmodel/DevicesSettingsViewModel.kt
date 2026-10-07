package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.DeviceApi // pragma: allowlist secret
import compose.project.click.click.data.DeviceApprovals // pragma: allowlist secret
import compose.project.click.click.data.api.MyDeviceDto // pragma: allowlist secret
import compose.project.click.click.data.thisChatDeviceId // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Settings › Devices: the account's active devices, with Remove for the others. */
class DevicesSettingsViewModel(
    private val api: DeviceApi = DeviceApprovals.api,
    private val myDeviceId: () -> String? = ::thisChatDeviceId,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val devices: List<MyDeviceDto> = emptyList(),
        val thisDeviceId: String? = null,
        val loadFailed: Boolean = false,
        val removing: Set<String> = emptySet(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, loadFailed = false, thisDeviceId = myDeviceId())
            api.myDevices().fold(
                onSuccess = { devices ->
                    _state.value = _state.value.copy(loading = false, devices = sortDevicesForSettings(devices, _state.value.thisDeviceId))
                },
                onFailure = { _state.value = _state.value.copy(loading = false, loadFailed = true) },
            )
        }
    }

    /** Signs [deviceId] out of secure messaging. This device can't remove itself (sign out instead). */
    fun remove(deviceId: String) {
        val current = _state.value
        if (deviceId == current.thisDeviceId || deviceId in current.removing) return
        _state.value = current.copy(removing = current.removing + deviceId, error = null)
        viewModelScope.launch {
            api.removeDevice(deviceId).fold(
                onSuccess = {
                    val s = _state.value
                    _state.value = s.copy(devices = s.devices.filterNot { it.deviceId == deviceId }, removing = s.removing - deviceId)
                },
                onFailure = {
                    val s = _state.value
                    _state.value = s.copy(removing = s.removing - deviceId, error = "Couldn't remove that device. Try again.")
                },
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

/** This device first, then the server's order (most recently seen first). */
internal fun sortDevicesForSettings(
    devices: List<MyDeviceDto>,
    thisDeviceId: String?,
): List<MyDeviceDto> = devices.filter { it.deviceId == thisDeviceId } + devices.filterNot { it.deviceId == thisDeviceId }
