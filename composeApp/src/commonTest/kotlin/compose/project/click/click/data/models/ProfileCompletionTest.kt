package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileCompletionTest {
    @Test
    fun incomplete_whenBirthdayMissing() {
        val u = User(
            id = "a",
            name = "Pat",
            firstName = "Pat",
            birthday = null,
        )
        assertTrue(isPublicUserProfileIncomplete(u))
    }

    @Test
    fun incomplete_whenFirstNameMissing() {
        val u = User(
            id = "a",
            name = "Pat",
            firstName = null,
            birthday = "2000-01-15",
        )
        assertTrue(isPublicUserProfileIncomplete(u))
    }

    @Test
    fun complete_whenBasicsPresent() {
        val u = User(
            id = "a",
            name = "Pat Example",
            firstName = "Pat",
            birthday = "2000-01-15",
        )
        assertFalse(isPublicUserProfileIncomplete(u))
    }
}
