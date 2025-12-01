package compose.project.click.click.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.User

@Composable
fun UserQrCode(
    user: User,
    size: Dp = 200.dp,
    qrService: QrServiceAndroid = QrServiceAndroid(),
    onShare: (Bitmap) -> Unit = {}
) {
    val payload = remember(user) { qrService.createPayloadForUser(user) }
    val density = LocalDensity.current
    val px = remember(size, density) { (with(density) { size.toPx() }).toInt() }
    val bitmap = remember(payload, px) { qrService.generateQrBitmap(payload, px.coerceAtLeast(64)) }

    Column {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "User QR Code", modifier = androidx.compose.ui.Modifier.size(size))
        Button(onClick = { onShare(bitmap) }) {
            Text("Share")
        }
    }
}
