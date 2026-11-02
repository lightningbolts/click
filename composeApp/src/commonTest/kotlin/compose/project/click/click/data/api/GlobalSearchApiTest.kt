package compose.project.click.click.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wire shapes against click-web `app/api/search/route.ts` (and `app/api/chat/search/route.ts` hits). */
class GlobalSearchApiTest {
    private val origin = "https://joinclick.co"

    @Test
    fun urlCarriesTheTrimmedEncodedQuery() {
        assertEquals(
            "https://joinclick.co/api/search?q=coffee%20%26%20tea",
            globalSearchUrl("$origin/", "  coffee & tea "),
        )
    }

    @Test
    fun queriesShorterThanTwoCharactersAreNotSent() {
        assertNull(globalSearchUrl(origin, ""))
        assertNull(globalSearchUrl(origin, " a "))
        assertEquals("https://joinclick.co/api/search?q=ab", globalSearchUrl(origin, "ab"))
    }

    @Test
    fun mineAddsTheFlagOnlyWhenAsked() {
        assertEquals("https://joinclick.co/api/search?q=jo&mine=1", globalSearchUrl(origin, "jo", mine = true))
        assertTrue(globalSearchUrl(origin, "jo")!!.endsWith("q=jo"))
    }

    @Test
    fun parsesEverySection() {
        val body =
            """
            {
              "people": [{"userId": "u1", "name": "Ava", "avatarUrl": "https://img/a.png", "context": "In a hub with you"}],
              "events": [{"beaconId": "b1", "title": "Trivia", "locationName": "Pub", "startAt": "2026-11-03T03:00:00.000Z", "imageUrl": null}],
              "hubs": [{"hubId": "h1", "name": "Climbers", "category": "sports"}],
              "hits": [{"messageId": "m1", "chatId": "c1", "conversationId": "c1", "connectionId": "k1", "senderId": "u2",
                        "timestamp": 1700000000000, "snippet": "see you", "chatName": "Ben", "isHub": false}],
              "clicks": [{"connectionId": "k2", "userId": "u3", "name": "Cal", "avatarUrl": null}],
              "groups": [{"groupId": "g1", "name": "Book club"}],
              "places": [{"placeId": "p1", "slug": "blue-door", "name": "Blue Door", "category": "cafe", "city": "Seattle"}]
            }
            """.trimIndent()
        val payload = parseGlobalSearchPayload(body)
        assertEquals(GlobalSearchPerson("u1", "Ava", "https://img/a.png", "In a hub with you"), payload.people.single())
        assertEquals(GlobalSearchEvent("b1", "Trivia", "Pub", "2026-11-03T03:00:00.000Z", null), payload.events.single())
        assertEquals(GlobalSearchHub("h1", "Climbers", "sports"), payload.hubs.single())
        val hit = payload.hits.single()
        assertEquals("m1", hit.messageId)
        assertEquals("k1", hit.connectionId)
        assertEquals(1_700_000_000_000L, hit.timestamp)
        assertEquals("Ben", hit.chatName)
        assertEquals(GlobalSearchClick("k2", "u3", "Cal", null), payload.clicks.single())
        assertEquals(GlobalSearchGroup("g1", "Book club"), payload.groups.single())
        assertEquals(GlobalSearchPlace("p1", "blue-door", "Blue Door", "cafe", "Seattle"), payload.places.single())
    }

    @Test
    fun rowsMissingTheirIdAreDroppedNotTheResponse() {
        val body =
            """
            {"people": [{"name": "No id"}, {"userId": "u1", "name": "Ok"}, {"userId": "u2"}],
             "events": [{"title": "No id"}, {"beaconId": "b1"}],
             "hits": [{"messageId": "m1"}, {"messageId": "m2", "chatId": "c2"}]}
            """.trimIndent()
        val payload = parseGlobalSearchPayload(body)
        assertEquals(listOf("u1"), payload.people.map { it.userId })
        // An untitled event falls back to "Event" (iOS `decodeRemote`).
        assertEquals(GlobalSearchEvent("b1", "Event"), payload.events.single())
        val hit = payload.hits.single()
        assertEquals("m2", hit.messageId)
        // Missing conversation/connection ids fall back to the chat id so the row can still open.
        assertEquals("c2", hit.connectionId)
        assertTrue(payload.hubs.isEmpty())
    }

    @Test
    fun aMalformedOrEmptyBodyIsAnEmptyResult() {
        assertEquals(GlobalSearchPayload(), parseGlobalSearchPayload("<!DOCTYPE html>"))
        assertEquals(GlobalSearchPayload(), parseGlobalSearchPayload("[]"))
        assertEquals(GlobalSearchPayload(), parseGlobalSearchPayload("""{"people": null, "events": {}}"""))
    }

    @Test
    fun theChatSearchFallbackBodyParsesAsHitsOnly() {
        val payload = parseGlobalSearchPayload("""{"hits": [{"messageId": "m", "chatId": "c", "isHub": true, "hubId": "h"}]}""")
        assertTrue(payload.hits.single().isHub)
        assertEquals("h", payload.hits.single().hubId)
        assertTrue(payload.people.isEmpty())
    }

    @Test
    fun failuresMapToOfflineOrShortCopy() {
        assertEquals(GlobalSearchOutcome.Offline, globalSearchFailure(Exception("Unable to resolve host joinclick.co")))
        assertEquals(
            GlobalSearchOutcome.Failed("Click is having trouble right now."),
            globalSearchFailure(ClickWebRequestException(502, "HTTP 502")),
        )
        assertEquals(GlobalSearchOutcome.Failed("Your session expired."), globalSearchFailure(ClickWebRequestException(401, "HTTP 401")))
        assertEquals("Too many searches. Wait a moment.", globalSearchErrorCopy(429))
        assertEquals("Something went wrong.", globalSearchErrorCopy(null))
    }
}
