package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.network.NetworkConnectivityMonitor
import compose.project.click.click.telemetry.ConnectionFlowTelemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the top-of-screen sync chip says (iOS `OfflineNotice`, F50). */
enum class SyncBanner {
    None,

    /** No network, or click-web can't be reached at all. */
    Offline,

    /** The network is up and click-web answers, but the last refresh failed: offer Retry. */
    RefreshFailed,
}

/**
 * [serverReachable] is null until a reachability check has run for the current error.
 */
fun syncBannerState(
    offlineDebounced: Boolean,
    loadError: String?,
    serverReachable: Boolean?,
): SyncBanner =
    when {
        offlineDebounced -> SyncBanner.Offline
        loadError == null -> SyncBanner.None
        serverReachable == false -> SyncBanner.Offline
        serverReachable == true -> SyncBanner.RefreshFailed
        else -> SyncBanner.None
    }

/**
 * Exposes platform network reachability for offline banners and guarded network actions.
 */
class ConnectivityViewModel : ViewModel() {
    private val monitor = NetworkConnectivityMonitor()
    private val api by lazy { ApiClient() }

    val isOnline: StateFlow<Boolean> =
        monitor.isOnline.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = monitor.isOnline.value,
        )

    private val _showOfflineBanner = MutableStateFlow(false)
    val showOfflineBanner: StateFlow<Boolean> = _showOfflineBanner.asStateFlow()

    private val serverReachable = MutableStateFlow<Boolean?>(null)

    val syncBanner: StateFlow<SyncBanner> =
        combine(_showOfflineBanner, AppDataManager.error, serverReachable) { offline, error, reachable ->
            syncBannerState(offline, error, reachable)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncBanner.None)

    init {
        monitor.start()
        viewModelScope.launch {
            monitor.isOnline.collect { online ->
                if (online) {
                    _showOfflineBanner.value = false
                    ConnectionFlowTelemetry.flush()
                } else {
                    delay(OFFLINE_BANNER_DELAY_MS)
                    if (!monitor.isOnline.value) {
                        _showOfflineBanner.value = true
                    }
                }
            }
        }
        // A failed load: ping click-web (`/api/ping`) to tell "offline" from "refresh failed".
        viewModelScope.launch {
            AppDataManager.error.collect { error ->
                serverReachable.value = null
                if (error != null) serverReachable.value = api.isClickWebReachable()
            }
        }
    }

    fun retryRefresh() {
        AppDataManager.refresh(force = true)
    }

    override fun onCleared() {
        monitor.stop()
        super.onCleared()
    }

    private companion object {
        const val OFFLINE_BANNER_DELAY_MS = 700L
    }
}
