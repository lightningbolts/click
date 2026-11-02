package compose.project.click.click.ui.components

import compose.project.click.click.connect.ConnectionVerification
import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.models.WeatherSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G4-6: encounter details on the result (iOS `PostConnectView.encounterDetails`, `PostConnectModel.load`). */
class EncounterDetailsTest {
    private val utc = TimeZone.UTC
    private val at = Instant.parse("2026-11-02T15:04:00Z").toEpochMilliseconds()

    private fun enc(
        id: String = "e1",
        at: String = "2026-11-02T15:04:00Z",
        place: String? = null,
        display: String? = null,
        lat: Double? = null,
        weather: WeatherSnapshot? = null,
        event: String? = null,
    ) = ConnectionEncounter(
        id = id,
        connectionId = "c",
        encounteredAt = at,
        locationName = place,
        displayLocation = display,
        gpsLat = lat,
        gpsLon = lat?.let { -122.3 },
        weatherSnapshot = weather,
        eventBeaconTitle = event,
    )

    @Test
    fun whenLineMatchesTheAbbreviatedShortStyle() {
        assertEquals("Nov 2, 2026 at 3:04 PM", encounterWhenLine(at, utc))
        assertEquals("Jan 1, 2026 at 12:05 AM", encounterWhenLine(Instant.parse("2026-01-01T00:05:00Z").toEpochMilliseconds(), utc))
    }

    @Test
    fun placeIsTheVenueOrFirstComponentNeverAFullAddress() {
        assertEquals("Victrola Coffee", enc(place = "Victrola Coffee, 411 15th Ave E").detailsPlace())
        assertEquals("Capitol Hill", enc(display = "Capitol Hill, Seattle, WA").detailsPlace())
        assertNull(enc().detailsPlace())
    }

    @Test
    fun weatherLine() {
        assertEquals("12°C · Clear", enc(weather = WeatherSnapshot(condition = "Clear", temperatureCelsius = 11.6)).detailsWeather())
        assertEquals("Rain", enc(weather = WeatherSnapshot(condition = "Rain")).detailsWeather())
        assertNull(enc().detailsWeather())
    }

    @Test
    fun rowsInIosOrderWithOptionalRowsLeftOut() {
        val rows =
            encounterDetailRows(
                connectedAtMs = at,
                encounter =
                    enc(place = "Cafe", event = "Open mic", weather = WeatherSnapshot(condition = "Clear", temperatureCelsius = 12.0)),
                isLoading = false,
                groupPeerNames = listOf("Ada Byron", "Ben", "Cy"),
                verification = ConnectionVerification.forTap(true, true, true, 8.0),
                isQr = false,
                timeZone = utc,
            )
        assertEquals(
            listOf("When", "Where", "With", "Event", "Weather", "Verified by"),
            rows.map { it.label },
        )
        assertEquals("Ada, Ben, and Cy", rows[2].value)
        assertEquals("Bluetooth · Sound · Location ±8 m", rows.last().value)
    }

    @Test
    fun whereStatesWhileLoadingAndAfter() {
        fun where(
            encounter: ConnectionEncounter?,
            loading: Boolean,
            verification: ConnectionVerification? = null,
        ) = encounterDetailRows(at, encounter, loading, emptyList(), verification, isQr = true, timeZone = utc)
            .first { it.kind == EncounterDetailKind.Where }

        assertEquals("Finding the place…", where(null, loading = true).value)
        assertTrue(where(null, loading = true).pending)
        assertEquals("Place name unavailable", where(enc(lat = 47.6), loading = false).value)
        assertEquals("Place name unavailable", where(null, false, ConnectionVerification.forQr(9.0)).value)
        assertEquals("Not recorded", where(null, loading = false).value)
        // One peer: no "With" row.
        assertFalse(
            encounterDetailRows(at, null, false, listOf("Ada"), null, false, timeZone = utc).any { it.kind == EncounterDetailKind.With },
        )
    }

    @Test
    fun latestTapEncounterMergesReportersAndPicksTheNewest() {
        val rows =
            listOf(
                enc(id = "old", at = "2026-10-01T10:00:00Z", place = "Old spot"),
                enc(id = "mine", at = "2026-11-02T15:04:00Z", lat = 47.6),
                enc(id = "theirs", at = "2026-11-02T15:04:00Z", place = "Cafe"),
            )
        val latest = latestTapEncounter(rows)!!
        assertEquals("Cafe", latest.detailsPlace())
        assertEquals(47.6, latest.gpsLat)
    }

    @Test
    fun placeIsFetchedOnceMoreOnlyWhenItCanStillArrive() =
        runTest {
            assertTrue(shouldRetryForPlace(null))
            assertTrue(shouldRetryForPlace(enc(lat = 47.6)))
            assertFalse(shouldRetryForPlace(enc()))
            assertFalse(shouldRetryForPlace(enc(place = "Cafe", lat = 47.6)))

            val responses = ArrayDeque(listOf(listOf(enc(lat = 47.6)), listOf(enc(lat = 47.6, place = "Cafe"))))
            val loaded = mutableListOf<String?>()
            var slept = 0L
            loadTapEncounter("c", fetch = { responses.removeFirst() }, onLoaded = { loaded += it?.detailsPlace() }, sleep = { slept += it })
            assertEquals(listOf(null, "Cafe"), loaded)
            assertEquals(ENCOUNTER_PLACE_RETRY_DELAY_MS, slept)

            var calls = 0
            loadTapEncounter("c", fetch = {
                calls++
                listOf(enc(place = "Cafe"))
            }, onLoaded = {}, sleep = {})
            assertEquals(1, calls)
        }
}
