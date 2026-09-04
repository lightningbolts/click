package compose.project.click.click.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatApiClientHubTest {
    @Test
    fun urlOnlyMediaResponse_decodesWithoutPath() {
        val response =
            Json.decodeFromString<ChatMediaUploadUrlResponse>(
                """{"url":"https://example.test/signed-audio"}""",
            )

        assertEquals("https://example.test/signed-audio", response.trimmedUrlOrNull())
    }
}
