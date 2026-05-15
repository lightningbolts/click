package compose.project.click.click.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HubChatSettingsMenuTest {

    @Test
    fun hubSettingsMenu_hidesEditAndDeleteForNonCreator() {
        val items = visibleHubSettingsMenuItems(
            currentUserId = "participant",
            creatorId = "creator",
        )

        assertEquals(listOf(HubSettingsMenuItem.Leave), items)
        assertFalse(HubSettingsMenuItem.Edit in items)
        assertFalse(HubSettingsMenuItem.Delete in items)
    }
}
