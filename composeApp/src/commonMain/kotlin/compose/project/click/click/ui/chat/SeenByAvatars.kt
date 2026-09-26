@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberIdentityNames // pragma: allowlist secret

private const val SEEN_BY_MAX_FACES = 5

/** Small stacked faces under the newest message each group member has read (iOS `SeenByAvatars`). */
@Composable
internal fun SeenByAvatars(
    readers: List<User>,
    alignEnd: Boolean,
) {
    val identities = rememberIdentityNames(readers.filter { it.name.isNullOrBlank() }.map { it.id })
    val names = readers.map { (it.name?.ifBlank { null } ?: identities[it.id]?.name)?.substringBefore(' ')?.ifBlank { null } ?: "Someone" }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp)
                .semantics { contentDescription = "Seen by ${names.joinToString(", ")}" },
        horizontalArrangement = Arrangement.spacedBy(2.dp, if (alignEnd) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        readers.take(SEEN_BY_MAX_FACES).forEach { user ->
            ConnectionListUserAvatarFace(
                displayName = user.name,
                email = user.email,
                avatarUrl = user.image,
                userId = user.id,
                modifier = Modifier.size(16.dp).clip(CircleShape),
                useCompactTypography = true,
            )
        }
        val extra = readers.size - SEEN_BY_MAX_FACES
        if (extra > 0) {
            Text("+$extra", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
