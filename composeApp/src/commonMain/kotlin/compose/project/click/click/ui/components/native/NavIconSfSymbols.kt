package compose.project.click.click.ui.components.native

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps Compose [ImageVector] icons used by native bridges to SF Symbol names. */
fun ImageVector.toSfSymbolName(): String? = when (this) {
    Icons.AutoMirrored.Filled.ArrowBack -> "chevron.left"
    Icons.Filled.Search -> "magnifyingglass"
    Icons.Filled.Refresh -> "arrow.clockwise"
    Icons.Filled.Close -> "xmark"
    Icons.Filled.MoreVert -> "ellipsis"
    Icons.Filled.Call -> "phone.fill"
    Icons.Filled.Videocam -> "video.fill"
    Icons.Filled.VideocamOff -> "video.slash.fill"
    Icons.Filled.Edit -> "pencil"
    Icons.Filled.Add -> "plus"
    Icons.AutoMirrored.Filled.Send -> "paperplane.fill"
    Icons.Filled.Check -> "checkmark"
    Icons.Filled.Groups -> "person.3.fill"
    Icons.Filled.AddLocationAlt -> "mappin.and.ellipse"
    Icons.Filled.OpenInFull -> "arrow.up.left.and.arrow.down.right"
    Icons.Filled.Remove -> "minus"
    Icons.Filled.Cameraswitch -> "arrow.triangle.2.circlepath.camera"
    Icons.Filled.Mic -> "mic.fill"
    Icons.Filled.MicOff -> "mic.slash.fill"
    Icons.Filled.SpeakerPhone -> "speaker.wave.2.fill"
    Icons.Filled.CallEnd -> "phone.down.fill"
    else -> null
}

/** All [ImageVector]s that must resolve for native nav bridges. */
val migratedNavIcons: List<ImageVector> = listOf(
    Icons.AutoMirrored.Filled.ArrowBack,
    Icons.Filled.Search,
    Icons.Filled.Refresh,
    Icons.Filled.Close,
    Icons.Filled.MoreVert,
    Icons.Filled.Call,
    Icons.Filled.Videocam,
    Icons.Filled.VideocamOff,
    Icons.Filled.Edit,
    Icons.Filled.Add,
    Icons.AutoMirrored.Filled.Send,
    Icons.Filled.Check,
    Icons.Filled.Groups,
    Icons.Filled.AddLocationAlt,
    Icons.Filled.OpenInFull,
    Icons.Filled.Remove,
    Icons.Filled.Cameraswitch,
    Icons.Filled.Mic,
    Icons.Filled.MicOff,
    Icons.Filled.SpeakerPhone,
    Icons.Filled.CallEnd,
)
