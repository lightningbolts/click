package compose.project.click.click

import compose.project.click.click.data.api.KlipyClient
import compose.project.click.click.data.models.ChatGif
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.gifOrNull
import compose.project.click.click.data.models.previewLabel
import compose.project.click.click.ui.chat.GifPickerState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors click-ios `Tests/ClickTests/ChatGifTests.swift` and click-web `__tests__/lib/chat/gif.test.ts`. */
class ChatGifTest {
    private val url = "https://static.klipy.com/ii/abc/def.webp"

    private fun meta(vararg pairs: Pair<String, Any>): JsonObject =
        buildJsonObject {
            put(
                "gif",
                buildJsonObject {
                    pairs.forEach { (k, v) ->
                        when (v) {
                            is Int -> put(k, v)
                            is Double -> put(k, v)
                            else -> put(k, v.toString())
                        }
                    }
                },
            )
        }

    private fun message(
        content: String,
        metadata: JsonObject?,
        type: String = "text",
    ) = Message(id = "m", user_id = "u", content = content, timeCreated = 0L, messageType = type, metadata = metadata)

    @Test
    fun klipyHostsOnly() {
        assertTrue(ChatGif.isKlipyMediaUrl(url))
        assertTrue(ChatGif.isKlipyMediaUrl("https://static2.klipy.com/x.gif"))
        assertTrue(ChatGif.isKlipyMediaUrl("  https://STATIC.klipy.com/x.gif \n"))
        assertFalse(ChatGif.isKlipyMediaUrl("http://static.klipy.com/x.gif"))
        assertFalse(ChatGif.isKlipyMediaUrl("https://evil.com/static.klipy.com/x.gif"))
        assertFalse(ChatGif.isKlipyMediaUrl("https://static.klipy.com.evil.com/x.gif"))
        assertFalse(ChatGif.isKlipyMediaUrl("https://static.klipy.com@evil.com/x.gif"))
        assertFalse(ChatGif.isKlipyMediaUrl("https://api.klipy.com/x.gif"))
        assertFalse(ChatGif.isKlipyMediaUrl("$url and text"))
        assertFalse(ChatGif.isKlipyMediaUrl(""))
        assertFalse(ChatGif.isKlipyMediaUrl(null))
    }

    @Test
    fun sendPayloadMatchesIos() {
        val gif = ChatGif(url, 320, 180)
        assertEquals(url, gif.content)
        val md = gif.messageMetadata()
        assertEquals(setOf("gif"), md.keys)
        val wire = md["gif"]!!.jsonObject
        assertEquals("klipy", wire["provider"]!!.jsonPrimitive.content)
        assertEquals("320", wire["width"]!!.jsonPrimitive.content)
        assertEquals("180", wire["height"]!!.jsonPrimitive.content)
        assertFalse("url" in wire)
        val reply = gif.messageMetadata(replyToId = "r1")
        assertEquals("r1", reply["reply_to_id"]!!.jsonPrimitive.content)
        assertEquals(setOf("gif"), gif.messageMetadata(replyToId = " ").keys)
    }

    @Test
    fun parseRoundTripsAndRejects() {
        val gif = ChatGif(url, 320, 180)
        assertEquals(gif, message(gif.content, gif.messageMetadata("r")).gifOrNull())
        // Wire numbers may arrive as strings or doubles.
        assertEquals(ChatGif(url, 300, 200), message(url, meta("provider" to "klipy", "width" to "300", "height" to 199.6)).gifOrNull())
        // Needs the marker, the provider, positive sizes, a text type and a KLIPY body.
        assertNull(message(url, null).gifOrNull())
        assertNull(message(url, meta("provider" to "tenor", "width" to 1, "height" to 1)).gifOrNull())
        assertNull(message(url, meta("provider" to "klipy", "width" to 0, "height" to 1)).gifOrNull())
        assertNull(message(url, meta("provider" to "klipy", "width" to 2)).gifOrNull())
        assertNull(message(url, gif.messageMetadata(), type = "image").gifOrNull())
        assertNull(message("https://evil.com/x.gif", gif.messageMetadata()).gifOrNull())
        assertEquals(url, message("  $url\n", gif.messageMetadata()).gifOrNull()?.url)
    }

    @Test
    fun previewLabel() {
        assertEquals("GIF", message(url, null).previewLabel())
        assertEquals("GIF", ChatGif.previewLabel(url))
        assertNull(ChatGif.previewLabel("hello"))
        assertEquals("hello", message("hello", null).previewLabel())
    }

    @Test
    fun bubbleSizeFitsLikeIos() {
        assertEquals(240f to 135f, ChatGif(url, 480, 270).bubbleSizeDp())
        assertEquals(240f to 320f, ChatGif(url, 300, 400).bubbleSizeDp())
        // Very wide / tall keep a usable minimum side.
        assertEquals(240f to 90f, ChatGif(url, 1000, 10).bubbleSizeDp())
        assertEquals(120f to 320f, ChatGif(url, 10, 1000).bubbleSizeDp())
    }

