@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.collaboration.sharedDropDevelopHours
import compose.project.click.click.core.FeatureFlags
import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropAudience
import compose.project.click.click.collaboration.graphemeCount
import compose.project.click.click.collaboration.sharedDropCaptionFits
import compose.project.click.click.ui.camera.DisposableCameraView

/*
 * Sharing a drop (sheet 07 G2-3, G2-5): the Click Drop camera, then the photo with its Locket-style
 * caption (≤100 characters as people count them), the audience (All / Core) and the not-E2EE note.
 */

internal fun sharedDropDisclosure(developHours: Int = sharedDropDevelopHours()): String {
    val time = if (developHours == 1) "an hour" else "$developHours hours"
    return "It develops for them in $time, like a story. Shared drops aren't end-to-end encrypted like chats: " +
        "only the people you pick can see them, and you can delete it anytime."
}

/** Camera → compose → share. [onShare] gets the photo, audience and caption. */
@Composable
fun SharedDropComposer(
    onShare: (ByteArray, SharedDropAudience, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var captured by remember { mutableStateOf<ByteArray?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val photo = captured
        if (photo == null) {
            DisposableCameraView(
                onPhotoConfirmed = { captured = it },
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SharedDropAudienceSheet(
                jpeg = photo,
                onShare = { audience, caption -> onShare(photo, audience, caption) },
                onCancel = onDismiss,
            )
        }
    }
}

@Composable
private fun SharedDropAudienceSheet(
    jpeg: ByteArray,
    onShare: (SharedDropAudience, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var audience by remember { mutableStateOf(SharedDropAudience.ALL) }
    var caption by remember { mutableStateOf("") }
    val flags by FeatureFlags.state.collectAsState()
    val photo = rememberDecodedBitmap(jpeg, 1200)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("Share drop", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Button(onClick = { onShare(audience, caption.trim().takeIf { it.isNotEmpty() }) }) { Text("Share") }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
            photo?.let { Image(it, "Your drop", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            val left = SharedDrop.CAPTION_LIMIT - graphemeCount(caption)
            if (left < 20) {
                Text("$left", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            TextField(
                value = caption,
                // One line, capped at the limit as people count characters.
                onValueChange = { next ->
                    val oneLine = next.replace("\n", "")
                    if (sharedDropCaptionFits(oneLine)) caption = oneLine
                },
                placeholder = { Text("Add a caption", color = Color.White.copy(alpha = 0.7f)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(50),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.45f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.45f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp),
            )
        }
        Text("Share with", style = MaterialTheme.typography.titleSmall)
        listOf(SharedDropAudience.ALL to "All connections", SharedDropAudience.CORE to "Core connections").forEach { (value, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = audience == value, role = Role.RadioButton) { audience = value }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = audience == value, onClick = null)
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(sharedDropDisclosure(sharedDropDevelopHours(flags)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
