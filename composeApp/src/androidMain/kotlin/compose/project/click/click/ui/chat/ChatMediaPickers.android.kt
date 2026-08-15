@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.chat // pragma: allowlist secret

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import compose.project.click.click.calls.CallSessionManager
import compose.project.click.click.calls.CallState
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
actual fun rememberChatMediaPickers(
    onImagePicked: (ByteArray, String) -> Unit,
    onAudioPicked: (ByteArray, String, Long?) -> Unit,
    onFilePicked: (PickedFile) -> Unit,
    onMediaAccessBlocked: (String) -> Unit,
): ChatMediaPickerHandles {
    val launchFilePicker =
        rememberFilePicker(
            onFilePicked = onFilePicked,
            onFilePickFailed = { message -> onMediaAccessBlocked(message) },
        )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onImagePickedState by rememberUpdatedState(onImagePicked)
    val onAudioPickedState by rememberUpdatedState(onAudioPicked)
    val onMediaAccessBlockedState by rememberUpdatedState(onMediaAccessBlocked)

    var showVoiceDialog by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(10),
        ) { uris: List<Uri> ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                for (uri in uris) {
                    val read = readUriBytes(context, uri)
                    if (read == null) {
                        onMediaAccessBlockedState(
                            "Couldn't read that photo. If access was denied, enable Photos & videos permission for Click in Settings.",
                        )
                        continue
                    }
                    val (bytes, mime) = read
                    onImagePickedState(bytes, mime)
                }
            }
        }

    val takePictureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success: Boolean ->
            val file = pendingCameraFile
            pendingCameraFile = null
            if (file == null) return@rememberLauncherForActivityResult
            if (!success) {
                file.delete()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                try {
                    val bytes =
                        try {
                            withContext(Dispatchers.IO) { file.readBytes() }
                        } catch (_: IOException) {
                            null
                        }
                    withContext(Dispatchers.Main.immediate) {
                        if (bytes != null && bytes.isNotEmpty()) {
                            onImagePickedState(bytes, "image/jpeg")
                        } else {
                            onMediaAccessBlockedState("Couldn't read that photo. Please try again.")
                        }
                    }
                } finally {
                    file.delete()
                }
            }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                val file = File(context.cacheDir, "chat_camera_${System.currentTimeMillis()}.jpg")
                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                pendingCameraFile = file
                takePictureLauncher.launch(uri)
            } else {
                onMediaAccessBlockedState(
                    "Camera permission is off. To take photos in chat, enable Camera for Click in Settings.",
                )
            }
        }

    val recordPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                showVoiceDialog = true
            } else {
                onMediaAccessBlockedState(
                    "Microphone permission is off. To send voice clips, enable Microphone for Click in Settings.",
                )
            }
        }

    fun openCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                val file = File(context.cacheDir, "chat_camera_${System.currentTimeMillis()}.jpg")
                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                pendingCameraFile = file
                takePictureLauncher.launch(uri)
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openVoiceRecorder() {
        val activeCall = CallSessionManager.callState.value
        if (activeCall is CallState.Connecting || activeCall is CallState.Connected) {
            onMediaAccessBlockedState(
                "Microphone is in use for a call. End the call before recording a voice message.",
            )
            return
        }
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> {
                showVoiceDialog = true
            }
            else -> recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (showVoiceDialog) {
        VoiceRecordDialog(
            onDismiss = { showVoiceDialog = false },
            onFinished = { bytes, durationSec ->
                showVoiceDialog = false
                onAudioPickedState(bytes, "audio/mp4", durationSec)
            },
            onRecordBlocked = { message ->
                onMediaAccessBlockedState(message)
            },
        )
    }

    return ChatMediaPickerHandles(
        openPhotoLibrary = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        openCamera = { openCamera() },
        openVoiceRecorder = { openVoiceRecorder() },
        openFilePicker = { launchFilePicker() },
    )
}

