package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret

/** UI label for the stored `beacon_type` enum / string. */
fun beaconTypeDisplayLabel(raw: String?, kind: MapBeaconKind): String =
    when (raw?.lowercase()) {
        "soundtrack" -> "Soundtrack"
        "sos" -> "SOS"
        "study" -> "Study"
        "hazard" -> "Hazard"
        "utility" -> "Utility"
        "hazard_utility" -> "Hazard / utility (legacy)"
        "transit" -> "Transit"
        "recreation" -> "Recreation"
        "hobby" -> "Hobby"
        "swag" -> "Swag"
        "capacity" -> "Capacity"
        "scavenger" -> "Scavenger"
        null -> kind.userFacingLabel()
        else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

fun MapBeaconKind.userFacingLabel(): String =
    when (this) {
        MapBeaconKind.SOUNDTRACK -> "Soundtrack"
        MapBeaconKind.SOS -> "SOS"
        MapBeaconKind.HAZARD -> "Hazard"
        MapBeaconKind.UTILITY -> "Utility"
        MapBeaconKind.STUDY -> "Study"
        MapBeaconKind.SOCIAL_VIBE -> "Social vibe"
        MapBeaconKind.OTHER -> "Beacon"
    }

fun MapBeacon.displayTypeTitle(): String =
    beaconTypeDisplayLabel(sourceBeaconType, kind)
