package compose.project.click.click.viewmodel

import compose.project.click.click.network.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Test double: deterministic online/offline for ViewModel unit tests. */
class FakeConnectivityMonitor(
    initialOnline: Boolean = true,
) : ConnectivityMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }

    override fun start() = Unit
    override fun stop() = Unit
}
