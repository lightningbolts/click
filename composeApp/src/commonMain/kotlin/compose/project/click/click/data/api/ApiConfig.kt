package compose.project.click.click.data.api

import compose.project.click.click.getPlatform

/**
 * API Configuration for different environments
 */
object ApiConfig {
    // Change this to match your environment
    private const val USE_LOCAL_SERVER = true

    // Your Mac's local IP (find with: ifconfig | grep "inet " | grep -v 127.0.0.1)
    private const val LOCAL_IP = "10.19.165.221"
    private const val LOCAL_PORT = 5000

    private const val PRODUCTION_URL = "https://your-production-api.com"

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
                "http://10.0.2.2:$LOCAL_PORT"
            } else {
                "http://$LOCAL_IP:$LOCAL_PORT"
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
