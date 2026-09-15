package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ClickConversationListRowContractTest {
    @Test
    fun conversationRows_keepCommercialInboxDensity() {
        assertEquals(72.dp, ClickConversationListRowMinHeight)
        assertEquals(48.dp, ClickConversationAvatarSize)
    }
}
