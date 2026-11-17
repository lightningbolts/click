package compose.project.click.click.platform

/** Regions that read temperatures in °F (CLDR `temperature` preference: US and territories, LR, MM, BS, BZ, KY, PW). */
private val FAHRENHEIT_REGIONS = setOf("US", "PR", "GU", "VI", "AS", "MP", "UM", "LR", "MM", "BS", "BZ", "KY", "PW")

/**
 * Whether [region] (ISO 3166 alpha-2) reads °F. [unitOverride] is the locale's `mu` extension (Android 14 per-app
 * regional preference): `fahrenhe` / `celsius` / `kelvin`, which wins over the region.
 */
fun usesFahrenheitFor(
    region: String?,
    unitOverride: String? = null,
): Boolean =
    when (unitOverride?.lowercase()) {
        "fahrenhe", "fahrenheit" -> true
        "celsius", "kelvin" -> false
        else -> region?.uppercase() in FAHRENHEIT_REGIONS
    }

/** The device's choice of °F over °C for weather labels (iOS formats by locale, `EventWeather.swift`). */
expect fun platformUsesFahrenheit(): Boolean
