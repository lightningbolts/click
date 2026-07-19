package compose.project.click.click.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun createSupabaseHttpEngine(): HttpClientEngine = Darwin.create()
