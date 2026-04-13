@file:OptIn(kotlin.time.ExperimentalTime::class)

package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import platform.AVFAudio.AVAudioQualityMedium
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.*
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * iOS: PHPicker, UIImagePickerController (camera), and AVAudioRecorder-backed voice dialog.
 * Simulator has no camera; mic and photo library behavior differ from device — test on hardware when possible.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberChatMediaPickers(
    onImagePicked: (ByteArray, String) -> Unit,
    onAudioPicked: (ByteArray, String, Long?) -> Unit,
    onMediaAccessBlocked: (String) -> Unit,
): ChatMediaPickerHandles {
    val viewController = LocalUIViewController.current
    val onImagePickedState by rememberUpdatedState(onImagePicked)
    val onAudioPickedState by rememberUpdatedState(onAudioPicked)
    val onMediaAccessBlockedState by rememberUpdatedState(onMediaAccessBlocked)

    var showVoiceDialog by remember { mutableStateOf(false) }

    val photoPickerDelegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                if (didFinishPicking.isEmpty()) return
                val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
                val provider = result.itemProvider
                provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
                    if (data == null) {
                        dispatch_async(dispatch_get_main_queue()) {
                            onMediaAccessBlockedState(
                                "Couldn't read that photo. If access was denied, enable Photos for Click in Settings.",
                            )
                        }
                        return@loadDataRepresentationForTypeIdentifier
                    }
                    val bytes = data.toByteArray()
                    dispatch_async(dispatch_get_main_queue()) {
                        if (bytes.isNotEmpty()) {
                            onImagePickedState(bytes, "image/jpeg")
                        } else {
                            onMediaAccessBlockedState(
                                "Couldn't read that photo. Enable Photos access for Click in Settings.",
                            )
                        }
                    }
                }
            }
        }
    }

    val cameraPickerDelegate = remember {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>,
            ) {
                picker.dismissViewControllerAnimated(true, completion = null)
                val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                    ?: return
                val jpeg = UIImageJPEGRepresentation(image, 0.92) ?: return
                val bytes = jpeg.toByteArray()
                if (bytes.isNotEmpty()) {
                    onImagePickedState(bytes, "image/jpeg")
                }
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }

    fun openPhotoLibrary() {
        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1L
        }
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = photoPickerDelegate
        viewController.presentViewController(picker, animated = true, completion = null)
    }

    fun openCameraInternal() {
        val cameraSource = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(cameraSource)) {
            onMediaAccessBlockedState("Camera is not available on this device.")
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = cameraSource
        picker.delegate = cameraPickerDelegate
        picker.allowsEditing = false
        viewController.presentViewController(picker, animated = true, completion = null)
    }

    fun openCamera() {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                onMediaAccessBlockedState(
                    "Camera permission is off. To take photos in chat, enable Camera for Click in Settings.",
                )
            }
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted: Boolean ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) {
                            openCameraInternal()
                        } else {
                            onMediaAccessBlockedState(
                                "Camera permission is off. To take photos in chat, enable Camera for Click in Settings.",
                            )
                        }
                    }
                }
            }
            else -> openCameraInternal()
        }
    }

    fun requestMicThenShowVoiceDialog() {
        val session = AVAudioSession.sharedInstance()
        when (session.recordPermission) {
            AVAudioSessionRecordPermissionGranted -> showVoiceDialog = true
            AVAudioSessionRecordPermissionDenied -> {
                onMediaAccessBlockedState(
                    "Microphone permission is off. To send voice clips, enable Microphone for Click in Settings.",
                )
            }
            else -> {
                session.requestRecordPermission { granted: Boolean ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) {
                            showVoiceDialog = true
                        } else {
                            onMediaAccessBlockedState(
                                "Microphone permission is off. To send voice clips, enable Microphone for Click in Settings.",
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVoiceDialog) {
        IosVoiceRecordDialog(
            onDismiss = { showVoiceDialog = false },
            onFinished = { bytes, durationSec ->
                showVoiceDialog = false
                onAudioPickedState(bytes, "audio/mp4", durationSec)
            },
        )
    }

    return ChatMediaPickerHandles(
        openPhotoLibrary = { openPhotoLibrary() },
        openCamera = { openCamera() },
        openVoiceRecorder = { requestMicThenShowVoiceDialog() },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    val base = bytes ?: return ByteArray(0)
    memcpy(out.refTo(0), base, len.toULong())
    return out
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosVoiceRecordDialog(
    onDismiss: () -> Unit,
    onFinished: (ByteArray, Long?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(VoiceRecordUiPhase.Idle) }
    var recorder by remember { mutableStateOf<AVAudioRecorder?>(null) }
    val outputUrl = remember {
        NSURL.fileURLWithPath(
            NSTemporaryDirectory().trimEnd('/') + "/click_voice_${kotlin.random.Random.nextLong()}.m4a",
        )
    }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var recordedDurationSec by remember { mutableLongStateOf(0L) }
    var recordStartMs by remember { mutableLongStateOf(0L) }
    var waveformSamples by remember { mutableStateOf(List(40) { 0.06f }) }

    val settings = remember {
        mapOf<Any?, Any>(
            AVFormatIDKey to 1633772320u,
            AVSampleRateKey to 44_100.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderAudioQualityKey to AVAudioQualityMedium,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                recorder?.apply {
                    try {
                        stop()
                    } catch (_: Throwable) {
                    }
                }
            }
            recorder = null
            NSFileManager.defaultManager.removeItemAtURL(outputUrl, error = null)
        }
    }

    LaunchedEffect(phase) {
        if (phase != VoiceRecordUiPhase.Recording) return@LaunchedEffect
        while (isActive) {
            delay(250)
            elapsedSec = kotlin.math.max(0L, Clock.System.now().toEpochMilliseconds() - recordStartMs) / 1000L
        }
    }

    LaunchedEffect(phase, recorder) {
        val rec = recorder
        if (phase != VoiceRecordUiPhase.Recording || rec == null) return@LaunchedEffect
        while (isActive) {
            delay(50)
            rec.updateMeters()
            val power = rec.averagePowerForChannel(0u).toDouble()
            val v = ((power + 50.0) / 50.0).coerceIn(0.0, 1.0).toFloat()
            val scaled = (v * 0.88f + 0.12f).coerceIn(0.08f, 1f)
            waveformSamples = waveformSamples.drop(1) + scaled
        }
    }

    val displaySeconds = when (phase) {
        VoiceRecordUiPhase.Preview -> recordedDurationSec
        else -> elapsedSec
    }
    val pathForPreview = outputUrl.path
    val previewFileOk = pathForPreview != null &&
        NSFileManager.defaultManager.fileExistsAtPath(pathForPreview)
    val previewUrl = if (phase == VoiceRecordUiPhase.Preview && previewFileOk) {
        outputUrl.absoluteString
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = {
            runCatching {
                recorder?.apply {
                    try {
                        stop()
                    } catch (_: Throwable) {
                    }
                }
            }
            recorder = null
            phase = VoiceRecordUiPhase.Idle
            NSFileManager.defaultManager.removeItemAtURL(outputUrl, error = null)
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
                    onCancel = {
                        runCatching {
                            recorder?.apply {
                                try {
                                    stop()
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        recorder = null
                        phase = VoiceRecordUiPhase.Idle
                        NSFileManager.defaultManager.removeItemAtURL(outputUrl, error = null)
                        onDismiss()
                    },
                    onRecord = {
                        runCatching {
                            NSFileManager.defaultManager.removeItemAtURL(outputUrl, error = null)
                        }
                        AVAudioSession.sharedInstance().setCategory(
                            AVAudioSessionCategoryPlayAndRecord,
                            error = null,
                        )
                        val rec = AVAudioRecorder(uRL = outputUrl, settings = settings, error = null)
                        rec.meteringEnabled = true
                        if (rec.prepareToRecord() && rec.record()) {
                            recorder = rec
                            recordStartMs = Clock.System.now().toEpochMilliseconds()
                            elapsedSec = 0L
                            recordedDurationSec = 0L
                            waveformSamples = List(40) { 0.06f }
                            phase = VoiceRecordUiPhase.Recording
                        }
                    },
                    onStopRecording = {
                        runCatching {
                            recorder?.apply {
                                stop()
                            }
                        }
                        recorder = null
                        recordedDurationSec = kotlin.math.max(
                            0L,
                            (Clock.System.now().toEpochMilliseconds() - recordStartMs) / 1000L,
                        )
                        elapsedSec = recordedDurationSec
                        phase = VoiceRecordUiPhase.Preview
                    },
                    onReRecord = {
                        runCatching {
                            recorder?.apply {
                                try {
                                    stop()
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        recorder = null
                        phase = VoiceRecordUiPhase.Idle
                        elapsedSec = 0L
                        recordedDurationSec = 0L
                        waveformSamples = List(40) { 0.06f }
                        NSFileManager.defaultManager.removeItemAtURL(outputUrl, error = null)
                    },
                    onSend = {
                        val durationSec = recordedDurationSec
                        scope.launch {
                            val path = outputUrl.path ?: return@launch
                            val fileData = NSFileManager.defaultManager.contentsAtPath(path)
                            val bytes = fileData?.toByteArray()
                            if (bytes != null && bytes.isNotEmpty()) {
                                onFinished(bytes, durationSec)
                            }
                            NSFileManager.defaultManager.removeItemAtURL(outputUrl, error = null)
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
