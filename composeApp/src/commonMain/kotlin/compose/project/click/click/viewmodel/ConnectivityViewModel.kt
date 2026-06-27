package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.network.NetworkConnectivityMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes platform network reachability for offline banners and guarded network actions.
 */
class ConnectivityViewModel : ViewModel() {
    private val monitor = NetworkConnectivityMonitor()

    val isOnline: StateFlow<Boolean> = monitor.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = monitor.isOnline.value,
    )

    init {
        monitor.start()
    }

    override fun onCleared() {
        monitor.stop()
        super.onCleared()
    }
}
