package compose.project.click.click.ui.chat // pragma: allowlist secret

import compose.project.click.click.data.api.KlipyClient // pragma: allowlist secret

/**
 * KLIPY picker grid state (iOS `GifPickerSheet` in click-ios `Click/Features/Chat/GifViews.swift`).
 * A blank query is trending; results keep KLIPY's order.
 */
internal data class GifPickerState(
    val query: String = "",
    val items: List<KlipyClient.Item> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** The page to request next: 1 on a fresh search, else the one after [page]. */
    fun nextPage(reset: Boolean): Int = if (reset) 1 else page + 1

    fun canLoadMore(): Boolean = hasNext && !isLoading && error == null

    fun loading(): GifPickerState = copy(isLoading = true)

    /**
     * Applies a loaded page for [requestedQuery]; a result for a stale query is dropped. Pages can
     * repeat an item, so only unseen ids are appended (grid identities stay unique).
     */
    fun loaded(
        requestedQuery: String,
        result: KlipyClient.Page,
        reset: Boolean,
    ): GifPickerState {
        if (requestedQuery != query) return this
        val known = HashSet<String>().apply { if (!reset) items.mapTo(this) { it.id } }
        val fresh = result.items.filter { known.add(it.id) }
        return copy(
            items = if (reset) fresh else items + fresh,
            page = result.page,
            hasNext = result.hasNext,
            isLoading = false,
            error = null,
        )
    }

    fun failed(
        requestedQuery: String,
        message: String,
        reset: Boolean,
    ): GifPickerState {
        if (requestedQuery != query) return this
        return copy(items = if (reset) emptyList() else items, isLoading = false, error = message)
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_PROMPT = "Search KLIPY"
        const val ATTRIBUTION = "Powered by KLIPY"
        const val LOAD_ERROR = "Couldn't load GIFs. Try again."
    }
}
