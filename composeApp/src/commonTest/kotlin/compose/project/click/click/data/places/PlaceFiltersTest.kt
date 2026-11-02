package compose.project.click.click.data.places

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Place filters, chips, storage and Nearby ordering (iOS `PlaceFilterTests`). */
class PlaceFiltersTest {
    private val fixturePlaces: List<PlaceSummaryDto> =
        requireNotNull(decodeNearbyPlaces(Json.parseToJsonElement("""{"places":${PlaceFilterFixture.placesJson}}""")))

    @Test
    fun sharedFixture_everyCaseMatchesWebAndIos() {
        assertEquals(5, fixturePlaces.size)
        assertEquals(14, PlaceFilterFixture.cases.size)
        PlaceFilterFixture.cases.forEach { case ->
            val filters = PlaceFilters.decode(case.filtersJson)
            assertEquals(case.expectedIds, filters.apply(fixturePlaces).map { it.id }, case.name)
        }
    }

    @Test
    fun noFilters_isInactiveAndShowsEverything() {
        assertFalse(PlaceFilters.NONE.isActive)
        assertEquals(fixturePlaces, PlaceFilters.NONE.apply(fixturePlaces))
        assertTrue(PlaceFilters(hasHub = true).isActive)
    }

    @Test
    fun storage_roundTripsAndCorruptValuesReadAsNone() {
        val filters =
            PlaceFilters(
                categories = setOf(PlaceCategory.BAR, PlaceCategory.CAFE),
                pulseNow = true,
                minReports = 5,
                energies = setOf(EnergyLabel.PACKED, EnergyLabel.LIVELY),
                openNow = true,
                hereNow = true,
            )
        assertEquals(filters, PlaceFilters.decode(filters.encode()))
        assertTrue(filters.encode().contains("\"categories\":[\"bar\",\"cafe\"]"))
        assertEquals(PlaceFilters.NONE, PlaceFilters.decode(null))
        assertEquals(PlaceFilters.NONE, PlaceFilters.decode(""))
        assertEquals(PlaceFilters.NONE, PlaceFilters.decode("{not json"))
        assertEquals(PlaceFilters.NONE, PlaceFilters.decode("[1,2]"))
        // Unknown categories and energies are dropped instead of failing the whole value.
        assertEquals(
            PlaceFilters(categories = setOf(PlaceCategory.GYM), energies = setOf(EnergyLabel.CHILL)),
            PlaceFilters.decode("""{"categories":["gym","spaceport"],"energies":["chill","electric"]}"""),
        )
        assertEquals("places.filters.v1", PlaceFilters.STORAGE_KEY)
    }

    @Test
    fun chips_toggleTheirFieldAndEnergyPresetsAreExclusive() {
        var f = PlaceFilters.NONE
        f = PlaceFilterChip.PULSE_NOW.toggle(f)
        assertTrue(f.pulseNow && PlaceFilterChip.PULSE_NOW.isOn(f))
        f = PlaceFilterChip.LIVELY.toggle(f)
        assertEquals(setOf(EnergyLabel.LIVELY, EnergyLabel.PACKED), f.energies)
        assertTrue(PlaceFilterChip.LIVELY.isOn(f))
        f = PlaceFilterChip.QUIET.toggle(f)
        assertEquals(setOf(EnergyLabel.CHILL, EnergyLabel.STEADY), f.energies)
        assertFalse(PlaceFilterChip.LIVELY.isOn(f))
        f = PlaceFilterChip.QUIET.toggle(f)
        assertTrue(f.energies.isEmpty())
        // A hand-picked energy set from the sheet lights neither preset.
        assertFalse(PlaceFilterChip.LIVELY.isOn(f.copy(energies = setOf(EnergyLabel.PACKED))))
        listOf(
            PlaceFilterChip.EVENTS_TODAY,
            PlaceFilterChip.OPEN_NOW,
            PlaceFilterChip.BEEN_HERE,
            PlaceFilterChip.CLICKS_BEEN_HERE,
        ).forEach { chip ->
            assertTrue(chip.isOn(chip.toggle(PlaceFilters.NONE)), chip.label)
            assertFalse(chip.isOn(chip.toggle(chip.toggle(PlaceFilters.NONE))), chip.label)
        }
        assertEquals(
            listOf("Pulse now", "Lively+", "Quiet", "Events today", "Open now", "Been here", "Clicks have been"),
            PlaceFilterChip.entries.map { it.label },
        )
    }

    @Test
    fun minReports_choicesAndLabels() {
        assertEquals(listOf("Any", "2+", "5+", "10+"), PlaceFilters.MIN_REPORT_CHOICES.map(::minReportsLabel))
    }

    @Test
    fun ordering_liveEventThenNewestLivePulseThenDistance() {
        fun place(
            id: String,
            distance: Double?,
            liveEvent: Boolean = false,
            pulseAt: String? = null,
            pulseState: String = "live",
        ) = PlaceSummaryDto(
            id = id,
            latitude = 0.0,
            longitude = 0.0,
            distanceMeters = distance,
            nextEvent = if (liveEvent) PlaceEventRefDto(beaconId = "e-$id", isLive = true) else null,
            pulse = if (pulseAt != null) PulseSummaryDto(state = pulseState, newestAt = pulseAt) else PulseSummaryDto(),
        )
        val sorted =
            PlaceOrdering.sorted(
                listOf(
                    place("far", 900.0),
                    place("unknown", null),
                    place("near", 50.0),
                    place("stalePulse", 10.0, pulseAt = "2026-10-01T20:00:00Z", pulseState = "stale"),
                    place("olderPulse", 500.0, pulseAt = "2026-10-01T18:00:00Z"),
                    place("newerPulse", 800.0, pulseAt = "2026-10-01T19:00:00Z"),
                    place("liveEvent", 2_000.0, liveEvent = true),
                ),
            )
        assertEquals(
            listOf("liveEvent", "newerPulse", "olderPulse", "stalePulse", "near", "far", "unknown"),
            sorted.map { it.id },
        )
    }
}
