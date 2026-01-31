package compose.project.click.click.data.models

import kotlinx.serialization.Serializable

/**
 * Category of icebreaker prompts
 */
enum class IcebreakerCategory {
    CONTEXT_BASED,      // Based on where/when they met
    FUN_QUESTIONS,      // General fun questions
    DEEP_QUESTIONS,     // More meaningful questions
    ACTIVITY_BASED,     // Suggestions for activities
    GETTING_TO_KNOW     // Basic getting to know you questions
}

/**
 * An icebreaker prompt to help start conversations
 */
@Serializable
data class IcebreakerPrompt(
    val id: String,
    val text: String,
    val category: IcebreakerCategory,
    val contextKeywords: List<String> = emptyList() // Keywords that make this prompt relevant
)

/**
 * Repository of icebreaker prompts organized by category
 */
object IcebreakerRepository {
    
    // Context-based prompts (will match against connection context_tag)
    private val contextPrompts = listOf(
        // Academic/Class contexts
        IcebreakerPrompt(
            id = "ctx_class_1",
            text = "What's your major? How are you liking the class so far?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("cse", "class", "lecture", "course", "101", "142", "143", "341", "351")
        ),
        IcebreakerPrompt(
            id = "ctx_class_2",
            text = "Are you finding the assignments challenging? Want to study together sometime?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("cse", "class", "math", "physics", "chemistry", "bio")
        ),
        IcebreakerPrompt(
            id = "ctx_study_1",
            text = "What's your go-to study spot on campus?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("library", "study", "odegaard", "suzzallo", "allen")
        ),
        
        // Event contexts
        IcebreakerPrompt(
            id = "ctx_event_1",
            text = "What was the best thing you saw/did at the event?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("dawg daze", "festival", "fair", "event", "concert", "show")
        ),
        IcebreakerPrompt(
            id = "ctx_event_2",
            text = "Are you going to any other campus events this quarter?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("dawg daze", "event", "festival")
        ),
        
        // Location contexts
        IcebreakerPrompt(
            id = "ctx_hub_1",
            text = "The HUB food is decent - what's your favorite spot to grab lunch on campus?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("hub", "food", "dining", "lunch", "coffee")
        ),
        IcebreakerPrompt(
            id = "ctx_quad_1",
            text = "The Quad is beautiful! Do you come here often to hang out?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("quad", "cherry", "blossom", "drumheller")
        ),
        IcebreakerPrompt(
            id = "ctx_gym_1",
            text = "Do you work out regularly? What's your gym routine like?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("ima", "gym", "fitness", "workout", "rec")
        ),
        
        // Club/Organization contexts
        IcebreakerPrompt(
            id = "ctx_club_1",
            text = "How long have you been involved with this club/organization?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("club", "meeting", "organization", "asuw", "rso")
        ),
        
        // Party/Social contexts
        IcebreakerPrompt(
            id = "ctx_party_1",
            text = "How do you know the host? Are you having a good time?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("party", "kickback", "hangout")
        ),
        
        // Sports contexts
        IcebreakerPrompt(
            id = "ctx_sports_1",
            text = "Did you see that play?! Are you a big Huskies fan?",
            category = IcebreakerCategory.CONTEXT_BASED,
            contextKeywords = listOf("game", "husky", "football", "basketball", "stadium")
        )
    )
    
    // Fun, lighthearted questions
    private val funPrompts = listOf(
        IcebreakerPrompt(
            id = "fun_1",
            text = "If you could have dinner with anyone, living or dead, who would it be?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_2",
            text = "What's your most unpopular opinion?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_3",
            text = "If you won the lottery tomorrow, what's the first thing you'd do?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_4",
            text = "What's the last show you binge-watched?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_5",
            text = "Do you have any hidden talents?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_6",
            text = "What's your go-to karaoke song?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_7",
            text = "What's the best trip you've ever taken?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_8",
            text = "Are you a morning person or a night owl?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_9",
            text = "What's your comfort food when you're stressed?",
            category = IcebreakerCategory.FUN_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "fun_10",
            text = "If you could instantly be an expert at something, what would it be?",
            category = IcebreakerCategory.FUN_QUESTIONS
        )
    )
    
    // Deeper, more meaningful questions
    private val deepPrompts = listOf(
        IcebreakerPrompt(
            id = "deep_1",
            text = "What's something you've been really proud of lately?",
            category = IcebreakerCategory.DEEP_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "deep_2",
            text = "What's a goal you're currently working towards?",
            category = IcebreakerCategory.DEEP_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "deep_3",
            text = "What's something new you've learned recently?",
            category = IcebreakerCategory.DEEP_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "deep_4",
            text = "What's a book, podcast, or show that changed your perspective on something?",
            category = IcebreakerCategory.DEEP_QUESTIONS
        ),
        IcebreakerPrompt(
            id = "deep_5",
            text = "If you could give advice to your younger self, what would it be?",
            category = IcebreakerCategory.DEEP_QUESTIONS
        )
    )
    
