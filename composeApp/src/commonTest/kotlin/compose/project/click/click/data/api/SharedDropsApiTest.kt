package compose.project.click.click.data.api

import compose.project.click.click.collaboration.SharedDropAudience
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wire shapes against click-web `lib/api/schemas/drops.ts` and `app/api/drops/develop/route.ts`. */
@OptIn(ExperimentalEncodingApi::class)
class SharedDropsApiTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun createBodyCarriesBothImagesAsBase64AndTheAudienceWireValue() {
        val body =
            sharedDropCreateBody(
                original = byteArrayOf(1, 2, 3),
                preview = byteArrayOf(9),
                width = 1200,
                height = 1600,
                audience = SharedDropAudience.CORE,
                caption = "  sunset  ",
                clientDropId = "11111111-1111-4111-8111-111111111111",
            )
        val wire = json.parseToJsonElement(json.encodeToString(body)).jsonObject
        assertEquals("11111111-1111-4111-8111-111111111111", wire["client_drop_id"]?.jsonPrimitive?.content)
        assertEquals("core", wire["audience"]?.jsonPrimitive?.content)
        assertEquals("image/jpeg", wire["mime_type"]?.jsonPrimitive?.content)
        assertEquals(Base64.encode(byteArrayOf(1, 2, 3)), wire["original_b64"]?.jsonPrimitive?.content)
        assertEquals(Base64.encode(byteArrayOf(9)), wire["preview_b64"]?.jsonPrimitive?.content)
        assertEquals("1200", wire["width"]?.jsonPrimitive?.content)
        assertEquals("sunset", wire["caption"]?.jsonPrimitive?.content)
    }

    @Test
    fun blankCaptionsAndUnknownSizesAreLeftOut() {
        val body =
            sharedDropCreateBody(
                original = byteArrayOf(1),
                preview = byteArrayOf(2),
                width = 0,
                height = null,
                audience = SharedDropAudience.ALL,
                caption = "   ",
                clientDropId = "id",
            )
        val wire = json.parseToJsonElement(json.encodeToString(body)) as JsonObject
        assertEquals("all", wire["audience"]?.jsonPrimitive?.content)
        assertFalse("caption" in wire)
        assertFalse("width" in wire)
        assertFalse("height" in wire)
    }

    @Test
    fun aSharedDevelopAnswerDecodesWithItsSignedOriginal() {
        val envelope =
            Json { ignoreUnknownKeys = true }.decodeFromString<DropDevelopEnvelope>(
                """{"drops":[{"kind":"shared","id":"d1","status":"developed","developed_at":"2026-10-01T10:05:00Z","url":"https://signed/o"},
                {"kind":"shared","id":"d2","status":"pending","reveal_at":"2026-10-01T11:00:00Z"}]}""",
            )
        assertEquals("developed", envelope.drops[0].status)
        assertEquals("https://signed/o", envelope.drops[0].url)
        assertEquals("pending", envelope.drops[1].status)
        assertNull(envelope.drops[1].url)
        val request = json.encodeToString(DropDevelopBody(listOf(DropDevelopRef(SharedDropsApi.SHARED_DROP_KIND, "d1"))))
        assertEquals("""{"drops":[{"kind":"shared","id":"d1"}]}""", request)
    }

    @Test
    fun reportsNameTheSharedKind() {
        assertEquals(
            """{"kind":"shared","id":"d1","reason":"spam"}""",
            json.encodeToString(DropReportBody(SharedDropsApi.SHARED_DROP_KIND, "d1", "spam")),
        )
    }

    @Test
    fun capRefusalsUseTheServerCopyWhenThereIsSome() {
        assertEquals(
            "You can share 3 drops a day. Try again tomorrow.",
            SharedDropPostException.CapReached("You can share 3 drops a day. Try again tomorrow.").message,
        )
        assertTrue(SharedDropPostException.CapReached().message!!.contains("Try again tomorrow"))
    }
}