@Composable
private fun VoiceRecordDialog(
    onDismiss: () -> Unit,
    onFinished: (ByteArray, Long?) -> Unit,
    onRecordBlocked: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(VoiceRecordUiPhase.Idle) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    val outputFile =
        remember {
            File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var recordedDurationSec by remember { mutableLongStateOf(0L) }
    var recordStartMs by remember { mutableLongStateOf(0L) }
    var recordError by remember { mutableStateOf<String?>(null) }

    /** Clear [recorder] first so metering stops, then stop/release the detached instance. */
    fun detachAndReleaseRecorder() {
        val mr = recorder
        recorder = null
        mr?.safeStopAndRelease()
    }

    DisposableEffect(Unit) {
        onDispose { detachAndReleaseRecorder() }
    }

    var waveformSamples by remember { mutableStateOf(List(40) { 0.06f }) }

    LaunchedEffect(phase) {
        if (phase != VoiceRecordUiPhase.Recording) return@LaunchedEffect
        while (isActive) {
            delay(250)
            elapsedSec = kotlin.math.max(0L, (System.currentTimeMillis() - recordStartMs) / 1000L)
        }
    }

    LaunchedEffect(phase, recorder) {
        val r = recorder
        if (phase != VoiceRecordUiPhase.Recording || r == null) return@LaunchedEffect
        while (isActive) {
            delay(50)
            // Released MediaRecorder throws IllegalStateException from getMaxAmplitude.
            val amp =
                runCatching { r.maxAmplitude }
                    .getOrElse { return@LaunchedEffect }
                    .coerceAtLeast(0)
                    .toFloat() / 32768f
            val v = (amp * 0.88f + 0.12f).coerceIn(0.08f, 1f)
            waveformSamples = waveformSamples.drop(1) + v
        }
    }

    val displaySeconds =
        when (phase) {
            VoiceRecordUiPhase.Preview -> recordedDurationSec
            else -> elapsedSec
        }
    val previewUrl =
        if (phase == VoiceRecordUiPhase.Preview && outputFile.exists() && outputFile.length() > 0L) {
            outputFile.absolutePath
        } else {
            null
        }

    GlassAlertDialog(
        onDismissRequest = {
            phase = VoiceRecordUiPhase.Idle
            detachAndReleaseRecorder()
            recordError = null
            if (outputFile.exists()) outputFile.delete()
            onDismiss()
        },
        title = { },
        text = {
            Box(Modifier.fillMaxWidth()) {
                VoiceMessageRecordDialogLayout(
                    phase = phase,
                    displaySeconds = displaySeconds,
                    waveformSamples = waveformSamples,
                    previewLocalMediaUrl = previewUrl,
                    errorMessage = recordError,
                    onCancel = {
                        phase = VoiceRecordUiPhase.Idle
                        detachAndReleaseRecorder()
                        recordError = null
                        if (outputFile.exists()) outputFile.delete()
                        onDismiss()
                    },
                    onRecord = {
                        if (recorder != null) {
                            detachAndReleaseRecorder()
                        }
                        val activeCall = CallSessionManager.callState.value
                        if (activeCall is CallState.Connecting || activeCall is CallState.Connected) {
                            onRecordBlocked(
                                "Microphone is in use for a call. End the call before recording a voice message.",
                            )
                            return@VoiceMessageRecordDialogLayout
                        }
                        outputFile.parentFile?.mkdirs()
                        if (outputFile.exists()) outputFile.delete()
                        recordError = null
                        val started =
                            runCatching {
                                val mr = createMediaRecorder(context, outputFile)
                                try {
                                    mr.start()
                                    mr
                                } catch (e: Exception) {
                                    runCatching { mr.release() }
                                    throw e
                                }
                            }
                        started.fold(
                            onSuccess = { mr ->
                                recorder = mr
                                recordStartMs = System.currentTimeMillis()
                                elapsedSec = 0L
                                recordedDurationSec = 0L
                                waveformSamples = List(40) { 0.06f }
                                phase = VoiceRecordUiPhase.Recording
                            },
                            onFailure = {
                                recorder = null
                                phase = VoiceRecordUiPhase.Idle
                                if (outputFile.exists()) outputFile.delete()
                                recordError =
                                    "Couldn't start recording. Check that the microphone isn't in use and try again."
                            },
                        )
                    },
                    onStopRecording = {
                        recordedDurationSec =
                            kotlin.math.max(
                                0L,
                                (System.currentTimeMillis() - recordStartMs) / 1000L,
                            )
                        elapsedSec = recordedDurationSec
                        // Leave Recording before release so metering LaunchedEffect cancels first.
                        phase = VoiceRecordUiPhase.Preview
                        detachAndReleaseRecorder()
                        recordError = null
                    },
                    onReRecord = {
                        phase = VoiceRecordUiPhase.Idle
                        detachAndReleaseRecorder()
                        recordError = null
                        elapsedSec = 0L
                        recordedDurationSec = 0L
                        waveformSamples = List(40) { 0.06f }
                        if (outputFile.exists()) outputFile.delete()
                    },
                    onSend = {
                        val durationSec = recordedDurationSec
                        scope.launch {
                            val bytes =
                                withContext(Dispatchers.IO) {
                                    if (!outputFile.exists() || outputFile.length() == 0L) {
                                        null
                                    } else {
                                        outputFile.readBytes()
                                    }
                                }
                            if (bytes != null && bytes.isNotEmpty()) {
                                onFinished(bytes, durationSec)
                            }
                            if (outputFile.exists()) outputFile.delete()
                        }
                    },
                )
            }
        },
        confirmButton = null,
        dismissButton = null,
        showActionRow = false,
    )
}

private fun MediaRecorder.safeStopAndRelease() {
    runCatching {
        try {
            stop()
        } catch (_: Exception) {
        }
        release()
    }
}

@Suppress("DEPRECATION")
private fun createMediaRecorder(
    context: Context,
    file: File,
): MediaRecorder {
    val mr =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    try {
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setOutputFile(file.absolutePath)
        mr.prepare()
        return mr
    } catch (e: Exception) {
        runCatching { mr.release() }
        throw e
    }
}

private suspend fun readUriBytes(
    context: Context,
    uri: Uri,
): Pair<ByteArray, String>? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            bytes to mime
        }.getOrNull()
    }
}
