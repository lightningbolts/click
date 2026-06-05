package compose.project.click.click.encounter

import android.content.Context

private var widgetContext: Context? = null

fun initEncounterTetherWidgetBridge(context: Context) {
    widgetContext = context.applicationContext
}

actual object EncounterTetherWidgetBridge {
    actual fun updateRecentEncounter(encounterId: String?, peerDisplayName: String?) {
        val ctx = widgetContext ?: return
        ctx.getSharedPreferences("encounter_tether_widget", Context.MODE_PRIVATE)
            .edit()
            .putString("recent_encounter_id", encounterId)
            .putString("peer_display_name", peerDisplayName)
            .apply()
    }
}
