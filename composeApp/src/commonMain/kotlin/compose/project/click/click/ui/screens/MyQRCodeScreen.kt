@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.platform.shareText // pragma: allowlist secret
import compose.project.click.click.qr.buildConnectionUniversalLink // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenWithFloatingHeader // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickScreenSpacing // pragma: allowlist secret
import compose.project.click.click.ui.components.HeaderBackIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.UserQrCode // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQRCodeScreen(
    userId: String,
    username: String?,
    locationService: LocationService? = null,
    onNavigateBack: () -> Unit,
) {
    val displayName = username?.trim()?.takeIf { it.isNotEmpty() } ?: "me"
    AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
        AppScreenWithFloatingHeader(
            title = "My QR Code",
            onNavigateBack = onNavigateBack,
            navigationIcon = {
                HeaderBackIconButton(onClick = onNavigateBack)
            },
        ) { contentModifier ->
            Column(
                modifier =
                    contentModifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                val user =
                    User(
                        id = userId,
                        name = username,
                        createdAt = 0L, // Dummy
                    )

                if (username != null) {
                    Text(
                        username,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(ClickScreenSpacing.Section))
                }

                UserQrCode(
                    user = user,
                    locationService = locationService,
                    size = 300.dp,
                    onShare = {
                        shareText(
                            text = buildConnectionUniversalLink(userId),
                            subject = "Connect with $displayName on Click",
                        )
                    },
                )
            }
        }
    }
}
