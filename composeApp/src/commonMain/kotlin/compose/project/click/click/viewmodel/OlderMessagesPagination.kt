package compose.project.click.click.viewmodel

/**
 * Pagination end-of-history decision for [ChatViewModel.loadOlderMessages].
 *
 * Stale JWTs make PostgREST return `[]` without throwing — that must NOT clear
 * `hasMoreOlderMessages` until we have successfully refreshed auth and still got empty.
 */
enum class OlderMessagesPageOutcome {
    /** Keep scrolling / retry later (null fetch or empty before a successful auth retry). */
    KeepHasMore,

    /** True end of history (empty page after auth was ready). */
    EndOfHistory,

    /** Non-empty page — merge into the timeline. */
    MergePage,
}

fun olderMessagesPageOutcome(
    fetched: List<*>?,
    authReadyAfterRetry: Boolean,
): OlderMessagesPageOutcome {
    if (fetched == null) return OlderMessagesPageOutcome.KeepHasMore
    if (fetched.isEmpty()) {
        return if (authReadyAfterRetry) {
            OlderMessagesPageOutcome.EndOfHistory
        } else {
            OlderMessagesPageOutcome.KeepHasMore
        }
    }
    return OlderMessagesPageOutcome.MergePage
}
