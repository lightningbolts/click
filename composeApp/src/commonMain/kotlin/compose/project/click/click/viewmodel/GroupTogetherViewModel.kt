package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.GroupHangout // pragma: allowlist secret
import compose.project.click.click.data.models.UpcomingPlan // pragma: allowlist secret
import compose.project.click.click.data.models.toFriendshipEncounter // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock

/**
 * Group "Together" (iOS `GroupSpaceModel.loadHangouts`): your encounters with each member,
 * clustered so two or more members within two hours count as one group hangout, plus the
 * group's upcoming plans.
 */
class GroupTogetherViewModel(
    private val chatId: String,
    private val repository: SupabaseRepository = SupabaseRepository(),
    private val chatRepository: ChatRepository = SupabaseChatRepository(createTokenStorage()),
) : ViewModel() {
    private val _hangouts = MutableStateFlow<List<GroupHangout>>(emptyList())
    val hangouts: StateFlow<List<GroupHangout>> = _hangouts.asStateFlow()

    private val _upcomingPlans = MutableStateFlow<List<UpcomingPlan>>(emptyList())
    val upcomingPlans: StateFlow<List<UpcomingPlan>> = _upcomingPlans.asStateFlow()

    private var loadedFor: Set<String>? = null

    fun load(
        viewerUserId: String,
        memberIds: List<String>,
    ) {
        val members = memberIds.filter { it.isNotBlank() && it != viewerUserId }.toSet()
        if (members == loadedFor) return
        loadedFor = members
        viewModelScope.launch {
            val limiter = Semaphore(4)
            val tagged =
                members
                    .map { member ->
                        async {
                            limiter.withPermit {
                                val connection =
                                    runCatching { repository.fetchSharedConnectionBetween(viewerUserId, member) }.getOrNull()
                                connection
                                    ?.connectionEncounters
                                    .orEmpty()
                                    .mapNotNull { it.toFriendshipEncounter() }
                                    .map { GroupHangout.Tagged(member, it) }
                            }
                        }
                    }.awaitAll()
                    .flatten()
            _hangouts.value = GroupHangout.clusters(tagged)
        }
        viewModelScope.launch {
            _upcomingPlans.value = chatRepository.fetchUpcomingPlans(chatId, Clock.System.now().toEpochMilliseconds()).take(3)
        }
    }
}
