package compose.project.click.click.data

import io.ktor.client.engine.HttpClientEngine

/** Platform Ktor engine with WebSocket support (OkHttp on Android). */
internal expect fun createSupabaseHttpEngine(): HttpClientEngine
