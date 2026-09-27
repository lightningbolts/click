package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import compose.project.click.click.data.IdentityCache // pragma: allowlist secret
import compose.project.click.click.data.UserIdentity // pragma: allowlist secret

/**
 * Names for [userIds] from [IdentityCache] (batched `POST /api/users/display-names`), for people
 * the viewer isn't connected to. Callers try their own user list first and fall back to this.
 */
@Composable
fun rememberIdentityNames(userIds: Collection<String>): Map<String, UserIdentity> {
    val key = userIds.filter { it.isNotBlank() }.distinct().sorted()
    LaunchedEffect(key) {
        if (key.isNotEmpty()) IdentityCache.request(key)
    }
    val identities by IdentityCache.identities.collectAsState()
    return identities
}
