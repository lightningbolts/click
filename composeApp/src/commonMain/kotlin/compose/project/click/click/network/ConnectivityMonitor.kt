package compose.project.click.click.network

import kotlinx.coroutines.flow.StateFlow

/** Platform-agnostic network reachability for send/offline UX. */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()
}
