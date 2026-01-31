package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Connection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class MapState {
    object Loading : MapState()
    data class Success(val connections: List<Connection>) : MapState()
    data class Error(val message: String) : MapState()
}

class MapViewModel : ViewModel() {

    private val _mapState = MutableStateFlow<MapState>(MapState.Loading)
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    init {
        observeAppData()
    }

    private fun observeAppData() {
        viewModelScope.launch {
            combine(
                AppDataManager.connections,
                AppDataManager.isDataLoaded,
                AppDataManager.isLoading
            ) { connections, isDataLoaded, isLoading ->
                Triple(connections, isDataLoaded, isLoading)
            }.collectLatest { (connections, isDataLoaded, isLoading) ->
                when {
                    isDataLoaded -> {
                        _mapState.value = MapState.Success(connections)
                    }
                    isLoading -> {
                        _mapState.value = MapState.Loading
                    }
                    else -> {
                        _mapState.value = MapState.Success(emptyList())
                    }
                }
            }
        }
    }

    fun loadConnections() {
        // Data is now loaded automatically via AppDataManager.initializeData()
        // but we can trigger a refresh if needed.
        if (!AppDataManager.isDataLoaded.value) {
            AppDataManager.initializeData()
        }
    }

    fun refresh() {
        AppDataManager.refresh(force = true)
    }
}
