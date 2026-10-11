package compose.project.click.click.platform // pragma: allowlist secret

import compose.project.click.click.events.EventCalendarEntry // pragma: allowlist secret

/** Intents the event page and the Click Pass hand to the OS (calendar, maps, clipboard, files). */
expect object EventPlatformActions {
    /** `ACTION_INSERT` into the device calendar; no permission needed. False when nothing handles it. */
    fun insertCalendarEvent(entry: EventCalendarEntry): Boolean

    /** Opens [uri] in whatever app handles it. False when nothing does. */
    fun openUri(uri: String): Boolean

    /** Opens a `geo:` link through the system chooser ("Other maps app"). */
    fun openGeoChooser(uri: String): Boolean

    fun copyToClipboard(
        label: String,
        text: String,
    )

    /** Saves a PNG to the gallery (MediaStore). True when it landed. */
    fun savePngToGallery(
        bytes: ByteArray,
        displayName: String,
    ): Boolean

    /** `ACTION_SEND` with a PNG and text. */
    fun sharePng(
        bytes: ByteArray,
        displayName: String,
        text: String,
    )
}
