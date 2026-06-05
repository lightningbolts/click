package compose.project.click.click.encounter

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun degreesToRadians(degrees: Double): Double = degrees * PI / 180.0
private fun radiansToDegrees(radians: Double): Double = radians * 180.0 / PI

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = degreesToRadians(lat2 - lat1)
    val dLon = degreesToRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(degreesToRadians(lat1)) * cos(degreesToRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = degreesToRadians(lon2 - lon1)
    val y = sin(dLon) * cos(degreesToRadians(lat2))
    val x = cos(degreesToRadians(lat1)) * sin(degreesToRadians(lat2)) -
        sin(degreesToRadians(lat1)) * cos(degreesToRadians(lat2)) * cos(dLon)
    val degrees = radiansToDegrees(atan2(y, x))
    return (degrees % 360.0 + 360.0) % 360.0
}

fun compassDirectionLabel(bearing: Double): String {
    val normalized = ((bearing % 360.0) + 360.0) % 360.0
    return when {
        normalized < 22.5 || normalized >= 337.5 -> "North"
        normalized < 67.5 -> "Northeast"
        normalized < 112.5 -> "East"
        normalized < 157.5 -> "Southeast"
        normalized < 202.5 -> "South"
        normalized < 247.5 -> "Southwest"
        normalized < 292.5 -> "West"
        else -> "Northwest"
    }
}

fun formatDistanceFeet(meters: Double): String {
    val feet = (meters * 3.28084).coerceAtLeast(0.0)
    return when {
        feet < 100.0 -> "${feet.toInt()} ft"
        feet < 1_000.0 -> "${(feet / 10.0).toInt() * 10} ft"
        else -> "${(feet / 100.0).toInt() * 100} ft"
    }
}

fun tetherCompassMessage(
    senderName: String,
    receiverLat: Double,
    receiverLng: Double,
    senderLat: Double,
    senderLng: Double,
): String {
    val meters = distanceMeters(receiverLat, receiverLng, senderLat, senderLng)
    val bearing = bearingDegrees(receiverLat, receiverLng, senderLat, senderLng)
    val direction = compassDirectionLabel(bearing)
    val distance = formatDistanceFeet(meters)
    return "$senderName is $distance $direction"
}
