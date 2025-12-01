package compose.project.click.click.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import compose.project.click.click.data.models.User

class QrServiceAndroid : QrService {
    override fun createPayloadForUser(user: User): String =
        QrPayload(userId = user.id, shareKey = user.shareKey, name = user.name).toJson()

    override fun parsePayload(payload: String): QrPayload? =
        payload.toQrPayloadOrNull()

    /**
     * ZXing を使って QR の Bitmap を生成するユーティリティ。
     * size はピクセル指定。
     */
    fun generateQrBitmap(payload: String, size: Int): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}

