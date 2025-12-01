package compose.project.click.click.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.models.User
import compose.project.click.click.qr.QrPayload
import compose.project.click.click.qr.toJson
import qrcode.QRCode

@Composable
fun UserQrCode(
    user: User,
    size: Dp = 200.dp,
    onShare: () -> Unit = {}
) {
    val payload = remember(user) { QrPayload(userId = user.id, name = user.name).toJson() }
    
    // Generate QR Code matrix
    val qrCode = remember(payload) {
        QRCode.ofSquares()
            .build(payload)
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            // TODO: Implement actual QR code drawing using qrcode-kotlin
            // val matrix = qrCode.rawData
            Text("QR Code\n(Generated)")
        }
        
        Button(onClick = onShare) {
            Text("Share")
        }
    }
}
