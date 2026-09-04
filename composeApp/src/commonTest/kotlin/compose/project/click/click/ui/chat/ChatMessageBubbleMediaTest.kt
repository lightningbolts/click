package compose.project.click.click.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMessageBubbleMediaTest {
    @Test
    fun encryptedHubAudio_usesDecryptedLocalPathAndNeverUsesStoragePathAsUrl() {
        val source =
            chatAudioPlaybackSource(
                mediaUrl = null,
                hubMediaPath = "user-1/hub-1/audio.bin",
                decryptedLocalPath = "/tmp/click/audio.m4a",
            )

        assertEquals("", source?.mediaUrl)
        assertEquals("/tmp/click/audio.m4a", source?.localFilePath)
    }

    @Test
    fun audioWithoutMediaReference_doesNotSelectAudioBranch() {
        assertNull(chatAudioPlaybackSource(null, null, "/tmp/click/audio.m4a"))
    }
}
