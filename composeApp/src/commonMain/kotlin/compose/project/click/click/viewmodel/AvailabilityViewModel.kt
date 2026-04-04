package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.AvailabilityHelper
import compose.project.click.click.data.models.AvailabilityStatus
import compose.project.click.click.data.models.DayOfWeek
import compose.project.click.click.data.models.AvailabilityIntentInsert // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.MutualAvailability
import compose.project.click.click.data.models.UserAvailability
import compose.project.click.click.data.repository.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Preset duration for [public.availability_intents] windows (from now). */
enum class AvailabilityIntentDuration(val durationMs: Long, val label: String) {
    FIFTEEN_MIN(15L * 60_000L, "15 min"),
    THIRTY_MIN(30L * 60_000L, "30 min"),
    FORTY_FIVE_MIN(45L * 60_000L, "45 min"),
    ONE_HOUR(60L * 60_000L, "1 hour"),
    NINETY_MIN(90L * 60_000L, "90 min"),
    TWO_HOURS(2L * 60L * 60_000L, "2 hours"),
    THREE_HOURS(3L * 60L * 60_000L, "3 hours"),
    SIX_HOURS(6L * 60L * 60_000L, "6 hours"),
    TWENTY_FOUR_HOURS(24L * 60L * 60_000L, "24 hours"),
}

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

    // Intent-based availability sheet (availability_intents)
    private val _intentTagInput = MutableStateFlow("")
    val intentTagInput: StateFlow<String> = _intentTagInput.asStateFlow()

    private val _intentDuration = MutableStateFlow(AvailabilityIntentDuration.THREE_HOURS)
    val intentDuration: StateFlow<AvailabilityIntentDuration> = _intentDuration.asStateFlow()

    private val _intentSubmitting = MutableStateFlow(false)
    val intentSubmitting: StateFlow<Boolean> = _intentSubmitting.asStateFlow()

    private val _intentSubmitError = MutableStateFlow<String?>(null)
    val intentSubmitError: StateFlow<String?> = _intentSubmitError.asStateFlow()

    /** Non-expired rows from [public.availability_intents] for the signed-in user. */
    private val _activeAvailabilityIntents = MutableStateFlow<List<AvailabilityIntentRow>>(emptyList())
    val activeAvailabilityIntents: StateFlow<List<AvailabilityIntentRow>> =
        _activeAvailabilityIntents.asStateFlow()

    private val _loadingActiveAvailabilityIntents = MutableStateFlow(false)
    val loadingActiveAvailabilityIntents: StateFlow<Boolean> =
        _loadingActiveAvailabilityIntents.asStateFlow()

    /** Non-null while the sheet is editing an existing row (insert when null). */
    private val _editingAvailabilityIntentId = MutableStateFlow<String?>(null)
    val editingAvailabilityIntentId: StateFlow<String?> = _editingAvailabilityIntentId.asStateFlow()

    /** Inline message under the active-intents list (e.g. delete/update failure). */
    private val _intentListFeedback = MutableStateFlow<String?>(null)
    val intentListFeedback: StateFlow<String?> = _intentListFeedback.asStateFlow()
    
    init {
        // Observe availability changes to update status
        viewModelScope.launch {
            currentAvailability.collectLatest { availability ->
                _availabilityStatus.value = AvailabilityHelper.getAvailabilityStatus(availability)
            }
        }
        viewModelScope.launch {
            refreshActiveAvailabilityIntentsInternal()
        }
    }

    private suspend fun refreshActiveAvailabilityIntentsInternal() {
        val uid = SupabaseConfig.client.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
        if (uid == null) {
            _activeAvailabilityIntents.value = emptyList()
            return
        }
        _intentListFeedback.value = null
        _loadingActiveAvailabilityIntents.value = true
        try {
            _activeAvailabilityIntents.value = supabaseRepository.fetchActiveAvailabilityIntentsForUser(uid)
        } finally {
            _loadingActiveAvailabilityIntents.value = false
        }
    }

    /**
     * Reload active intent posts from Supabase (call when opening Settings or after a successful save).
     */
    fun refreshActiveAvailabilityIntents() {
        viewModelScope.launch {
            refreshActiveAvailabilityIntentsInternal()
        }
    }

    fun beginEditAvailabilityIntent(row: AvailabilityIntentRow) {
        val id = row.id?.takeIf { it.isNotBlank() } ?: return
        _editingAvailabilityIntentId.value = id
        _intentTagInput.value = row.intentTag.orEmpty()
        val tf = row.timeframe?.trim().orEmpty()
        _intentDuration.value = AvailabilityIntentDuration.entries.find { it.label == tf }
            ?: AvailabilityIntentDuration.entries.find { entry -> tf.contains(entry.label, ignoreCase = true) }
            ?: AvailabilityIntentDuration.THREE_HOURS
        _intentSubmitError.value = null
        _intentSubmitting.value = false
    }

    fun deleteAvailabilityIntent(intentId: String) {
        viewModelScope.launch {
            val result = supabaseRepository.deleteAvailabilityIntent(intentId)
            refreshActiveAvailabilityIntentsInternal()
            if (!result.success) {
                _intentListFeedback.value = formatAvailabilityIntentSaveError(result.errorMessage)
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

    fun resetAvailabilityIntentSheet() {
        _editingAvailabilityIntentId.value = null
        _intentTagInput.value = ""
        _intentDuration.value = AvailabilityIntentDuration.THREE_HOURS
        _intentSubmitError.value = null
        _intentSubmitting.value = false
    }

    fun updateIntentTagInput(raw: String) {
        _intentTagInput.value = raw.replace(WHITESPACE_REGEX, " ").take(AVAILABILITY_INTENT_TAG_MAX_LENGTH)
    }

    fun setIntentDuration(duration: AvailabilityIntentDuration) {
        _intentDuration.value = duration
    }

    fun clearIntentSubmitError() {
        _intentSubmitError.value = null
    }

    /**
     * Inserts a row into [public.availability_intents] for the signed-in user.
     */
    fun submitAvailabilityIntent(onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (_intentSubmitting.value) return@launch
            // RLS uses auth.uid(); must match JWT subject (not only cached profile id).
            val userId = SupabaseConfig.client.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
            if (userId == null) {
                _intentSubmitError.value = "Sign in to share availability."
                return@launch
            }
            val tag = _intentTagInput.value.trim()
            if (tag.isEmpty()) {
                _intentSubmitError.value = "Add a short intent tag."
                return@launch
            }
            val startMs = Clock.System.now().toEpochMilliseconds()
            val endMs = startMs + _intentDuration.value.durationMs
            val startsIso = Instant.fromEpochMilliseconds(startMs).toString()
            val endsIso = Instant.fromEpochMilliseconds(endMs).toString()
            val editingId = _editingAvailabilityIntentId.value?.takeIf { it.isNotBlank() }
            _intentSubmitting.value = true
            _intentSubmitError.value = null
            val result = if (editingId != null) {
                supabaseRepository.updateAvailabilityIntent(
                    id = editingId,
                    userId = userId,
                    intentTag = tag,
                    timeframe = _intentDuration.value.label,
                    startsAt = startsIso,
                    endsAt = endsIso,
                    expiresAt = endsIso,
                )
            } else {
                supabaseRepository.insertAvailabilityIntent(
                    AvailabilityIntentInsert(
                        userId = userId,
                        intentTag = tag,
                        timeframe = _intentDuration.value.label,
                        startsAt = startsIso,
                        endsAt = endsIso,
                        expiresAt = endsIso,
                    ),
                )
            }
            _intentSubmitting.value = false
            if (result.success) {
                resetAvailabilityIntentSheet()
                refreshActiveAvailabilityIntentsInternal()
                onSuccess()
            } else {
                _intentSubmitError.value = formatAvailabilityIntentSaveError(result.errorMessage)
            }
        }
    }

    companion object {
        const val AVAILABILITY_INTENT_TAG_MAX_LENGTH = 25
        private val WHITESPACE_REGEX = Regex("\\s+")

        private fun formatAvailabilityIntentSaveError(raw: String?): String {
            val detail = raw?.trim().orEmpty()
            return when {
                detail.contains("availability_intents", ignoreCase = true) &&
                    (detail.contains("does not exist", ignoreCase = true) ||
                        detail.contains("schema cache", ignoreCase = true)) ->
                    "Availability isn’t set up on the server yet. Ask your admin to run the database migration."
                detail.contains("row-level security", ignoreCase = true) ||
                    detail.contains("violates row-level security", ignoreCase = true) ||
                    detail.contains("42501", ignoreCase = true) ->
                    "Couldn’t save (permissions). Sign out, sign in again, then retry."
                detail.contains("JWT", ignoreCase = true) ||
                    detail.contains("not authenticated", ignoreCase = true) ||
                    detail.contains("401", ignoreCase = true) ->
                    "Session issue. Sign in again, then retry."
                detail.isNotEmpty() -> detail.take(240)
                else -> "Could not save. Try again."
            }
        }
    }
}
