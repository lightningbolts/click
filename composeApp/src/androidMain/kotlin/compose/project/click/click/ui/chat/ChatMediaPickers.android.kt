package compose.project.click.click.ui.chat

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import compose.project.click.click.ui.chat.VoiceMessageRecordDialogLayout

@Composable
actual fun rememberChatMediaPickers(
    onImagePicked: (ByteArray, String) -> Unit,
    onAudioPicked: (ByteArray, String, Long?) -> Unit,
): ChatMediaPickerHandles {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showVoiceDialog by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, mime) = readUriBytes(context, uri) ?: return@launch
            onImagePicked(bytes, mime)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
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
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            file.delete()
            onImagePicked(bytes, "image/jpeg")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "chat_camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            pendingCameraFile = file
            takePictureLauncher.launch(uri)
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) showVoiceDialog = true
    }

    fun openCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                val file = File(context.cacheDir, "chat_camera_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
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
                onAudioPicked(bytes, "audio/mp4", durationSec)
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
    )
}

@Composable
private fun VoiceRecordDialog(
    onDismiss: () -> Unit,
    onFinished: (ByteArray, Long?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    val outputFile = remember {
        File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
    }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var recordStartMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                recorder?.apply {
                    try {
                        stop()
                    } catch (_: Exception) {
                    }
                    release()
                }
            }
            recorder = null
        }
    }

    var waveformSamples by remember { mutableStateOf(List(40) { 0.06f }) }

    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        while (isActive) {
            delay(250)
            elapsedSec = kotlin.math.max(0L, (System.currentTimeMillis() - recordStartMs) / 1000L)
        }
    }

    LaunchedEffect(isRecording, recorder) {
        val r = recorder
        if (!isRecording || r == null) return@LaunchedEffect
        while (isActive) {
            delay(50)
            val amp = r.maxAmplitude.coerceAtLeast(0).toFloat() / 32768f
            val v = (amp * 0.88f + 0.12f).coerceIn(0.08f, 1f)
            waveformSamples = waveformSamples.drop(1) + v
        }
    }

    AlertDialog(
        onDismissRequest = {
            runCatching {
                recorder?.apply {
                    try {
                        stop()
                    } catch (_: Exception) {
                    }
                    release()
                }
            }
            recorder = null
            isRecording = false
            if (outputFile.exists()) outputFile.delete()
            onDismiss()
        },
        title = { },
        text = {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
            VoiceMessageRecordDialogLayout(
                isRecording = isRecording,
                elapsedSec = elapsedSec,
                waveformSamples = waveformSamples,
                onCancel = {
                    runCatching {
                        recorder?.apply {
                            try {
                                stop()
                            } catch (_: Exception) {
                            }
                            release()
                        }
                    }
                    recorder = null
                    isRecording = false
                    if (outputFile.exists()) outputFile.delete()
                    onDismiss()
                },
                onRecord = {
                    outputFile.parentFile?.mkdirs()
                    if (outputFile.exists()) outputFile.delete()
                    val mr = createMediaRecorder(context, outputFile)
                    mr.start()
                    recorder = mr
                    recordStartMs = System.currentTimeMillis()
                    elapsedSec = 0L
                    waveformSamples = List(40) { 0.06f }
                    isRecording = true
                },
                onStopSend = {
                    runCatching {
                        recorder?.apply {
                            stop()
                            release()
                        }
                    }
                    recorder = null
                    isRecording = false
                    val durationSec = (System.currentTimeMillis() - recordStartMs) / 1000L
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) {
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
        confirmButton = {},
        dismissButton = {},
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(8.dp),
    )
}

@Suppress("DEPRECATION")
private fun createMediaRecorder(context: Context, file: File): MediaRecorder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
        }
    } else {
        MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
        }
    }
}

private suspend fun readUriBytes(context: Context, uri: Uri): Pair<ByteArray, String>? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            bytes to mime
        }.getOrNull()
    }
}
