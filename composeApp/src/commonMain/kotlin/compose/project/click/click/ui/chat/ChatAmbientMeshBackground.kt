package compose.project.click.click.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import compose.project.click.click.data.models.Connection

/** Test tag for Compose UI tests (visibility / bounds). */
internal const val ChatAmbientMeshTestTag = "chat_ambient_mesh_layer"

/** Test tag for the chrome plate behind the connection chat header. */
internal const val ChatGlassHeaderPlateTestTag = "chat_glass_header_plate"

/** Test tag for the chrome plate behind the message composer. */
internal const val ChatGlassComposerPlateTestTag = "chat_glass_composer_plate"

/**
 * Flat Functional Clarity chat background — solid theme background, no mesh gradients.
 * [connection] / [isHubNeutral] / [animateMesh] retained for call-site compatibility.
 */
@Composable
fun ChatAmbientMeshBackground(
    connection: Connection?,
    isHubNeutral: Boolean,
    modifier: Modifier = Modifier,
    animateMesh: Boolean = false,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredConnection = connection
    @Suppress("UNUSED_VARIABLE")
    val ignoredHub = isHubNeutral
    @Suppress("UNUSED_VARIABLE")
    val ignoredAnimate = animateMesh

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ChatAmbientMeshTestTag)
            .background(MaterialTheme.colorScheme.background),
    )
}

/**
 * Solid tint behind outbound bubbles — no mesh.
 */
@Composable
internal fun ChatBubbleSentMeshTint(
    connection: Connection?,
    isHubNeutral: Boolean,
    modifier: Modifier = Modifier,
) {
    ChatAmbientMeshBackground(
        connection = connection,
        isHubNeutral = isHubNeutral,
        modifier = modifier,
        animateMesh = false,
    )
}
