package compose.project.click.click.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import compose.project.click.click.ui.chat.rememberChatMediaPickers
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch

/**
 * Phase 2 — C8 avatar step. Sits between Interests and Complete in the new onboarding
 * state machine. Users can pick a photo (gallery or camera), preview it locally, and
 * upload — or tap "Skip for now" which flips `avatarSetOrSkipped = true` without a
 * network round-trip. Upload failures surface inline; on success [onUploaded] is called
 * with the public URL so the caller can call `AppDataManager.applyProfilePictureUrl`.
 */
@Composable
fun AvatarScreen(
    existingAvatarUrl: String?,
    onUploadBytes: suspend (ByteArray, String) -> Result<String>,
    onUploaded: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var selectedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedMime by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val mediaPickers = rememberChatMediaPickers(
        onImagePicked = { bytes, mime ->
            errorMessage = null
            if (bytes.size > 2_000_000) {
                errorMessage = "Image must be under 2 MB"
                return@rememberChatMediaPickers
            }
            selectedBytes = bytes
            selectedMime = mime
        },
        onAudioPicked = { _, _, _ -> },
        onMediaAccessBlocked = { msg -> errorMessage = msg },
    )

    fun performUpload() {
        val bytes = selectedBytes ?: return
        val mime = selectedMime ?: "image/jpeg"
        scope.launch {
            uploading = true
            errorMessage = null
            try {
                onUploadBytes(bytes, mime).fold(
                    onSuccess = { url ->
                        onUploaded(url)
                    },
                    onFailure = { err ->
                        errorMessage = err.message?.lines()?.firstOrNull()?.take(180)
                            ?: "Could not upload photo. Try again."
                    },
                )
            } finally {
                uploading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PrimaryBlue.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = topInset + 28.dp,
                    bottom = 32.dp,
                    start = 24.dp,
                    end = 24.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Add a photo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A real face goes a long way — but you can skip and add it later from Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(40.dp))

            AvatarPreview(
                bytes = selectedBytes,
                existingUrl = existingAvatarUrl,
            )

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarSourceButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Image,
                    label = "From library",
                    onClick = { mediaPickers.openPhotoLibrary() },
                    enabled = !uploading,
                )
                AvatarSourceButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.PhotoCamera,
                    label = "Take photo",
                    onClick = { mediaPickers.openCamera() },
                    enabled = !uploading,
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(36.dp))

            Button(
                onClick = ::performUpload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                enabled = selectedBytes != null && !uploading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = Color.White,
                    disabledContainerColor = PrimaryBlue.copy(alpha = 0.32f),
                    disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
            ) {
                if (uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Uploading…", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                } else {
                    Text(
                        text = if (selectedBytes != null) "Use this photo" else "Choose a photo",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uploading,
            ) {
                Text(
                    text = "Skip for now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AvatarPreview(bytes: ByteArray?, existingUrl: String?) {
    Box(
        modifier = Modifier
            .size(168.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        PrimaryBlue.copy(alpha = 0.18f),
                        LightBlue.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(width = 2.dp, color = PrimaryBlue.copy(alpha = 0.35f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bytes != null -> {
                AsyncImage(
                    model = bytes,
                    contentDescription = "Selected avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(168.dp)
                        .clip(CircleShape),
                )
            }
            !existingUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = existingUrl,
                    contentDescription = "Current avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(168.dp)
                        .clip(CircleShape),
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = PrimaryBlue.copy(alpha = 0.85f),
                    modifier = Modifier.size(88.dp),
                )
            }
        }
    }
}

@Composable
private fun AvatarSourceButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBlue,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
