package compose.project.click.click.telemetry

import compose.project.click.click.appVersionLabel // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

expect fun platformEventsTelemetryStorage(): EventsTelemetryStorage

/**
 * App-wide [EventsTelemetrySink]. Fire-and-forget: telemetry never blocks or errors the app.
 *
 * Call sites follow click-ios `Click/App/RootGateView.swift`: a resolved signed-in user records
 * the install and today's open; returning to the foreground records today's open; going to the
 * background flushes.
 */
object ProductTelemetry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api by lazy { ApiClient() }

    internal val sink by lazy {
        EventsTelemetrySink(
            storage = platformEventsTelemetryStorage(),
            post = { body ->
                api.clickWebClient
                    .post("${ApiClient.clickWebAuthOrigin}${EventsTelemetrySink.PATH}") {
                        setBody(TextContent(body.toWireJson(), ContentType.Application.Json))
                    }.status.value
            },
            appVersion = { appVersionNameOf(appVersionLabel()) },
        )
    }

    /** The signed-in user is known (sign-in, or the first load after launch). */
    fun onSignedIn() =
        launchSafely {
            sink.installedIfNeeded()
            sink.appOpened()
            sink.flush()
        }

    /** Foregrounded while signed in. */
    fun onForegroundSignedIn() =
        launchSafely {
            sink.appOpened()
            sink.flush()
        }

    /** Backgrounded: send what is queued while the process is still alive. */
    fun onBackground() = launchSafely { sink.flush() }

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch { runCatching { block() } }
    }
}

/** "1.2 (8)" → "1.2": the route takes the marketing version, like iOS `CFBundleShortVersionString`. */
internal fun appVersionNameOf(label: String): String? = label.substringBefore(" (").trim().takeIf { it.isNotEmpty() }
