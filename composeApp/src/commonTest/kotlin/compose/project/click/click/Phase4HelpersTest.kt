package compose.project.click.click

import compose.project.click.click.data.IdentityCache
import compose.project.click.click.data.api.DisplayNamesResponseDto
import compose.project.click.click.data.api.EventBookmarkItemDto
import compose.project.click.click.data.api.GuestListEntryDto
import compose.project.click.click.data.models.AvailabilityIntentRow
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.models.User
import compose.project.click.click.deeplink.AppDeepLink
import compose.project.click.click.deeplink.AppDeepLinkRouter
import compose.project.click.click.events.EventEditDraft
import compose.project.click.click.events.EventListingOptions
import compose.project.click.click.events.EventReminders
import compose.project.click.click.events.EventRsvpRequestStatus
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.buildEventEditPatch
import compose.project.click.click.events.customEventCategory
import compose.project.click.click.events.eventEditValidationError
import compose.project.click.click.events.joinRequestDecisionMessage
import compose.project.click.click.events.sanitizeEventCategories
import compose.project.click.click.notifications.PushRoute
import compose.project.click.click.notifications.PushRoutes
import compose.project.click.click.telemetry.ConnectionFlowTelemetry
import compose.project.click.click.ui.components.ordinalLabel
import compose.project.click.click.ui.components.reconnectSubtitle
import compose.project.click.click.ui.screens.availabilityStatusLabel
import compose.project.click.click.ui.screens.clicksCountLabel
import compose.project.click.click.ui.screens.guestEntryLabel
import compose.project.click.click.ui.screens.meCoreItems
import compose.project.click.click.ui.screens.savedEventSections
import compose.project.click.click.ui.theme.AppearanceMode
import compose.project.click.click.ui.utils.AppPermissionState
import compose.project.click.click.ui.utils.PermissionRowAction
import compose.project.click.click.ui.utils.permissionRowAction
import compose.project.click.click.util.isAllowedMusicShareUrl
import compose.project.click.click.utils.GeocodedPlace
import compose.project.click.click.viewmodel.SyncBanner
import compose.project.click.click.viewmodel.syncBannerState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase4HelpersTest {
    // region D1 deep links: one assertion per route

    @Test
    fun deepLinkRoutes() {
        val parse = AppDeepLinkRouter::parse
        assertEquals(AppDeepLink.Chat("c-1", null), parse("click://chat/c-1"))
        assertEquals(AppDeepLink.Chat("c-1", "m-9"), parse("click://chat/c-1?m=m-9"))
        assertEquals(AppDeepLink.Chat("c-1", "m-9"), parse("click://chat/c-1?message=m-9"))
        assertEquals(AppDeepLink.Profile("u-1"), parse("click://profile/u-1"))
        assertEquals(AppDeepLink.Profile("u-1"), parse("click://u/u-1"))
        assertEquals(AppDeepLink.MyQr, parse("click://myqr"))
        assertEquals(AppDeepLink.Scan, parse("click://scan"))
        assertEquals(AppDeepLink.TapConnect, parse("click://tap"))
        assertEquals(AppDeepLink.Search("coffee near me"), parse("click://search?q=coffee+near%20me"))
        assertEquals(AppDeepLink.Search("jazz"), parse("https://joinclick.co/search?q=jazz"))
        assertEquals(AppDeepLink.Search("jazz"), parse("https://www.joinclick.co/search?q=jazz"))
        assertEquals(AppDeepLink.Search(""), parse("click://search"))
        assertEquals(AppDeepLink.Search("café"), parse("click://search?q=caf%C3%A9"))
    }

    @Test
    fun deepLinksLeaveOtherRoutesAlone() {
        val parse = AppDeepLinkRouter::parse
        assertNull(parse("click://chat"))
        assertNull(parse("click://profile/"))
        assertNull(parse("click://login?code=1"))
        assertNull(parse("click://hub/h1"))
        assertNull(parse("https://joinclick.co/c/u1"))
        assertNull(parse("https://evil.example/search?q=x"))
        assertNull(parse("not a url"))
    }

    // endregion

    // region D2 push routes

    @Test
    fun pushRouteTableCoversEveryType() {
        fun r(vararg pairs: Pair<String, String>) = PushRoutes.route(mapOf(*pairs))
        assertEquals(PushRoute.DirectChat("ch", "cn"), r("type" to "chat_message", "chat_id" to "ch", "connection_id" to "cn"))
        assertEquals(PushRoute.GroupChat("g"), r("type" to "chat_message", "chat_id" to "g", "group_id" to "grp"))
        assertEquals(PushRoute.DirectChat("", "cn"), r("type" to "disposable_reveal", "connection_id" to "cn"))
        assertEquals(PushRoute.Event("b"), r("type" to "event_reminder", "beacon_id" to "b"))
        assertEquals(PushRoute.Event("b"), r("type" to "shared_upcoming_event", "beacon_id" to "b"))
        assertEquals(PushRoute.Hub("h"), r("type" to "hub_message", "hub_id" to "h"))
        assertEquals(PushRoute.Profile("p"), r("type" to "archive_warning", "peer_user_id" to "p"))
        assertEquals(PushRoute.Connections, r("type" to "archive_warning"))
        assertEquals(PushRoute.Profile("p"), r("type" to "reconnect_nudge", "peer_user_id" to "p"))
        assertEquals(PushRoute.Connections, r("type" to "availability_match", "connection_id" to "cn"))
        assertEquals(PushRoute.OpenApp, r("type" to "something_new"))
        assertEquals(PushRoute.OpenApp, r("type" to "event_reminder"))
        assertTrue("archive_warning" in PushRoutes.serverTextTypes)
        assertFalse("chat_message" in PushRoutes.serverTextTypes)
    }

    // endregion

    // region C3 event reminders

    @Test
    fun eventRemindersAtSixtyAndFifteen() {
        val start = 10_000_000_000L
        assertEquals(listOf(60 to start - 3_600_000L, 15 to start - 900_000L), EventReminders.triggers(start, start - 7_200_000L))
        // 30 minutes out: only T-15 is still ahead.
        assertEquals(listOf(15 to start - 900_000L), EventReminders.triggers(start, start - 1_800_000L))
        assertTrue(EventReminders.triggers(start, start).isEmpty())
        assertEquals("event.b1.60", EventReminders.reminderId("b1", 60))
        assertEquals("Starts in an hour · Pier 39", EventReminders.body(60, " Pier 39 "))
        assertEquals("Starts in 15 minutes", EventReminders.body(15, null))
    }

    @Test
    fun eventReminderSubjectsFromSavedEvents() {
        val saved =
            listOf(
                EventBookmarkItemDto(beaconId = "b1", title = "Jazz", eventStartAt = "2030-01-01T20:00:00Z", locationName = "Blue Note"),
                EventBookmarkItemDto(beaconId = "b2", title = "No time"),
            )
        val subjects = EventReminders.subjects(goingBeaconIds = setOf("unknown"), bookmarks = saved, beaconById = { null })
        assertEquals(listOf("b1"), subjects.map { it.beaconId })
        assertEquals(Instant.parse("2030-01-01T20:00:00Z").toEpochMilliseconds(), subjects.single().startEpochMs)
        assertEquals(2, EventReminders.reminders(subjects.single(), 0L).size)
    }

    // endregion

    // region B: Me and Settings

    @Test
    fun meRootLabels() {
        val viewer = "me"

        fun conn(
            id: String,
            peer: String,
        ) = Connection(id = id, created = 1L, expiry = Long.MAX_VALUE, user_ids = listOf(viewer, peer), status = "kept")
        val connections = listOf(conn("c1", "p1"), conn("c2", "p2"), conn("c3", "p3"))
        val users = mapOf("p1" to User(id = "p1", name = "Sam Lee"), "p2" to User(id = "p2", name = "Lena"))
        val core =
            meCoreItems(connections, setOf("c1", "c3"), hiddenConnectionIds = setOf("c3"), connectedUsers = users, viewerUserId = viewer)
        assertEquals(listOf("p1"), core.map { it.userId })
        assertEquals("Sam Lee", core.single().displayName)
        assertEquals("2 Clicks", clicksCountLabel(connections, setOf("c3")))
        assertEquals("1 Click", clicksCountLabel(connections.take(1), emptySet()))
        assertNull(clicksCountLabel(emptyList(), emptySet()))
        assertEquals("Down for coffee", availabilityStatusLabel(listOf(AvailabilityIntentRow(intentTag = "Coffee"))))
        assertNull(availabilityStatusLabel(emptyList()))
    }

    @Test
    fun appearanceDefaultsToSystem() {
        assertEquals(AppearanceMode.System, AppearanceMode.fromStoredDarkMode(null))
        assertEquals(AppearanceMode.Dark, AppearanceMode.fromStoredDarkMode(true))
        assertNull(AppearanceMode.System.toStoredDarkMode())
        assertTrue(AppearanceMode.System.resolveDark(systemDark = true))
        assertFalse(AppearanceMode.Light.resolveDark(systemDark = true))
    }

    @Test
    fun permissionRowsOfferAllowThenSettings() {
        assertEquals(PermissionRowAction.Allow, permissionRowAction(AppPermissionState.CanRequest))
        assertEquals(PermissionRowAction.OpenSettings, permissionRowAction(AppPermissionState.Blocked))
        assertEquals(PermissionRowAction.Label("Allowed"), permissionRowAction(AppPermissionState.Granted))
        assertEquals(PermissionRowAction.Label("Not needed"), permissionRowAction(AppPermissionState.NotNeeded))
    }

    @Test
    fun savedEventsSplitUpcomingAndPast() {
        val now = Instant.parse("2030-06-01T12:00:00Z").toEpochMilliseconds()
        val bookmarks =
            listOf(
                EventBookmarkItemDto(beaconId = "past", eventStartAt = "2030-05-01T12:00:00Z", eventEndAt = "2030-05-01T14:00:00Z"),
                EventBookmarkItemDto(beaconId = "later", eventStartAt = "2030-07-01T12:00:00Z"),
                EventBookmarkItemDto(beaconId = "soon", eventStartAt = "2030-06-02T12:00:00Z"),
                EventBookmarkItemDto(beaconId = "live", eventStartAt = "2030-06-01T10:00:00Z"),
                EventBookmarkItemDto(beaconId = "unknown"),
            )
        val sections = savedEventSections(bookmarks, now)
        assertEquals(listOf("live", "soon", "later"), sections.upcoming.map { it.beaconId })
        assertEquals(setOf("past", "unknown"), sections.past.map { it.beaconId }.toSet())
    }

    // endregion

    // region D5 identity cache

    @Test
    fun identityCacheBatchesAndExpires() =
        runTest {
            val entry =
                IdentityCache.Entry(
                    compose.project.click.click.data
                        .UserIdentity("A", null),
                    fetchedAtMs = 1_000,
                )
            val cache = mapOf("a" to entry)
            assertEquals(listOf("b"), IdentityCache.idsNeedingFetch(listOf("a", "b", " ", "b"), cache, nowMs = 2_000))
            assertEquals(listOf("a"), IdentityCache.idsNeedingFetch(listOf("a"), cache, nowMs = 1_000 + IdentityCache.TTL_MS))

            val calls = mutableListOf<List<String>>()
            val previous = IdentityCache.fetcher
            IdentityCache.clear()
            IdentityCache.fetcher = { ids ->
                calls += ids
                Result.success(DisplayNamesResponseDto(names = ids.associateWith { "Name $it" }))
            }
            try {
                val first = IdentityCache.resolve(listOf("x", "y"))
                assertEquals("Name x", first["x"]?.name)
                IdentityCache.resolve(listOf("x", "y"))
                assertEquals(1, calls.size)
            } finally {
                IdentityCache.fetcher = previous
                IdentityCache.clear()
            }
        }

    // endregion

    // region C events

    @Test
    fun customCategoriesAndMusicLinks() {
        assertEquals("Book club", customEventCategory("  Book   club ", listOf("Social")))
        assertNull(customEventCategory("social", listOf("Social")))
        assertNull(customEventCategory("x".repeat(25), emptyList()))
        assertEquals(
            listOf("Social", "Jazz", "Food"),
            sanitizeEventCategories(
                listOf(
                    "Social",
                    "jazz".replaceFirstChar {
                        it.uppercase()
                    },
                    "social",
                    "Food",
                    "Extra",
                ),
            ),
        )
        assertTrue(isAllowedMusicShareUrl("https://open.spotify.com/track/1"))
        assertTrue(isAllowedMusicShareUrl("https://youtu.be/abc"))
        assertFalse(isAllowedMusicShareUrl("http://open.spotify.com/track/1"))
        assertFalse(isAllowedMusicShareUrl("https://soundcloud.com/a/b"))
        assertFalse(isAllowedMusicShareUrl("https://evil.com/?q=open.spotify.com"))
        assertFalse(isAllowedMusicShareUrl("https://open.spotify.com@evil.com/"))
    }

    @Test
    fun eventEditValidationAndPatch() {
        val now = 1_000_000_000L
        val draft =
            EventEditDraft(
                title = "Jazz night",
                description = "Bring friends",
                newPlace = null,
                schedule = EventSchedule(now + 3_600_000, now + 7_200_000),
                categories = listOf("Social", "Jazz"),
                listing = EventListingOptions(),
                capacityText = "",
            )
        assertNull(eventEditValidationError(draft, originalStartEpochMs = now + 3_600_000, nowEpochMs = now))
        assertEquals("Please add a title.", eventEditValidationError(draft.copy(title = " "), now, now))
        // An event that already started keeps its (past) start without an error.
        val started = draft.copy(schedule = EventSchedule(now - 60_000_000, now + 60_000))
        assertNull(eventEditValidationError(started, originalStartEpochMs = now - 60_000_000, nowEpochMs = now))
        assertEquals(
            "Event start must be in the future.",
            eventEditValidationError(started, originalStartEpochMs = now + 1, nowEpochMs = now),
        )

        val place = GeocodedPlace(latitude = 1.5, longitude = 2.5, displayName = "1 Main St, Town", shortLabel = "Main St")
        val patch = buildEventEditPatch(draft.copy(newPlace = place, removePhoto = true), newImageUrl = null)
        val meta = patch.metadata!!
        assertEquals(JsonPrimitive("Jazz night"), meta["title"])
        assertEquals(JsonArray(listOf(JsonPrimitive("Social"), JsonPrimitive("Jazz"))), meta["event_categories"])
        assertEquals(JsonNull, meta["image_url"])
        assertEquals(JsonNull, meta["event_capacity"])
        assertEquals(JsonPrimitive("Main St"), meta["location_name"])
        assertEquals(1.5, patch.lat)
        assertEquals(2.5, patch.lon)
        val noMove = buildEventEditPatch(draft.copy(capacityText = "40"), newImageUrl = "https://img")
        assertNull(noMove.lat)
        assertEquals(JsonPrimitive(40), noMove.metadata!!["event_capacity"])
        assertEquals(JsonPrimitive("https://img"), noMove.metadata!!["image_url"])
    }

    @Test
    fun joinRequestDecisions() {
        val pending = EventRsvpRequestStatus.PENDING
        assertNull(joinRequestDecisionMessage(null, pending, false))
        assertNull(joinRequestDecisionMessage(pending, pending, false))
        assertEquals(
            "You're in. The host approved your request.",
            joinRequestDecisionMessage(pending, EventRsvpRequestStatus.APPROVED, false),
        )
        assertEquals("You're in. The host approved your request.", joinRequestDecisionMessage(pending, null, true))
        assertEquals("The host declined your request.", joinRequestDecisionMessage(pending, EventRsvpRequestStatus.DENIED, false))
    }

    @Test
    fun guestLabels() {
        assertEquals("a***@x.com", guestEntryLabel(GuestListEntryDto(id = "1", emailTruncated = "a***@x.com")))
        assertEquals("@sam", guestEntryLabel(GuestListEntryDto(id = "2", instagramHandle = "@sam")))
        assertEquals("Guest", guestEntryLabel(GuestListEntryDto(id = "3")))
    }

    @Test
    fun firstMetAndReconnectCopy() {
        val encounter =
            ConnectionEncounter(id = "e1", connectionId = "c", encounteredAt = "2030-01-01T00:00:00Z", locationName = "Blue Bottle")
        val conn =
            Connection(
                id = "c",
                created = 1L,
                expiry = Long.MAX_VALUE,
                user_ids = listOf("a", "b"),
                connectionEncounters = listOf(encounter),
            )
        assertEquals("Met at Blue Bottle", conn.firstMetLabel())
        assertEquals("First met here", conn.copy(connectionEncounters = emptyList()).firstMetLabel())
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "22nd", "101st"),
            listOf(1, 2, 3, 4, 11, 12, 22, 101).map(::ordinalLabel),
        )
        assertEquals("3rd time with Sam", reconnectSubtitle("Sam", priorEncounters = 2))
        assertEquals("Another crossing with Sam", reconnectSubtitle("Sam", priorEncounters = 0))
    }

    // endregion

    // region D3 + D4

    @Test
    fun syncBannerDistinguishesOfflineFromRefreshFailed() {
        assertEquals(SyncBanner.Offline, syncBannerState(offlineDebounced = true, loadError = null, serverReachable = null))
        assertEquals(SyncBanner.None, syncBannerState(false, null, null))
        assertEquals(SyncBanner.None, syncBannerState(false, "boom", null))
        assertEquals(SyncBanner.Offline, syncBannerState(false, "boom", false))
        assertEquals(SyncBanner.RefreshFailed, syncBannerState(false, "boom", true))
    }

    @Test
    fun telemetryQueueIsBounded() {
        val full = (1..ConnectionFlowTelemetry.QUEUE_MAX).toList()
        val next = ConnectionFlowTelemetry.enqueueBounded(full, 999)
        assertEquals(ConnectionFlowTelemetry.QUEUE_MAX, next.size)
        assertEquals(2, next.first())
        assertEquals(999, next.last())
    }

    // endregion
}
