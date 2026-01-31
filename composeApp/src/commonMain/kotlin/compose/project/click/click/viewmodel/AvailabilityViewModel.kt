package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.AvailabilityHelper
import compose.project.click.click.data.models.AvailabilityStatus
import compose.project.click.click.data.models.DayOfWeek
import compose.project.click.click.data.models.MutualAvailability
import compose.project.click.click.data.models.UserAvailability
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class AvailabilityViewModel(
    private val supabaseRepository: SupabaseRepository = SupabaseRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    
    // Current user's availability
    private val _currentAvailability = MutableStateFlow<UserAvailability?>(null)
    val currentAvailability: StateFlow<UserAvailability?> = _currentAvailability.asStateFlow()
    
    // Availability status
    private val _availabilityStatus = MutableStateFlow(AvailabilityStatus.NOT_SET)
    val availabilityStatus: StateFlow<AvailabilityStatus> = _availabilityStatus.asStateFlow()
    
    // Mutual availabilities with connections
    private val _mutualAvailabilities = MutableStateFlow<List<MutualAvailability>>(emptyList())
    val mutualAvailabilities: StateFlow<List<MutualAvailability>> = _mutualAvailabilities.asStateFlow()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Show availability settings dialog
    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()
    
    init {
        loadCurrentUserAvailability()
    }
    
    /**
     * Load the current user's availability settings
     */
    fun loadCurrentUserAvailability() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    val availability = supabaseRepository.fetchUserAvailability(currentUser.id)
                    _currentAvailability.value = availability
                    _availabilityStatus.value = AvailabilityHelper.getAvailabilityStatus(availability)
                }
            } catch (e: Exception) {
                println("Error loading availability: ${e.message}")
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Toggle the "I'm free this week" status
     */
    fun toggleFreeThisWeek() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser() ?: return@launch
                val currentStatus = _currentAvailability.value?.isFreeThisWeek ?: false
                val newStatus = !currentStatus
                
                // Update local state optimistically
                val updatedAvailability = _currentAvailability.value?.copy(
                    isFreeThisWeek = newStatus,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                ) ?: UserAvailability(
                    userId = currentUser.id,
                    isFreeThisWeek = newStatus,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                _currentAvailability.value = updatedAvailability
                _availabilityStatus.value = AvailabilityHelper.getAvailabilityStatus(updatedAvailability)
                
                // Persist to database in background
                val success = supabaseRepository.setFreeThisWeek(currentUser.id, newStatus)
                if (!success) {
                    // Revert on failure
                    _currentAvailability.value = updatedAvailability.copy(isFreeThisWeek = currentStatus)
                    _availabilityStatus.value = AvailabilityHelper.getAvailabilityStatus(
                        _currentAvailability.value
                    )
                    _error.value = "Failed to update availability"
                }
            } catch (e: Exception) {
                println("Error toggling availability: ${e.message}")
                _error.value = e.message
            }
        }
    }
    
    /**
     * Update available days
     */
    fun updateAvailableDays(days: List<DayOfWeek>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = authRepository.getCurrentUser() ?: return@launch
                val dayNames = days.map { it.name.lowercase() }
                
                val currentAvailability = _currentAvailability.value
                val updated = currentAvailability?.copy(
                    availableDays = dayNames,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                ) ?: UserAvailability(
                    userId = currentUser.id,
                    availableDays = dayNames,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                
                val success = supabaseRepository.updateUserAvailability(updated)
                if (success) {
                    loadCurrentUserAvailability()
                }
            } catch (e: Exception) {
                println("Error updating days: ${e.message}")
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update preferred activities
     */
    fun updatePreferredActivities(activities: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = authRepository.getCurrentUser() ?: return@launch
                
                val currentAvailability = _currentAvailability.value
                val updated = currentAvailability?.copy(
                    preferredActivities = activities,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                ) ?: UserAvailability(
                    userId = currentUser.id,
                    preferredActivities = activities,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                
                val success = supabaseRepository.updateUserAvailability(updated)
                if (success) {
                    loadCurrentUserAvailability()
                }
            } catch (e: Exception) {
                println("Error updating activities: ${e.message}")
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update custom status message
     */
    fun updateCustomStatus(status: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = authRepository.getCurrentUser() ?: return@launch
                
                val currentAvailability = _currentAvailability.value
                val updated = currentAvailability?.copy(
                    customStatus = status,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                ) ?: UserAvailability(
                    userId = currentUser.id,
                    customStatus = status,
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
                
                val success = supabaseRepository.updateUserAvailability(updated)
                if (success) {
                    loadCurrentUserAvailability()
                }
            } catch (e: Exception) {
                println("Error updating status: ${e.message}")
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load mutual availabilities with user's connections
     */
    fun loadMutualAvailabilities() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = authRepository.getCurrentUser() ?: return@launch
                
                // Get current user's availability
                val myAvailability = supabaseRepository.fetchUserAvailability(currentUser.id)
                
                // Get user's connections
                val connections = supabaseRepository.fetchUserConnections(currentUser.id)
                
                // Get other user IDs
                val otherUserIds = connections.flatMap { it.user_ids }.filter { it != currentUser.id }.distinct()
                
                // Get their availabilities
                val availabilities = supabaseRepository.fetchAvailabilityForUsers(otherUserIds)
                
                // Get user info
                val users = supabaseRepository.fetchUsersByIds(otherUserIds)
                val usersMap = users.associateBy { it.id }
                
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
            } finally {
                _isLoading.value = false
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
