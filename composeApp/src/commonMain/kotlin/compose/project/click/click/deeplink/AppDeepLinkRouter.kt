package compose.project.click.click.deeplink // pragma: allowlist secret

import compose.project.click.click.notifications.ChatDeepLinkManager // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app destinations reachable from a URL (iOS `AppRouter.parseIncomingURL`). */
sealed interface AppDeepLink {
    data class Chat(
        val chatId: String,
        val messageId: String? = null,
    ) : AppDeepLink

    data class Profile(
        val userId: String,
    ) : AppDeepLink

    data object MyQr : AppDeepLink

    data object Scan : AppDeepLink

    data object TapConnect : AppDeepLink

    data class Search(
        val query: String,
    ) : AppDeepLink

    /** The Clicks list; only reached from push taps, never parsed from a URL. */
    data object Clicks : AppDeepLink
}

/** Minimal URL split; enough for `click://` and joinclick.co links without platform URI types. */
internal data class ParsedLink(
    val scheme: String,
    val host: String,
    val segments: List<String>,
    val query: Map<String, String>,
)

internal fun parseLink(url: String): ParsedLink? {
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = trimmed.substring(0, schemeEnd).lowercase()
    val rest = trimmed.substring(schemeEnd + 3).substringBefore('#')
    val beforeQuery = rest.substringBefore('?')
    val queryString = if ('?' in rest) rest.substringAfter('?') else ""
    val host = beforeQuery.substringBefore('/').substringBefore(':').lowercase()
    val segments =
        beforeQuery
            .substringAfter('/', "")
            .split('/')
            .filter { it.isNotEmpty() }
            .map(::percentDecode)
    val query =
        queryString
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val key = percentDecode(pair.substringBefore('='))
                val value = percentDecode(pair.substringAfter('=', "").replace('+', ' '))
                key to value
            }
    return ParsedLink(scheme, host, segments, query)
}

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes += hex.toByte()
                i += 3
                continue
            }
        }
        c.toString().encodeToByteArray().forEach { bytes += it }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

private val WEB_HOSTS = setOf("joinclick.co", "www.joinclick.co", "click-us.vercel.app")

/**
 * Routes the URLs iOS handles beyond `/c`, `/e` and `/hub` (which keep their own routers):
 * `click://chat/{chatId}?m=`, `click://profile/{userId}` (alias `u`), `click://myqr`,
 * `click://scan`, `click://tap`, `click://search?q=` and `https://joinclick.co/search?q=`.
 */
object AppDeepLinkRouter {
    private val _pending = MutableStateFlow<AppDeepLink?>(null)

    /** Destinations the app shell opens itself (QR, scanner, tap, search). */
    val pending: StateFlow<AppDeepLink?> = _pending.asStateFlow()

    fun parse(url: String): AppDeepLink? {
        val link = parseLink(url) ?: return null
        val first = link.segments.firstOrNull()
        return when (link.scheme) {
            "click" ->
                when (link.host) {
                    "chat" -> first?.let { AppDeepLink.Chat(it, link.query["m"] ?: link.query["message"]) }
                    "profile", "u" -> first?.let { AppDeepLink.Profile(it) }
                    "myqr" -> AppDeepLink.MyQr
                    "scan" -> AppDeepLink.Scan
                    "tap" -> AppDeepLink.TapConnect
                    "search" -> AppDeepLink.Search(link.query["q"].orEmpty().trim())
                    else -> null
                }
            "https", "http" ->
                if (link.host in WEB_HOSTS && first == "search") {
                    AppDeepLink.Search(link.query["q"].orEmpty().trim())
                } else {
                    null
                }
            else -> null
        }?.takeIf { it.isValid() }
    }

    private fun AppDeepLink.isValid(): Boolean =
        when (this) {
            is AppDeepLink.Chat -> chatId.isNotBlank()
            is AppDeepLink.Profile -> userId.isNotBlank()
            else -> true
        }

    /** Returns true when [url] was one of these routes. */
    fun handleIncomingUrl(url: String): Boolean {
        val link = parse(url) ?: return false
        open(link)
        return true
    }

    /** Also used by in-app shortcuts (Me → My QR) so they share the deep-link path. */
    fun open(link: AppDeepLink) {
        when (link) {
            is AppDeepLink.Chat -> {
                ChatDeepLinkManager.setPendingChat(link.chatId)
                link.messageId?.let { ChatDeepLinkManager.setPendingTargetMessage(it) }
            }
            is AppDeepLink.Profile -> ChatDeepLinkManager.setPendingProfile(link.userId)
            else -> _pending.value = link
        }
    }

    fun consume(): AppDeepLink? {
        val value = _pending.value
        _pending.value = null
        return value
    }

    private var searchQuery: String? = null

    /** The shell stores a `search?q=` query here right before opening the search sheet. */
    fun setSearchQuery(query: String) {
        searchQuery = query.trim().takeIf { it.isNotEmpty() }
    }

    fun takeSearchQuery(): String? {
        val value = searchQuery
        searchQuery = null
        return value
    }
}
