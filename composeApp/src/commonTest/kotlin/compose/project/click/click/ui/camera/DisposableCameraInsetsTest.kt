package compose.project.click.click.ui.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp

class DisposableCameraInsetsTest {

    @Test
    fun extraPaddingWhenTabBarMayOverlap() {
        assertEquals(
            83.dp,
            disposableRollExtraBottomPadding(isOpenedFromChat = false, tabBarOverlayHeight = 83.dp),
        )
    }

    @Test
    fun noExtraPaddingWhenOpenedFromChat() {
        assertEquals(
            0.dp,
            disposableRollExtraBottomPadding(isOpenedFromChat = true, tabBarOverlayHeight = 83.dp),
        )
    }
}
