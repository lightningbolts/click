package compose.project.click.click.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import compose.project.click.click.ui.components.AdaptiveButton
import compose.project.click.click.utils.toImageBitmap

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
    
    val qrImageBitmap = remember(qrCode) {
        val sizePx = 512 // High resolution for display
        val content = qrCode.renderToBytes()
        content.toImageBitmap()
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = qrImageBitmap,
                contentDescription = "QR Code",
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        AdaptiveButton(onClick = onShare) {
            Text("Share QR Code")
        }
    }
}
