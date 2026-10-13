package compose.project.click.click.platform // pragma: allowlist secret

/**
 * What kind of device this is ("Pixel 9", "Galaxy S24"), never its user-given name. Sent as
 * `device_label` on `POST /api/chat/devices` and shown when another device approves this one, in
 * Settings › Devices and in "Approve on your …" copy. Null when the platform can't tell.
 */
expect fun thisDeviceLabel(): String?

/** click-web keeps labels of 1..64 characters and drops anything longer. */
const val DEVICE_LABEL_MAX_LENGTH = 64

/**
 * Samsung reports model codes ("SM-S921U"); the region suffix after the 4-digit family is dropped
 * before the lookup. Families seen most in the field; anything else is "Galaxy phone".
 */
internal val SAMSUNG_FAMILIES: Map<String, String> =
    mapOf(
        "SM-S901" to "Galaxy S22",
        "SM-S906" to "Galaxy S22+",
        "SM-S908" to "Galaxy S22 Ultra",
        "SM-S911" to "Galaxy S23",
        "SM-S916" to "Galaxy S23+",
        "SM-S918" to "Galaxy S23 Ultra",
        "SM-S711" to "Galaxy S23 FE",
        "SM-S921" to "Galaxy S24",
        "SM-S926" to "Galaxy S24+",
        "SM-S928" to "Galaxy S24 Ultra",
        "SM-S721" to "Galaxy S24 FE",
        "SM-S931" to "Galaxy S25",
        "SM-S936" to "Galaxy S25+",
        "SM-S938" to "Galaxy S25 Ultra",
        "SM-F731" to "Galaxy Z Flip5",
        "SM-F741" to "Galaxy Z Flip6",
        "SM-F946" to "Galaxy Z Fold5",
        "SM-F956" to "Galaxy Z Fold6",
        "SM-A156" to "Galaxy A15 5G",
        "SM-A256" to "Galaxy A25 5G",
        "SM-A356" to "Galaxy A35 5G",
        "SM-A556" to "Galaxy A55 5G",
    )

internal val BRAND_NAMES: Map<String, String> =
    mapOf(
        "google" to "Google",
        "samsung" to "Samsung",
        "oneplus" to "OnePlus",
        "motorola" to "Motorola",
        "xiaomi" to "Xiaomi",
        "oppo" to "OPPO",
        "vivo" to "vivo",
        "sony" to "Sony",
        "nothing" to "Nothing",
        "huawei" to "Huawei",
        "honor" to "HONOR",
        "lge" to "LG",
        "lg" to "LG",
        "asus" to "ASUS",
        "realme" to "realme",
        "fairphone" to "Fairphone",
    )

/**
 * A marketing name for `Build.MANUFACTURER` / `Build.MODEL`: Pixels already report "Pixel 9";
 * Samsung codes map to their Galaxy names; other models are prefixed with the brand unless they
 * already start with it. "Android phone" when nothing is known. At most [DEVICE_LABEL_MAX_LENGTH].
 */
fun friendlyDeviceLabel(
    manufacturer: String?,
    model: String?,
): String {
    val brandKey = manufacturer?.trim()?.lowercase().orEmpty()
    val brand = BRAND_NAMES[brandKey] ?: manufacturer?.trim()?.replaceFirstChar { it.uppercase() }.orEmpty()
    val rawModel = model?.trim().orEmpty()
    val label =
        when {
            rawModel.isEmpty() -> brand.takeIf { it.isNotEmpty() }?.let { "$it phone" } ?: "Android phone"
            rawModel.startsWith("SM-", ignoreCase = true) ->
                SAMSUNG_FAMILIES[rawModel.uppercase().take(7)] ?: "Galaxy phone"
            rawModel.startsWith("Pixel", ignoreCase = true) -> rawModel
            brand.isEmpty() || rawModel.startsWith(brand, ignoreCase = true) -> rawModel
            else -> "$brand $rawModel"
        }
    return neverABrowserLabel(label.take(DEVICE_LABEL_MAX_LENGTH).trim())
}

/**
 * click-web treats a label containing " on " or equal to "Web browser" as a browser and skips the
 * approval request for browsers when the account has a passkey recovery vault
 * (`web/lib/chat/deviceLabel.ts`, sheet 10 §A6). An app's label must never look like one.
 */
internal fun neverABrowserLabel(label: String): String {
    var cleaned = label
    while (cleaned.contains(" on ")) cleaned = cleaned.replace(" on ", " ")
    return if (cleaned.isBlank() || cleaned.equals("web browser", ignoreCase = true)) "Android phone" else cleaned
}