    // Activity-based suggestions
    private val activityPrompts = listOf(
        IcebreakerPrompt(
            id = "activity_1",
            text = "Have you tried any good restaurants around campus lately?",
            category = IcebreakerCategory.ACTIVITY_BASED
        ),
        IcebreakerPrompt(
            id = "activity_2",
            text = "Want to grab coffee sometime and chat more?",
            category = IcebreakerCategory.ACTIVITY_BASED
        ),
        IcebreakerPrompt(
            id = "activity_3",
            text = "Are you into any sports or fitness activities?",
            category = IcebreakerCategory.ACTIVITY_BASED
        ),
        IcebreakerPrompt(
            id = "activity_4",
            text = "Do you play any games? Board games, video games, sports?",
            category = IcebreakerCategory.ACTIVITY_BASED
        ),
        IcebreakerPrompt(
            id = "activity_5",
            text = "What do you usually do on the weekends?",
            category = IcebreakerCategory.ACTIVITY_BASED
        )
    )
    
    // Getting to know you basics
    private val gettingToKnowPrompts = listOf(
        IcebreakerPrompt(
            id = "gtky_1",
            text = "What year are you? How are you liking UW so far?",
            category = IcebreakerCategory.GETTING_TO_KNOW
        ),
        IcebreakerPrompt(
            id = "gtky_2",
            text = "Where are you originally from?",
            category = IcebreakerCategory.GETTING_TO_KNOW
        ),
        IcebreakerPrompt(
            id = "gtky_3",
            text = "What made you choose your major?",
            category = IcebreakerCategory.GETTING_TO_KNOW
        ),
        IcebreakerPrompt(
            id = "gtky_4",
            text = "Are you living on campus or off campus?",
            category = IcebreakerCategory.GETTING_TO_KNOW
        ),
        IcebreakerPrompt(
            id = "gtky_5",
            text = "What's your favorite thing about being here so far?",
            category = IcebreakerCategory.GETTING_TO_KNOW
        )
    )
    
    /**
     * Get icebreaker prompts based on connection context.
     * Returns contextual prompts if a matching context is found, otherwise returns general prompts.
     * 
     * @param contextTag The context_tag from the connection (e.g., "Met at Dawg Daze")
     * @param count Number of prompts to return
     * @return List of relevant icebreaker prompts
     */
    fun getPromptsForContext(contextTag: String?, count: Int = 3): List<IcebreakerPrompt> {
        val result = mutableListOf<IcebreakerPrompt>()
        
        // Try to find context-based prompts that match the tag
        if (!contextTag.isNullOrBlank()) {
            val lowercaseTag = contextTag.lowercase()
            val matchingPrompts = contextPrompts.filter { prompt ->
                prompt.contextKeywords.any { keyword -> lowercaseTag.contains(keyword) }
            }
            result.addAll(matchingPrompts.shuffled().take(count))
        }
        
        // Fill remaining slots with random prompts from other categories
        val remaining = count - result.size
        if (remaining > 0) {
            val otherPrompts = (funPrompts + activityPrompts + gettingToKnowPrompts)
                .shuffled()
                .take(remaining)
            result.addAll(otherPrompts)
        }
        
        return result.shuffled()
    }
    
    /**
     * Get prompts by category
     */
    fun getPromptsByCategory(category: IcebreakerCategory, count: Int = 3): List<IcebreakerPrompt> {
        val prompts = when (category) {
            IcebreakerCategory.CONTEXT_BASED -> contextPrompts
            IcebreakerCategory.FUN_QUESTIONS -> funPrompts
            IcebreakerCategory.DEEP_QUESTIONS -> deepPrompts
            IcebreakerCategory.ACTIVITY_BASED -> activityPrompts
            IcebreakerCategory.GETTING_TO_KNOW -> gettingToKnowPrompts
        }
        return prompts.shuffled().take(count)
    }
    
    /**
     * Get a random prompt from any category
     */
    fun getRandomPrompt(): IcebreakerPrompt {
        val allPrompts = contextPrompts + funPrompts + deepPrompts + activityPrompts + gettingToKnowPrompts
        return allPrompts.random()
    }
    
    /**
     * Get all prompts (for displaying full list to user)
     */
    fun getAllPrompts(): List<IcebreakerPrompt> {
        return contextPrompts + funPrompts + deepPrompts + activityPrompts + gettingToKnowPrompts
    }
}