    @Test
    fun klipyUrlBuilder() {
        val trending = KlipyClient.gifsUrl("KEY", "  ", 1, "cust", "US")
        assertEquals(
            "https://api.klipy.com/api/v1/KEY/gifs/trending?page=1&per_page=24&customer_id=cust" +
                "&content_filter=medium&format_filter=gif%2Cwebp&locale=us",
            trending,
        )
        val search = KlipyClient.gifsUrl("KEY", " happy cat ", 3, "cust", null)
        assertTrue(search.startsWith("https://api.klipy.com/api/v1/KEY/gifs/search?page=3&"))
        assertTrue(search.endsWith("&q=happy%20cat"))
        assertFalse("locale" in KlipyClient.gifsUrl("KEY", "", 1, "c", "419"))
        assertEquals("""{"customer_id":"c","q":"hi"}""", KlipyClient.shareBody("c", " hi ").toString())
    }

    @Test
    fun customerIdIsStableAndOpaque() {
        val id = KlipyClient.customerId("user-1")
        assertEquals(32, id.length)
        assertTrue(id.all { it in "0123456789abcdef" })
        assertEquals(id, KlipyClient.customerId("user-1"))
        assertFalse(id == KlipyClient.customerId("user-2"))
        assertFalse("user-1" in id)
    }

    private val pageJson =
        """
        {"result":true,"data":{"data":[
          {"id":11,"slug":"cat","title":"Cat","file":{
            "hd":{"gif":{"url":"https://static.klipy.com/hd.gif","width":800,"height":600}},
            "md":{"webp":{"url":"https://static1.klipy.com/md.webp","width":400,"height":300},
                  "gif":{"url":"https://static1.klipy.com/md.gif","width":400,"height":300}},
            "sm":{"gif":{"url":"https://static.klipy.com/sm.gif","width":200,"height":150}}}},
          {"id":12,"slug":"evil","file":{"md":{"gif":{"url":"https://evil.com/md.gif","width":1,"height":1}}}},
          {"slug":"noid","file":{"xs":{"webp":{"url":"https://static.klipy.com/xs.webp","width":100,"height":50}},
            "hd":{"gif":{"url":"https://static.klipy.com/hd2.gif","width":1000,"height":500}}}}
        ],"current_page":2,"has_next":true}}
        """.trimIndent()

    @Test
    fun parsesKlipyPage() {
        val page = KlipyClient.parsePage(pageJson, requestedPage = 9)!!
        assertEquals(2, page.page)
        assertTrue(page.hasNext)
        assertEquals(listOf("11", "noid"), page.items.map { it.id })
        val cat = page.items[0]
        assertEquals("Cat", cat.title)
        assertEquals("https://static.klipy.com/sm.gif", cat.preview.url)
        // md, webp first.
        assertEquals(KlipyClient.Rendition("https://static1.klipy.com/md.webp", 400, 300), cat.send)
        assertEquals(ChatGif("https://static1.klipy.com/md.webp", 400, 300), cat.toChatGif())
        val noid = page.items[1]
        assertEquals("GIF", noid.title)
        assertEquals("https://static.klipy.com/xs.webp", noid.preview.url)
        assertEquals("https://static.klipy.com/hd2.gif", noid.send.url)
        assertNull(KlipyClient.parsePage("""{"data":[]}""", 1))
        assertNull(KlipyClient.parsePage("not json", 1))
        assertEquals(1, KlipyClient.parsePage("""{"data":{"data":[]}}""", 1)!!.page)
        // Sanity: the fixture itself is valid JSON.
        Json.parseToJsonElement(pageJson)
    }

    @Test
    fun pickerStateAppendsDedupesAndDropsStale() {
        val page = KlipyClient.parsePage(pageJson, 1)!!
        var s = GifPickerState(query = "cat")
        assertEquals(1, s.nextPage(reset = true))
        s = s.loading().loaded("cat", page, reset = true)
        assertEquals(2, s.items.size)
        assertEquals(3, s.nextPage(reset = false))
        assertTrue(s.canLoadMore())
        // A repeated page appends nothing new.
        s = s.loaded("cat", page.copy(page = 3, hasNext = false), reset = false)
        assertEquals(2, s.items.size)
        assertFalse(s.canLoadMore())
        // Results for an old query are ignored.
        val typed = s.copy(query = "dog")
        assertEquals(typed, typed.loaded("cat", page, reset = true))
        assertEquals(typed, typed.failed("cat", "x", reset = true))
        val failed = typed.loading().failed("dog", GifPickerState.LOAD_ERROR, reset = true)
        assertTrue(failed.items.isEmpty())
        assertFalse(failed.isLoading)
        assertEquals(GifPickerState.LOAD_ERROR, failed.error)
    }

    @Test
    fun clientWithoutKeyIsNotConfigured() {
        assertFalse(KlipyClient(appKey = null).isConfigured)
        assertFalse(KlipyClient(appKey = " ").isConfigured)
        assertTrue(KlipyClient(appKey = "k").isConfigured)
    }
}
