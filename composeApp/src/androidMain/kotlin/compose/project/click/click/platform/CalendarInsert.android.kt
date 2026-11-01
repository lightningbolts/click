package compose.project.click.click.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import compose.project.click.click.events.EventCalendarDraft
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext

actual fun openSystemCalendarInsert(draft: EventCalendarDraft): Boolean {
    val ctx = AndroidChatImageSaveContext.applicationContext
    val intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, draft.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.startEpochMs)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, draft.endEpochMs)
            .putExtra(CalendarContract.Events.DESCRIPTION, draft.details)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    draft.location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
    return try {
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

actual fun openMapsChooser(geoUri: String): Boolean {
    val ctx = AndroidChatImageSaveContext.applicationContext
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
    return try {
        ctx.startActivity(Intent.createChooser(intent, "Open in").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
