package compose.project.click.click.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createSupabaseHttpEngine(): HttpClientEngine = OkHttp.create()
