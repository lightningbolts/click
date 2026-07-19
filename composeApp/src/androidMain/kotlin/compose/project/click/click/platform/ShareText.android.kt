package compose.project.click.click.platform

import android.content.Intent
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext

actual fun shareText(text: String, subject: String?) {
    val body = text.trim()
    if (body.isEmpty()) return
    val ctx = AndroidChatImageSaveContext.applicationContext
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        subject?.trim()?.takeIf { it.isNotEmpty() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    ctx.startActivity(
        Intent.createChooser(intent, subject?.takeIf { it.isNotBlank() } ?: "Share")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
