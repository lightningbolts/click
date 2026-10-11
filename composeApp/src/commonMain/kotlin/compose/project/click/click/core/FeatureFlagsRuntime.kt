package compose.project.click.click.core

import compose.project.click.click.data.api.ApiClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

expect fun platformFeatureFlagsPersistence(): FeatureFlagsPersistence

/** App-wide flag store. [FeatureFlagGate] reads it once [install] runs at launch. */
object FeatureFlags {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api by lazy { ApiClient() }

    internal val store =
        FeatureFlagsStore(
            persistence = ::platformFeatureFlagsPersistence,
            fetch = {
                val response = api.clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/me/features")
                if (response.status.value in 200..299) response.bodyAsText() else null
            },
        )

    val state: StateFlow<FeatureFlagsState> get() = store.state

    fun isEnabled(key: String): Boolean = store.isEnabled(key)

    /** Call once at launch, after storage is initialized: restores the last user's flags. */
    fun install() {
        FeatureFlagGate.isEnabled = store::isEnabled
        store.restoreAtLaunch()
    }

    /** Sign-in (or a profile load that resolved the user): switch to their flags and refresh. */
    fun onSignedIn(userId: String) {
        store.restore(userId)
        refreshInBackground()
    }

    fun refreshInBackground() {
        if (store.state.value.userId == null) return
        scope.launch { store.refresh() }
    }

    fun clear() = store.clear()
}
