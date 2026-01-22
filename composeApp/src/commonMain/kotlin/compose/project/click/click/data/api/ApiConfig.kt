package compose.project.click.click.data.api

/**
 * API Configuration for different environments
 *
 * iOS Simulator notes:
 * - localhost refers to the simulator itself, not your Mac
 * - Use 127.0.0.1 or your Mac's local IP address
 * - For iOS simulator, you can also use the special host
 */
import compose.project.click.click.getPlatform

object ApiConfig {
    // Change this to match your environment
    private const val USE_LOCAL_SERVER = true

    // Your Mac's local IP (find with: ifconfig | grep "inet " | grep -v 127.0.0.1)
    // Or use 127.0.0.1 for Android emulator
    private const val LOCAL_IP = "10.19.165.221"
    private const val LOCAL_PORT = 5000

    private const val PRODUCTION_URL = "https://your-production-api.com"

    /**
     * Base URL for the Flask API
     * 
     * IMPORTANT: 
     * - Android Emulator: use 10.0.2.2
     * - iOS Simulator: use 127.0.0.1 or Local IP
     * - Physical Device: use your machine's network IP
     */


    /**
     * Base URL for the Flask API
     */
    val BASE_URL: String
        get() = if (USE_LOCAL_SERVER) {
            val isAndroid = getPlatform().name.contains("Android", ignoreCase = true)
            val host = if (isAndroid) "10.0.2.2" else LOCAL_IP 
            "http://$host:$LOCAL_PORT"
        } else {
            PRODUCTION_URL
        }

    /**
     * Get the appropriate base URL for the current platform
     */
    fun getBaseUrlForPlatform(isAndroidEmulator: Boolean = false): String {
        return if (USE_LOCAL_SERVER) {
            if (isAndroidEmulator) {
                "http://10.0.2.2:$LOCAL_PORT" // Android emulator special alias
            } else {
                "http://$LOCAL_IP:$LOCAL_PORT" // iOS simulator or physical device
            }
        } else {
            PRODUCTION_URL
        }
    }

    /**
     * Supabase configuration (for Realtime only)
     */
    const val SUPABASE_REALTIME_ENABLED = true
}

