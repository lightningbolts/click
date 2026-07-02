package compose.project.click.click.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

actual class NetworkConnectivityMonitor actual constructor() : ConnectivityMonitor {
    private val _isOnline = MutableStateFlow(false)
    actual override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var monitor: platform.Network.nw_path_monitor_t? = null

    actual override fun start() {
        if (monitor != null) return

        val pathMonitor = nw_path_monitor_create()
        monitor = pathMonitor
        val queue = dispatch_queue_create("click.network.monitor", null)
        nw_path_monitor_set_queue(pathMonitor, queue)
        nw_path_monitor_set_update_handler(pathMonitor) { path ->
            val online = path?.let { nw_path_get_status(it) == nw_path_status_satisfied } ?: false
            _isOnline.value = online
        }
        nw_path_monitor_start(pathMonitor)
    }

    actual override fun stop() {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
    }
}
