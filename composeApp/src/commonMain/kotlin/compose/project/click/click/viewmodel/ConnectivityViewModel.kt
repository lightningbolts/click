package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.network.NetworkConnectivityMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _showOfflineBanner = MutableStateFlow(false)
    val showOfflineBanner: StateFlow<Boolean> = _showOfflineBanner.asStateFlow()

    init {
        monitor.start()
        viewModelScope.launch {
            monitor.isOnline.collect { online ->
                if (online) {
                    _showOfflineBanner.value = false
                } else {
                    delay(OFFLINE_BANNER_DELAY_MS)
                    if (!monitor.isOnline.value) {
                        _showOfflineBanner.value = true
                    }
                }
            }
        }
    }

    override fun onCleared() {
        monitor.stop()
        super.onCleared()
    }

    private companion object {
        const val OFFLINE_BANNER_DELAY_MS = 700L
    }
}
