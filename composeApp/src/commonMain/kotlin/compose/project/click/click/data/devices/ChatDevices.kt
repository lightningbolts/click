package compose.project.click.click.data.devices

import compose.project.click.click.data.api.MyChatDeviceDto
import kotlinx.datetime.Instant

/** This device first, then the rest in the server's order (most recently active first), like iOS. */
fun orderDevicesThisFirst(
    devices: List<MyChatDeviceDto>,
    thisDeviceId: String?,
): List<MyChatDeviceDto> = devices.filter { it.deviceId == thisDeviceId } + devices.filter { it.deviceId != thisDeviceId }

/** "Pixel 9" from the server label; "This phone" / "Unnamed device" when it has none. */
fun deviceDisplayName(
    device: MyChatDeviceDto,
    isThisDevice: Boolean,
): String = device.label?.trim()?.takeIf { it.isNotEmpty() } ?: if (isThisDevice) "This phone" else "Unnamed device"

/** "Active 5 minutes ago", else "Added yesterday", else empty (iOS `DevicesSettingsView.activity`). */
fun deviceActivityLabel(
    device: MyChatDeviceDto,
    nowEpochMs: Long,
): String {
    fun parse(iso: String?) = iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
    parse(device.lastSeenAt)?.let { return "Active ${relativePastLabel(it, nowEpochMs)}" }
    parse(device.createdAt)?.let { return "Added ${relativePastLabel(it, nowEpochMs)}" }
    return ""
}

/** "just now", "5 minutes ago", "3 hours ago", "yesterday", "4 days ago", "6 weeks ago", "2 years ago". */
fun relativePastLabel(
    epochMs: Long,
    nowEpochMs: Long,
): String {
    val minutes = ((nowEpochMs - epochMs).coerceAtLeast(0L)) / 60_000L

    fun plural(
        n: Long,
        unit: String,
    ) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> plural(minutes, "minute")
        minutes < 24 * 60 -> plural(minutes / 60, "hour")
        minutes < 48 * 60 -> "yesterday"
        minutes < 14 * 24 * 60 -> plural(minutes / (24 * 60), "day")
        minutes < 365L * 24 * 60 -> plural(minutes / (7 * 24 * 60), "week")
        else -> plural(minutes / (365L * 24 * 60), "year")
    }
}

/** Remove confirmation copy (iOS). */
fun removeDeviceTitle(name: String): String = "Remove $name?"

const val REMOVE_DEVICE_MESSAGE: String =
    "It stops getting new messages. If it's opened again while signed in, it comes back as a new device, " +
        "and your earlier messages need your approval."

const val DEVICES_FOOTER: String =
    "Messages are end-to-end encrypted, and each device holds its own key. Removing a device stops it " +
        "reading new messages; it doesn't sign it out."
