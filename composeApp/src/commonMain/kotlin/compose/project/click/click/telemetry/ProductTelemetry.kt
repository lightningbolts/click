package compose.project.click.click.telemetry

import compose.project.click.click.appVersionLabel
import compose.project.click.click.data.api.ApiClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

expect fun platformEventsTelemetryStorage(): EventsTelemetryStorage

/** App-wide [EventsTelemetrySink]. Fire-and-forget: telemetry never blocks or errors the app. */
object ProductTelemetry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api by lazy { ApiClient() }

    internal val sink by lazy {
        EventsTelemetrySink(
            storage = platformEventsTelemetryStorage(),
            post = { body ->
                api.clickWebClient
                    .post("${ApiClient.clickWebAuthOrigin}${EventsTelemetrySink.PATH}") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.status.value
            },
            // "1.2 (8)" → "1.2"
            appVersion = { appVersionLabel().substringBefore(" (").trim() },
        )
    }

    /** Signed in and foregrounded: record the install and today's open, then send. */
    fun onSignedInAndActive() {
        scope.launch {
            runCatching {
                sink.installedIfNeeded()
                sink.appOpened()
                sink.flush()
            }
        }
    }

    fun recapOpened(access: String? = null) {
        scope.launch {
            runCatching {
                sink.recapOpened(access)
                sink.flush()
            }
        }
    }
}
