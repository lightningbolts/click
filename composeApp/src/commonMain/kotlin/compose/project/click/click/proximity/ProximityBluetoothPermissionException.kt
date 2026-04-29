package [REDACTED].proximity

/**
 * Thrown when BLE runtime permissions are missing (Android 12+) so callers can prompt the user.
 */
expect open class ProximityBluetoothPermissionException(message: String) : Throwable
