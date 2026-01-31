package compose.project.click.click.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a user's availability for hanging out
 */
@Serializable
data class UserAvailability(
    val id: String = "",
    @SerialName("user_id")
    val userId: String,
    @SerialName("is_free_this_week")
    val isFreeThisWeek: Boolean = false,
    @SerialName("available_days")
    val availableDays: List<String> = emptyList(), // ["monday", "tuesday", etc.]
    @SerialName("preferred_activities")
    val preferredActivities: List<String> = emptyList(), // ["coffee", "study", "lunch", etc.]
    @SerialName("custom_status")
    val customStatus: String? = null, // e.g., "Free after 3pm most days"
    @SerialName("last_updated")
    val lastUpdated: Long = 0
)

/**
 * Availability status options
 */
enum class AvailabilityStatus {
    FREE_NOW,        // User is free right now
    FREE_THIS_WEEK,  // User is free sometime this week
    BUSY,            // User is not available
    NOT_SET          // User hasn't set availability
}

/**
 * Pre-defined activity suggestions
 */
object ActivitySuggestions {
    val activities = listOf(
        "☕ Grab coffee",
        "📚 Study together",
        "🍕 Get lunch/dinner",
        "🚶 Go for a walk",
        "🎮 Play games",
        "🏃 Work out",
        "🎬 Watch something",
        "💬 Just chat"
    )
    
    val coffeeMessages = listOf(
        "Hey! Want to grab coffee sometime this week?",
        "I'm free this week - coffee? ☕",
        "Let's catch up over coffee!",
        "Free for coffee this week if you are!"
    )
    
    val studyMessages = listOf(
        "Want to study together sometime?",
        "I could use a study buddy this week!",
        "Library session soon?",
        "Let's hit the books together!"
    )
    
    val genericMessages = listOf(
        "Hey, I'm free this week! Want to hang out?",
        "I've got some free time - want to meet up?",
        "Let's hang out soon!",
        "Free this week if you want to do something!"
    )
    
    /**
     * Get a suggested message based on the activity
     */
    fun getSuggestedMessage(activity: String? = null): String {
        return when {
            activity?.contains("coffee", ignoreCase = true) == true -> coffeeMessages.random()
            activity?.contains("study", ignoreCase = true) == true -> studyMessages.random()
            else -> genericMessages.random()
        }
    }
}

/**
 * Days of the week for availability
 */
enum class DayOfWeek(val displayName: String, val shortName: String) {
    MONDAY("Monday", "Mon"),
    TUESDAY("Tuesday", "Tue"),
    WEDNESDAY("Wednesday", "Wed"),
    THURSDAY("Thursday", "Thu"),
    FRIDAY("Friday", "Fri"),
    SATURDAY("Saturday", "Sat"),
    SUNDAY("Sunday", "Sun");
    
    companion object {
        fun fromString(day: String): DayOfWeek? {
            return entries.find { 
                it.name.equals(day, ignoreCase = true) || 
                it.displayName.equals(day, ignoreCase = true) ||
                it.shortName.equals(day, ignoreCase = true)
            }
        }
    }
}

/**
 * Mutual availability between two users
 */
data class MutualAvailability(
    val connectionId: String,
    val otherUserId: String,
    val otherUserName: String?,
    val currentUserAvailability: UserAvailability?,
    val otherUserAvailability: UserAvailability?,
    val mutuallyFreeThisWeek: Boolean = false,
    val commonDays: List<String> = emptyList(),
    val commonActivities: List<String> = emptyList()
) {
    fun hasMutualAvailability(): Boolean {
        return mutuallyFreeThisWeek && (commonDays.isNotEmpty() || currentUserAvailability?.isFreeThisWeek == true)
    }
    
    fun getSuggestedMeetupMessage(): String {
        val activity = commonActivities.firstOrNull()
        val day = commonDays.firstOrNull()
        
        return when {
            activity != null && day != null -> 
                "Both free on $day for ${activity.lowercase()}?"
            activity != null -> 
                "You're both up for ${activity.lowercase()}!"
            day != null -> 
                "Both available on $day!"
            else -> 
                "You're both free this week!"
        }
    }
}

/**
 * Utility object for availability management
 */
object AvailabilityHelper {
    
    /**
     * Calculate mutual availability between two users
     */
    fun calculateMutualAvailability(
        connectionId: String,
        currentUserAvailability: UserAvailability?,
        otherUserAvailability: UserAvailability?,
        otherUserId: String,
        otherUserName: String?
    ): MutualAvailability {
        val bothFree = (currentUserAvailability?.isFreeThisWeek == true) && 
                       (otherUserAvailability?.isFreeThisWeek == true)
        
        val commonDays = if (bothFree) {
            currentUserAvailability?.availableDays?.filter { 
                otherUserAvailability?.availableDays?.contains(it) == true 
            } ?: emptyList()
        } else {
            emptyList()
        }
        
        val commonActivities = if (bothFree) {
            currentUserAvailability?.preferredActivities?.filter { 
                otherUserAvailability?.preferredActivities?.contains(it) == true 
            } ?: emptyList()
        } else {
            emptyList()
        }
        
        return MutualAvailability(
            connectionId = connectionId,
            otherUserId = otherUserId,
            otherUserName = otherUserName,
            currentUserAvailability = currentUserAvailability,
            otherUserAvailability = otherUserAvailability,
            mutuallyFreeThisWeek = bothFree,
            commonDays = commonDays,
            commonActivities = commonActivities
        )
    }
    
    /**
     * Get the availability status for a user
     */
    fun getAvailabilityStatus(availability: UserAvailability?): AvailabilityStatus {
        return when {
            availability == null -> AvailabilityStatus.NOT_SET
            availability.isFreeThisWeek -> AvailabilityStatus.FREE_THIS_WEEK
            else -> AvailabilityStatus.BUSY
        }
    }
}
