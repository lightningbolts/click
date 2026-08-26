package compose.project.click.click.data.api

import compose.project.click.click.qr.CLICK_WEB_BASE_URL as QrClickWebBaseUrl

/**
 * API configuration.
 *
 * All companion HTTP traffic goes through the Next.js app (`click-web`).
 * The legacy Flask server has been removed — do not reintroduce LAN/localhost API bases.
 */
object ApiConfig {
    /**
     * Supabase configuration (for Realtime only)
     */
    const val SUPABASE_REALTIME_ENABLED = true

    /**
     * Next.js companion (`click-web`) — profile QR, secure API tunnel, chat gatekeeper, etc.
     * Single source of truth lives in [compose.project.click.click.qr.CLICK_WEB_BASE_URL].
     */
    val CLICK_WEB_BASE_URL: String get() = QrClickWebBaseUrl
}
