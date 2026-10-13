package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpMethod
import kotlinx.io.IOException

/**
 * Transport hardening for the click-web clients (sheet 11 B6, ledger F52; iOS
 * `ClickAPIClient.makeSession` + `sendRetryingOnce`):
 * - every request is bounded (15 s, connecting 10 s), so a dead network fails fast and the UI can
 *   show its cached data and an offline state instead of hanging;
 * - a GET or HEAD that hits a transport error or a 502/503/504 is retried **once** after a short
 *   delay. POST, PUT, PATCH and DELETE are never retried: sends, checkouts and scans must not
 *   happen twice.
 */
object ClickWebTransport {
    const val REQUEST_TIMEOUT_MS = 15_000L
    const val CONNECT_TIMEOUT_MS = 10_000L

    /** Media uploads and downloads: one large body can't finish in 15 s on a slow network. */
    const val LONG_TRANSFER_TIMEOUT_MS = 120_000L
    const val RETRY_DELAY_MS = 500L
    const val MAX_RETRIES = 1

    private val retryableStatuses = setOf(502, 503, 504)

    fun isIdempotentRead(method: HttpMethod): Boolean = method == HttpMethod.Get || method == HttpMethod.Head

    fun isRetryableStatus(status: Int): Boolean = status in retryableStatuses
}

/** Installs [ClickWebTransport]'s timeouts and its one GET/HEAD retry on this client. */
internal fun HttpClientConfig<*>.installClickWebTransportPolicy(retryDelayMs: Long = ClickWebTransport.RETRY_DELAY_MS) {
    install(HttpTimeout) {
        requestTimeoutMillis = ClickWebTransport.REQUEST_TIMEOUT_MS
        connectTimeoutMillis = ClickWebTransport.CONNECT_TIMEOUT_MS
        socketTimeoutMillis = ClickWebTransport.REQUEST_TIMEOUT_MS
    }
    install(HttpRequestRetry) {
        maxRetries = ClickWebTransport.MAX_RETRIES
        retryIf { request, response ->
            ClickWebTransport.isIdempotentRead(request.method) && ClickWebTransport.isRetryableStatus(response.status.value)
        }
        // Timeouts are IOExceptions too; a cancellation never is, so it is never retried.
        retryOnExceptionIf { request, cause ->
            ClickWebTransport.isIdempotentRead(request.method) && cause is IOException
        }
        constantDelay(millis = retryDelayMs, randomizationMs = 0, respectRetryAfterHeader = false)
    }
}

/** A request that carries or fetches media: lifts the 15 s bound for this call only. */
internal fun HttpRequestBuilder.longTransfer() {
    timeout {
        requestTimeoutMillis = ClickWebTransport.LONG_TRANSFER_TIMEOUT_MS
        socketTimeoutMillis = ClickWebTransport.LONG_TRANSFER_TIMEOUT_MS
    }
}
