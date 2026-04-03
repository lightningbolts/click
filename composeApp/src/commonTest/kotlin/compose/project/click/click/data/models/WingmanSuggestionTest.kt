package compose.project.click.click.data.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WingmanSuggestionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun deserializeSingleSuggestion() {
        val payload = """
            {
                "user_a_id": "aaaa-bbbb-cccc-dddd",
                "user_a_name": "Alice",
                "user_c_id": "eeee-ffff-0000-1111",
                "user_c_name": "Charlie",
                "shared_tags": ["music", "hiking"]
            }
        """.trimIndent()

        val suggestion = json.decodeFromString<WingmanSuggestion>(payload)

        assertEquals("aaaa-bbbb-cccc-dddd", suggestion.userAId)
        assertEquals("Alice", suggestion.userAName)
        assertEquals("eeee-ffff-0000-1111", suggestion.userCId)
        assertEquals("Charlie", suggestion.userCName)
        assertEquals(listOf("music", "hiking"), suggestion.sharedTags)
    }

    @Test
    fun deserializeListOfSuggestions() {
        val payload = """
            [
                {
                    "user_a_id": "a1",
                    "user_a_name": "Alice",
                    "user_c_id": "c1",
                    "user_c_name": "Charlie",
                    "shared_tags": ["music", "cooking", "hiking"]
                },
                {
                    "user_a_id": "a2",
                    "user_a_name": "Bob",
                    "user_c_id": "c2",
                    "user_c_name": "Dana",
                    "shared_tags": ["coding", "gaming"]
                }
            ]
        """.trimIndent()

        val suggestions = json.decodeFromString<List<WingmanSuggestion>>(payload)

        assertEquals(2, suggestions.size)
        assertEquals("Alice", suggestions[0].userAName)
        assertEquals(3, suggestions[0].sharedTags.size)
        assertEquals("Bob", suggestions[1].userAName)
        assertEquals(listOf("coding", "gaming"), suggestions[1].sharedTags)
    }

    @Test
    fun deserializeWithEmptyTags() {
        val payload = """
            {
                "user_a_id": "a1",
                "user_a_name": "Alice",
                "user_c_id": "c1",
                "user_c_name": "Charlie"
            }
        """.trimIndent()

        val suggestion = json.decodeFromString<WingmanSuggestion>(payload)

        assertEquals(emptyList(), suggestion.sharedTags)
    }

    @Test
    fun roundTripSerialization() {
        val original = WingmanSuggestion(
            userAId = "id-a",
            userAName = "Alice",
            userCId = "id-c",
            userCName = "Charlie",
            sharedTags = listOf("music", "hiking")
        )

        val encoded = json.encodeToString(WingmanSuggestion.serializer(), original)
        val decoded = json.decodeFromString<WingmanSuggestion>(encoded)

        assertEquals(original, decoded)
    }
}
