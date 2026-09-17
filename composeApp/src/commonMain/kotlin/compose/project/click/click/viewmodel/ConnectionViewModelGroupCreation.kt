package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Opens the existing verified-group creation flow without inventing a second picker for Add Click.
 * AppMainShell already collects [verifiedCliqueFromProximity], switches to Clicks, and forwards the
 * intent into ConnectionsListView; an empty selection means a normal user-initiated create flow.
 */
fun ConnectionViewModel.requestVerifiedGroupCreation() {
    viewModelScope.launch {
        _verifiedCliqueFromProximity.emit(
            VerifiedCliqueProximityIntent(
                preselectFriendIds = emptyList(),
                matchedUsers = emptyList(),
            ),
        )
    }
}
