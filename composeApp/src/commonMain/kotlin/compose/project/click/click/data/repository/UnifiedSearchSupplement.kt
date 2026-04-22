package compose.project.click.click.data.repository

import compose.project.click.click.data.models.AvailabilityIntentRow

/**
 * Cross-table graph search payload from [user_interests] and [availability_intents]
 * for mutual peers. Substring filtering on tags / intent text is applied by callers.
 */
data class UnifiedSearchSupplement(
    /** Peer user id → full interest tag list (bounded select). */
    val peerInterestTagsByUserId: Map<String, List<String>>,
    /** Peer user id → non-expired intent rows (bounded select). */
    val activePeerIntentsByUserId: Map<String, List<AvailabilityIntentRow>>,
) {
    companion object {
        val EMPTY = UnifiedSearchSupplement(emptyMap(), emptyMap())
    }
}
