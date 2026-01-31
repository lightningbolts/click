package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.AvailabilityHelper
import compose.project.click.click.data.models.AvailabilityStatus
import compose.project.click.click.data.models.DayOfWeek
import compose.project.click.click.data.models.MutualAvailability
import compose.project.click.click.data.models.UserAvailability
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class AvailabilityViewModel(
    private val supabaseRepository: SupabaseRepository = SupabaseRepository()
) : ViewModel() {
    
    // Current user's availability from AppDataManager
    val currentAvailability: StateFlow<UserAvailability?> = AppDataManager.userAvailability
    
    // Availability status
    private val _availabilityStatus = MutableStateFlow(AvailabilityStatus.NOT_SET)
    val availabilityStatus: StateFlow<AvailabilityStatus> = _availabilityStatus.asStateFlow()
    
    // Mutual availabilities with connections
    private val _mutualAvailabilities = MutableStateFlow<List<MutualAvailability>>(emptyList())
    val mutualAvailabilities: StateFlow<List<MutualAvailability>> = _mutualAvailabilities.asStateFlow()
    
    // Loading state from AppDataManager
    val isLoading: StateFlow<Boolean> = AppDataManager.isLoading
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Show availability settings dialog
    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()
    
    init {
        // Observe availability changes to update status
        viewModelScope.launch {
            currentAvailability.collectLatest { availability ->
                _availabilityStatus.value = AvailabilityHelper.getAvailabilityStatus(availability)
            }
        }
    }
    
    /**
     * Toggle the "I'm free this week" status via AppDataManager
     */
    fun toggleFreeThisWeek() {
        AppDataManager.toggleFreeThisWeek()
    }
    
    /**
     * Update available days
     */
    fun updateAvailableDays(days: List<DayOfWeek>) {
        viewModelScope.launch {
            try {
                val currentUser = AppDataManager.currentUser.value ?: return@launch
                val dayNames = days.map { it.name.lowercase() }
                
                val current = currentAvailability.value
                val updated = current?.copy(
                    availableDays = dayNames,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                ) ?: UserAvailability(
                    userId = currentUser.id,
                    availableDays = dayNames,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                
                AppDataManager.updateUserAvailability(updated)
                supabaseRepository.updateUserAvailability(updated)
            } catch (e: Exception) {
                println("Error updating days: ${e.message}")
                _error.value = e.message
            }
        }
    }
    
    /**
     * Update preferred activities
     */
    fun updatePreferredActivities(activities: List<String>) {
        viewModelScope.launch {
            try {
                val currentUser = AppDataManager.currentUser.value ?: return@launch
                
                val current = currentAvailability.value
                val updated = current?.copy(
                    preferredActivities = activities,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                ) ?: UserAvailability(
                    userId = currentUser.id,
                    preferredActivities = activities,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                
                AppDataManager.updateUserAvailability(updated)
                supabaseRepository.updateUserAvailability(updated)
            } catch (e: Exception) {
                println("Error updating activities: ${e.message}")
                _error.value = e.message
            }
        }
    }
    
    /**
     * Update custom status message
     */
    fun updateCustomStatus(status: String?) {
        viewModelScope.launch {
            try {
                val currentUser = AppDataManager.currentUser.value ?: return@launch
                
                val current = currentAvailability.value
                val updated = current?.copy(
                    customStatus = status,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                ) ?: UserAvailability(
                    userId = currentUser.id,
                    customStatus = status,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                
                AppDataManager.updateUserAvailability(updated)
                supabaseRepository.updateUserAvailability(updated)
            } catch (e: Exception) {
                println("Error updating status: ${e.message}")
                _error.value = e.message
            }
        }
    }
    
    /**
     * Load mutual availabilities with user's connections
     */
    fun loadMutualAvailabilities() {
        viewModelScope.launch {
            try {
                val currentUser = AppDataManager.currentUser.value ?: return@launch
                val connections = AppDataManager.connections.value
                val myAvailability = currentAvailability.value
                
                // Get other user IDs
                val otherUserIds = connections.flatMap { it.user_ids }.filter { it != currentUser.id }.distinct()
                
                // Get their availabilities
                val availabilities = supabaseRepository.fetchAvailabilityForUsers(otherUserIds)
                
                // Get user info from AppDataManager
                val usersMap = AppDataManager.connectedUsers.value
                
                // Calculate mutual availabilities
                val mutuals = connections.mapNotNull { connection ->
                    val otherUserId = connection.user_ids.firstOrNull { it != currentUser.id } ?: return@mapNotNull null
                    val otherAvailability = availabilities[otherUserId]
                    val otherUser = usersMap[otherUserId]
                    
                    val mutual = AvailabilityHelper.calculateMutualAvailability(
                        connectionId = connection.id,
                        currentUserAvailability = myAvailability,
                        otherUserAvailability = otherAvailability,
                        otherUserId = otherUserId,
                        otherUserName = otherUser?.name
                    )
                    
                    // Only return if there's mutual availability
                    if (mutual.hasMutualAvailability()) mutual else null
                }
                
                _mutualAvailabilities.value = mutuals
            } catch (e: Exception) {
                println("Error loading mutual availabilities: ${e.message}")
                _error.value = e.message
            }
        }
    }
    
    /**
     * Show the availability settings dialog
     */
    fun showSettings() {
        _showSettingsDialog.value = true
    }
    
    /**
     * Hide the availability settings dialog
     */
    fun hideSettings() {
        _showSettingsDialog.value = false
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }
}
