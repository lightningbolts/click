package compose.project.click.click.crypto

import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals

@OptIn(ExperimentalEncodingApi::class)
class E2eeV2GoldenVectorTest {
    @Test
    fun sharedWebVectorDecryptsOnMobile() {
        val metadata =
            MessageCryptoV2.MessageMetadata(
                chatId = "11111111-1111-4111-8111-111111111111",
                epoch = 7,
                senderDeviceId = "sender-device-01",
                clientMessageId = "22222222-2222-4222-8222-222222222222",
            )
        val wireJson =
            """
            {"v":2,"type":"message","chatId":"11111111-1111-4111-8111-111111111111","epoch":7,"senderDeviceId":"sender-device-01","cryptoVersion":2,"clientMessageId":"22222222-2222-4222-8222-222222222222","nonce":"AAECAwQFBgcICQoL","ciphertext":"IG26f6CL4m3oIuPkw/7QgpGg+yKGSVbcsDe3tBQ="}
            """.trimIndent()
        val key = Base64.decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
        val wire = MessageCryptoV2.PREFIX + Base64.encode(wireJson.encodeToByteArray())

        assertEquals("golden vector", MessageCryptoV2.decryptMessage(metadata, key, wire))
    }
}
