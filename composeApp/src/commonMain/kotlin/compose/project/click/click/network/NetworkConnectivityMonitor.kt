package compose.project.click.click.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes platform network reachability and signals when the device regains connectivity.
 */
expect class NetworkConnectivityMonitor() {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()
}
